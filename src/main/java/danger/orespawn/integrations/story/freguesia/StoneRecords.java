package danger.orespawn.integrations.story.freguesia;

import danger.orespawn.integrations.config.IntegrationsConfig;
import danger.orespawn.integrations.story.StoryCore;
import danger.orespawn.integrations.story.deed.DeedLog;
import danger.orespawn.integrations.story.firsthour.FirstHourUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A stone's life (item-marco-da-freguesia, system-freguesia "When the stone falls"): placing one founds a Freguesia,
 * or, for a signer within 48 blocks of a record that fell under 30 game days ago, resumes it where it paused; one
 * {@code minecraft:marker} tagged {@code cc_freguesia} stands at every stone (never sent to clients, its tick is
 * empty) and carries score {@code cc_arcas} for the casket swaps. Removing the stone pauses the record: nothing
 * despawns, and the signers online hear that it fell. Every standing stone is mirrored into command storage
 * {@code orespawn_integrations:freguesia} ({@code stones: [{pos, dim}]}), which the Falls' safety rule reads.
 */
public final class StoneRecords {

    public static final String MARKER_TAG = "cc_freguesia";
    public static final String ARCAS = "cc_arcas";
    public static final String QUINTAL = "cc_quintal";
    public static final ResourceLocation STORAGE = FirstHourUtil.oi("freguesia");
    static final String K = "orespawn_integrations.freguesia.";

    private StoneRecords() {
    }

    static boolean enabled() {
        return IntegrationsConfig.on(IntegrationsConfig.storyFreguesia);
    }

    static StandingRules.Numbers numbers() {
        boolean loaded = IntegrationsConfig.SPEC.isLoaded();
        return StandingRules.Numbers.of(
                loaded ? IntegrationsConfig.freguesiaThresholds.get() : IntegrationsConfig.freguesiaThresholds.getDefault(),
                loaded ? IntegrationsConfig.freguesiaBuilderKeys.get() : IntegrationsConfig.freguesiaBuilderKeys.getDefault());
    }

    static int graceDays() {
        return IntegrationsConfig.get(IntegrationsConfig.freguesiaStoneGraceDays);
    }

    /** The world's game day (overworld time / 24,000): what stone days and the grace count in. */
    public static long today(MinecraftServer server) {
        return server.overworld().getDayTime() / 24000L;
    }

    static String dim(Level level) {
        return level.dimension().location().toString();
    }

    static ServerLevel level(MinecraftServer server, String dim) {
        ResourceLocation id = ResourceLocation.tryParse(dim);
        return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    // --- placing and losing the stone ---

    static void onStonePlaced(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (!enabled()) {
            return;
        }
        try {
            MinecraftServer server = level.getServer();
            FreguesiaData data = FreguesiaData.get(server);
            long today = today(server);
            FreguesiaData.Stone stone = null;
            for (FreguesiaData.Stone s : data.near(dim(level), pos.getX(), pos.getZ(), StandingRules.RADIUS)) {
                if (!s.standing && s.isSigner(player.getUUID())
                        && StandingRules.withinGrace(s.fallenDay, today, graceDays())) {
                    stone = s; // a signer's new stone near the old spot resumes the record where it paused
                    break;
                }
            }
            if (stone != null) {
                data.move(stone, pos);
                stone.standing = true;
                stone.fallenDay = -1;
            } else {
                stone = data.create(dim(level), pos);
                data.addSigner(stone, player.getUUID(), player.getGameProfile().getName());
                stone.foundedDay = today;
                stone.founderOrigin = originOf(player);
            }
            stone.lastSignerDay = today;
            summonMarker(level, stone);
            data.setDirty();
            mirror(server);
        } catch (Throwable t) {
            StoryCore.logOnce("freguesia.place", t);
        }
    }

    static void onStoneRemoved(ServerLevel level, BlockPos pos) {
        try {
            MinecraftServer server = level.getServer();
            FreguesiaData data = FreguesiaData.get(server);
            FreguesiaData.Stone s = data.at(dim(level), pos);
            if (s == null) {
                return;
            }
            fell(level, data, s);
        } catch (Throwable t) {
            StoryCore.logOnce("freguesia.remove", t);
        }
    }

    /** The record pauses: the stone is gone, nothing despawns, the arrivals keep their tag and persistence. */
    static void fell(ServerLevel level, FreguesiaData data, FreguesiaData.Stone s) {
        MinecraftServer server = level.getServer();
        s.standing = false;
        s.fallenDay = today(server);
        killMarkers(level, s.pos);
        s.marker = null;
        data.setDirty();
        mirror(server);
        for (ServerPlayer p : onlineSigners(server, s)) {
            p.sendSystemMessage(Component.translatable(K + "stone_fell"));
        }
    }

    // --- the marker ---

    static void summonMarker(ServerLevel level, FreguesiaData.Stone s) {
        killMarkers(level, s.pos);
        Marker marker = EntityType.MARKER.create(level);
        if (marker == null) {
            return;
        }
        marker.moveTo(s.pos.getX() + 0.5, s.pos.getY(), s.pos.getZ() + 0.5);
        marker.addTag(MARKER_TAG);
        level.addFreshEntity(marker);
        s.marker = marker.getUUID();
        Scoreboard board = level.getServer().getScoreboard();
        board.getOrCreatePlayerScore(marker, objective(board, ARCAS)).set(0);
        objective(board, QUINTAL);
    }

    /** Kills every cc_freguesia marker within 2 blocks of {@code pos}; the search box is bounded. */
    static void killMarkers(ServerLevel level, BlockPos pos) {
        AABB box = new AABB(pos).inflate(2.0);
        for (Entity e : level.getEntities(EntityType.MARKER, box, m -> m.getTags().contains(MARKER_TAG))) {
            e.discard();
        }
    }

    static Entity marker(ServerLevel level, FreguesiaData.Stone s) {
        if (s.marker == null) {
            return null;
        }
        return level.getEntity(s.marker);
    }

    static Objective objective(Scoreboard board, String name) {
        return FirstHourUtil.objective(board, name);
    }

    // --- the mirror the Falls read ---

    static void mirror(MinecraftServer server) {
        ListTag list = new ListTag();
        for (FreguesiaData.Stone s : FreguesiaData.get(server).stones()) {
            if (s.standing && !s.filed && !s.signers.isEmpty()) {
                CompoundTag c = new CompoundTag();
                c.put("pos", new IntArrayTag(new int[]{s.pos.getX(), s.pos.getY(), s.pos.getZ()}));
                c.putString("dim", s.dim);
                c.putInt("id", s.id);
                list.add(c);
            }
        }
        CompoundTag tag = new CompoundTag();
        tag.put("stones", list);
        server.getCommandStorage().set(STORAGE, tag);
    }

    // --- what Jade shows ---

    /**
     * The looked-at stone's numbers for Jade, or null when no record stands there: the name's kind and argument
     * ({@code nk} 0 default, 1 epithet, 2 name tag; {@code na}), standing, the stage, the next stage and its
     * threshold, stone days and the days it needs, and {@code key}: the stage (3 or 4) whose key alone is missing.
     */
    public static CompoundTag jadeView(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockPos lower = state.hasProperty(MarcoBlock.HALF)
                && state.getValue(MarcoBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER
                ? pos.below() : pos;
        FreguesiaData.Stone s = FreguesiaData.get(level.getServer()).at(dim(level), lower);
        if (s == null) {
            return null;
        }
        CompoundTag tag = new CompoundTag();
        if (s.customName != null) {
            tag.putInt("nk", 2);
            tag.putString("na", s.customName);
        } else if (s.epithetKey != null) {
            tag.putInt("nk", 1);
            tag.putString("na", s.epithetKey);
        } else {
            tag.putInt("nk", 0);
            tag.putString("na", s.founderName());
        }
        StandingRules.Numbers n = numbers();
        int next = s.stage + 1;
        tag.putInt("standing", s.standing());
        tag.putInt("stage", s.stage);
        tag.putInt("next", next);
        if (next <= StandingRules.TOWN) {
            tag.putInt("threshold", StandingRules.threshold(next, n));
            tag.putInt("days", s.stoneDays);
            tag.putInt("days_needed", StandingRules.daysNeeded(next));
            StandingRules.Missing missing = StandingRules.missing(s.stage, s.standing(), s.stoneDays,
                    s.deeds > 0, s.bossKill, s.knight, n);
            tag.putInt("key", missing == StandingRules.Missing.KEY ? next : 0);
        }
        return tag;
    }

    // --- names, signers and deeds ---

    /** The stone's name as each client reads it: a name tag's name, else the founder's epithet, else the default. */
    public static Component name(FreguesiaData.Stone s) {
        if (s.customName != null) {
            return Component.literal(s.customName);
        }
        if (s.epithetKey != null) {
            return Component.translatable(K + "name.epithet", Component.translatable(s.epithetKey));
        }
        return Component.translatable(K + "name.default", s.founderName());
    }

    /**
     * The founder's first truco epithet renames their stones once, unless a name tag came first (called by the
     * Saturday retelling with the epithet's lang key, the thing with its preposition in pt-BR: "da Colher").
     */
    public static void founderEpithet(MinecraftServer server, UUID founder, String epithetKey) {
        FreguesiaData data = FreguesiaData.get(server);
        for (FreguesiaData.Stone s : data.stones()) {
            if (founder.equals(s.founder()) && s.customName == null && s.epithetKey == null) {
                s.pendingEpithet = epithetKey; // the rename shows from the next dawn
                data.setDirty();
            }
        }
    }

    static List<ServerPlayer> onlineSigners(MinecraftServer server, FreguesiaData.Stone s) {
        List<ServerPlayer> out = new ArrayList<>(s.signers.size());
        for (UUID id : s.signers) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                out.add(p);
            }
        }
        return out;
    }

    /** Online signers in the stone's level within {@code radius} blocks of it. */
    static int signersNear(ServerLevel level, FreguesiaData.Stone s, int radius, List<ServerPlayer> into) {
        int n = 0;
        double r2 = (double) radius * radius;
        Vec3 c = Vec3.atCenterOf(s.pos);
        for (UUID id : s.signers) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p != null && p.level() == level && p.distanceToSqr(c) <= r2) {
                n++;
                if (into != null) {
                    into.add(p);
                }
            }
        }
        return n;
    }

    /** Writes a deed for {@code player} now, or at their next login; it also counts for the stone's Hamlet. */
    static void deed(MinecraftServer server, FreguesiaData.Stone s, UUID player, String key, String arg) {
        FreguesiaData data = FreguesiaData.get(server);
        if (s != null) {
            s.deeds++;
        }
        ServerPlayer p = player == null ? null : server.getPlayerList().getPlayer(player);
        if (p != null) {
            DeedLog.record(p, key, arg);
        } else if (player != null) {
            data.pendingDeeds.computeIfAbsent(player, k -> new ArrayList<>()).add(new String[]{key, arg});
        }
        data.setDirty();
    }

    /** The founder's origin path from the Egg's Label tag ({@code cc_origin.<path>}), or "". */
    static String originOf(ServerPlayer player) {
        for (String tag : player.getTags()) {
            if (tag.startsWith("cc_origin.")) {
                return tag.substring("cc_origin.".length());
            }
        }
        return "";
    }
}
