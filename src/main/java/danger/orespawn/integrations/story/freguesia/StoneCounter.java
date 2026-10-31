package danger.orespawn.integrations.story.freguesia;

import danger.orespawn.integrations.story.Ranks;
import danger.orespawn.integrations.story.StoryCore;
import danger.orespawn.integrations.story.deed.DeedLog;
import danger.orespawn.integrations.story.firsthour.FirstHourUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manor standing, counted as it is built (system-freguesia, system-manor-standing): one point per placement of a
 * {@code #orespawn_integrations:manor_decor} block within 48 blocks of a stone the placer signs, 1.5 while two or more
 * signers stand in the radius (the Mutirão), at most 200 a game day per stone, and each block position at most once a
 * day, so one lantern placed and broken 200 times pays 1. A placement is one tag check and a look at the four grid
 * cells around it: never a scan. A player who signs no stone counts the same way within 64 blocks of their respawn
 * point. A signer's placement or wake-up within 48 blocks marks the stone's day; a wake-up there also runs a check the
 * dawn skipped because the stone wasn't loaded.
 */
final class StoneCounter {

    static final TagKey<Block> MANOR_DECOR = TagKey.create(Registries.BLOCK, FirstHourUtil.oi("manor_decor"));
    static final TagKey<EntityType<?>> WANTED = TagKey.create(Registries.ENTITY_TYPE, FirstHourUtil.oi("wall_of_the_wanted"));
    private static final int MUTIRAO_LINE_TICKS = 60 * 20;
    private static final Map<UUID, Integer> MUTIRAO_SHOWN = new HashMap<>();

    private StoneCounter() {
    }

    static void reset() {
        MUTIRAO_SHOWN.clear();
    }

    static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)
                || !StoneRecords.enabled() || !event.getPlacedBlock().is(MANOR_DECOR)) {
            return;
        }
        try {
            count(level, player, event.getPos());
        } catch (Throwable t) {
            StoryCore.logOnce("freguesia.count", t);
        }
    }

    /** Counts one decor placement by {@code player} at {@code pos}. Public for the GameTests. */
    static void count(ServerLevel level, ServerPlayer player, BlockPos pos) {
        MinecraftServer server = level.getServer();
        FreguesiaData data = FreguesiaData.get(server);
        UUID id = player.getUUID();
        String dim = StoneRecords.dim(level);
        boolean signer = false;
        for (FreguesiaData.Stone s : data.near(dim, pos.getX(), pos.getZ(), StandingRules.RADIUS)) {
            if (!s.standing || !s.isSigner(id)) {
                continue;
            }
            signer = true;
            markDay(s);
            if (s.today.contains(pos.asLong()) || s.today.size() >= StandingRules.MAX_TODAY_POSITIONS) {
                continue;
            }
            List<ServerPlayer> present = new ArrayList<>(4);
            int inRadius = StoneRecords.signersNear(level, s, StandingRules.RADIUS, present);
            int halves = StandingRules.capped(s.todayHalves, StandingRules.placementHalves(inRadius));
            if (halves == 0) {
                continue;
            }
            s.today.add(pos.asLong());
            s.standingHalves += halves;
            s.weekGainHalves += halves;
            s.todayHalves += halves;
            if (inRadius >= 2) {
                mutirao(server, present);
            }
        }
        if (signer) {
            data.setDirty();
            return;
        }
        if (data.signsAny(id)) {
            return; // a signer's decor counts only around their stones
        }
        BlockPos respawn = player.getRespawnPosition();
        if (respawn == null || player.getRespawnDimension() != level.dimension()) {
            return;
        }
        long dx = respawn.getX() - pos.getX();
        long dz = respawn.getZ() - pos.getZ();
        if (dx * dx + dz * dz > (long) StandingRules.BED_RADIUS * StandingRules.BED_RADIUS) {
            return;
        }
        FreguesiaData.Bed bed = data.bed(id);
        bed.name = player.getGameProfile().getName();
        bed.bedPos = respawn.immutable();
        bed.bedDim = dim;
        if (bed.today.contains(pos.asLong()) || bed.today.size() >= StandingRules.MAX_TODAY_POSITIONS) {
            return;
        }
        int halves = StandingRules.capped(bed.todayHalves, 2);
        if (halves > 0) {
            bed.today.add(pos.asLong());
            bed.standingHalves += halves;
            bed.weekGainHalves += halves;
            bed.todayHalves += halves;
            data.setDirty();
        }
    }

    static void markDay(FreguesiaData.Stone s) {
        if (!s.todayBit) {
            s.todayBit = true;
            s.stoneDays++;
        }
    }

    /** The work party's line (at most once a minute per player) and its advancement. */
    private static void mutirao(MinecraftServer server, List<ServerPlayer> present) {
        int now = server.getTickCount();
        for (ServerPlayer p : present) {
            Integer last = MUTIRAO_SHOWN.get(p.getUUID());
            if (last == null || now - last >= MUTIRAO_LINE_TICKS) {
                MUTIRAO_SHOWN.put(p.getUUID(), now);
                FirstHourUtil.actionbar(p, Component.translatable(StoneRecords.K + "mutirao"));
                FirstHourUtil.award(p, "parish/mutirao");
            }
        }
    }

    // --- the wake-up ---

    static void onWake(PlayerWakeUpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)
                || !StoneRecords.enabled()) {
            return;
        }
        try {
            FreguesiaData data = FreguesiaData.get(level.getServer());
            for (FreguesiaData.Stone s : data.near(StoneRecords.dim(level), player.getBlockX(), player.getBlockZ(),
                    StandingRules.RADIUS)) {
                if (!s.standing || !s.isSigner(player.getUUID())) {
                    continue;
                }
                markDay(s);
                data.setDirty();
                if (s.pendingCheck) {
                    StoneDawn.check(level, data, s, StoneRecords.today(level.getServer()));
                }
            }
        } catch (Throwable t) {
            StoryCore.logOnce("freguesia.wake", t);
        }
    }

    // --- the stage-3 key: a signer's boss ---

    static void onDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer) || !StoneRecords.enabled()
                || !event.getEntity().getType().is(WANTED)) {
            return;
        }
        try {
            FreguesiaData data = FreguesiaData.get(killer.getServer());
            for (FreguesiaData.Stone s : data.stones()) {
                if (!s.filed && !s.bossKill && s.isSigner(killer.getUUID())) {
                    s.bossKill = true;
                    data.setDirty();
                }
            }
        } catch (Throwable t) {
            StoryCore.logOnce("freguesia.boss", t);
        }
    }

    // --- logins: the absent-signer rule, the Knight key, deeds and nodes kept for later ---

    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        try {
            seen(player);
        } catch (Throwable t) {
            StoryCore.logOnce("freguesia.login", t);
        }
    }

    /** Refreshes what a signer's presence tells the records. Also run for every online signer at dawn. */
    static void seen(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        FreguesiaData data = FreguesiaData.get(server);
        long today = StoneRecords.today(server);
        boolean knight = Ranks.atLeast(player, Ranks.KNIGHT);
        for (FreguesiaData.Stone s : data.stones()) {
            int index = s.signers.indexOf(player.getUUID());
            if (s.filed || index < 0) {
                continue;
            }
            s.lastSignerDay = today;
            s.signerNames.set(index, player.getGameProfile().getName());
            if (index == 0) {
                s.founderOrigin = StoneRecords.originOf(player);
            }
            if (knight) {
                s.knight = true;
            }
            StoneDawn.awardStages(player, s.stage);
            data.setDirty();
        }
        List<String[]> deeds = data.pendingDeeds.remove(player.getUUID());
        if (deeds != null) {
            for (String[] d : deeds) {
                DeedLog.record(player, d[0], d.length > 1 ? d[1] : "");
            }
            data.setDirty();
        }
    }
}
