package danger.orespawn.integrations.story.hunt;

import danger.orespawn.integrations.OreSpawnIntegrations;
import danger.orespawn.integrations.config.IntegrationsConfig;
import danger.orespawn.integrations.story.StoryCore;
import danger.orespawn.integrations.story.StoryScheduler;
import danger.orespawn.integrations.story.court.CourtRanks;
import danger.orespawn.integrations.story.deed.DeedLog;
import danger.orespawn.integrations.story.deed.DeedRules;
import danger.orespawn.integrations.story.firsthour.FirstHourUtil;
import danger.orespawn.integrations.story.shelf9.HubPlaces;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Wall of the Wanted (system-boss-ladder, 0.10.1): one boss-kill handler. A kill of a boss on any of the seven
 * rows writes the deed (the party within 32 blocks named), grants each of those players the row's advancement
 * ({@code hunt/row_1} to {@code row_7}), advances their Monday bounties and, the first time anyone on the server
 * brings that boss down, records the killer and rewrites the row's sign on every Lodge wall that is loaded (the others
 * when their chunk next loads). Until the Kill Register (0.19.0) the signs keep the first kills.
 *
 * <p>SavedData {@code orespawn_integrations_wall}: each boss's first killer, name and Land's day (at most 104 rows
 * of about 32 B), each row's latest first kill, and the Lodges' bounding boxes as their starts load.</p>
 */
public final class WallOfTheWanted {

    public static final String FILE = "orespawn_integrations_wall";
    private static final String KEY = "orespawn_integrations.hunt.";
    private static final ResourceKey<Structure> LODGE = ResourceKey.create(Registries.STRUCTURE,
            FirstHourUtil.oi("hunt_lodge"));
    private static final ResourceKey<Structure> DESERT_TEMPLE = ResourceKey.create(Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath("betterdeserttemples", "desert_temple"));
    @SuppressWarnings("unchecked")
    private static final TagKey<EntityType<?>>[] ROW_TAGS = new TagKey[WallRows.ROWS + 1];

    static {
        for (int row = 1; row <= WallRows.ROWS; row++) {
            ROW_TAGS[row] = TagKey.create(Registries.ENTITY_TYPE, FirstHourUtil.oi("wall/row_" + row));
        }
    }

    /** A Wall boss: its key (an entity id, or a coded key such as a proctor's), its row and its name. */
    public record Hit(String key, int row, Component name) {}

    private WallOfTheWanted() {
    }

    static void init() {
        NeoForge.EVENT_BUS.addListener(WallOfTheWanted::onDeath);
        NeoForge.EVENT_BUS.addListener(WallOfTheWanted::onChunkLoad);
    }

    // --- which boss ---

    /** The Wall boss {@code entity} is, or null for anything that hangs on no poster. */
    public static Hit identify(LivingEntity entity) {
        EntityType<?> type = entity.getType();
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
        for (String tag : entity.getTags()) {
            WallRows.Proctor proctor = WallRows.proctor(tag);
            if (proctor != null) {
                return new Hit(proctor.key(), proctor.row(), entity.getName());
            }
        }
        CompoundTag data = entity.getPersistentData();
        if (data.getBoolean("apoth.boss")) {
            return new Hit(WallRows.INVADER, 3, entity.getName());
        }
        if (data.getBoolean("apoth.miniboss")) {
            String elite = eliteName(entity);
            if (type == EntityType.GOAT || elite.contains("craig")) {
                return new Hit(WallRows.CRAIG, 6, entity.getName());
            }
            if (elite.equals("Undead Knight")) {
                return new Hit(WallRows.UNDEAD_KNIGHT, 1, entity.getName());
            }
            if (elite.equals("Withering Archer")) {
                return new Hit(WallRows.WITHERING_ARCHER, 2, entity.getName());
            }
        }
        if (type == EntityType.BLAZE) {
            return entity.getTags().contains("in.hovering_inferno")
                    ? new Hit(WallRows.GOVERNOR, 5, entity.getName()) : null;
        }
        if (type == EntityType.HUSK && entity.level() instanceof ServerLevel level) {
            StructureStart start = level.structureManager().getStructureWithPieceAt(entity.blockPosition(),
                    h -> h.is(DESERT_TEMPLE));
            return start.isValid() ? new Hit(WallRows.PHARAOH, 1, entity.getName()) : null;
        }
        if (id.equals("orespawn:the_prince_adult") && entity instanceof OwnableEntity own && own.getOwnerUUID() != null) {
            return null; // a tamed Prince is never on the Wall
        }
        for (int row = WallRows.ROWS; row >= 1; row--) {
            if (type.is(ROW_TAGS[row])) {
                return new Hit(id, row, entity.getType().getDescription());
            }
        }
        return null;
    }

    private static String eliteName(LivingEntity entity) {
        Component name = entity.getCustomName();
        if (name == null) {
            return "";
        }
        if (name.getContents() instanceof TranslatableContents t) {
            return t.getKey();
        }
        return name.getString();
    }

    // --- the kill handler ---

    private static void onDeath(LivingDeathEvent event) {
        try {
            LivingEntity dead = event.getEntity();
            if (dead.level().isClientSide() || dead instanceof ServerPlayer) {
                return;
            }
            ServerPlayer killer = creditOf(dead);
            if (killer == null) {
                return;
            }
            Hit hit = identify(dead);
            if (hit != null) {
                killed(killer, dead, hit);
            }
        } catch (Throwable t) {
            StoryCore.logOnce("wall_kill", t);
        }
    }

    /** The player credited with a kill: vanilla's {@code getKillCredit}, or the owner of the pet that made it. */
    public static ServerPlayer creditOf(LivingEntity dead) {
        LivingEntity credit = dead.getKillCredit();
        if (credit instanceof ServerPlayer p) {
            return p;
        }
        if (credit instanceof OwnableEntity pet && pet.getOwner() instanceof ServerPlayer owner) {
            return owner;
        }
        return null;
    }

    public static void killed(ServerPlayer killer, LivingEntity dead, Hit hit) {
        MinecraftServer server = killer.getServer();
        List<ServerPlayer> party = new ArrayList<>();
        party.add(killer);
        party.addAll(DeedLog.witnessesOf(killer));
        Data data = Data.get(server);
        boolean first = !data.kills.containsKey(hit.key());
        int day = CourtRanks.landDay(server);
        String bossArg = bossArgument(dead, hit);
        if (first) {
            data.kills.put(hit.key(), new Kill(killer.getUUID(), killer.getGameProfile().getName(), day, hit.row()));
            data.rows.put(hit.row(), new Latest(hit.key(), bossArg, killer.getGameProfile().getName(), day));
            data.setDirty();
        }
        if (IntegrationsConfig.on(IntegrationsConfig.storyBossLadder)) {
            for (ServerPlayer p : party) {
                FirstHourUtil.award(p, "hunt/row_" + hit.row());
            }
            if (first) {
                refreshAll(server);
            }
        }
        String weapon = weaponArgument(killer.getMainHandItem());
        String name = killer.getGameProfile().getName();
        if (first) {
            DeedLog.record(killer, DeedRules.Kind.WEAPON, true, "orespawn_integrations.deed.boss_first", name, bossArg, weapon);
        } else if (party.size() > 1) {
            StringBuilder names = new StringBuilder();
            for (int i = 1; i < party.size(); i++) {
                names.append(i > 1 ? ", " : "").append(party.get(i).getGameProfile().getName());
            }
            DeedLog.record(killer, DeedRules.Kind.WEAPON, true, "orespawn_integrations.deed.boss_kill_party",
                    name, bossArg, names.toString(), weapon);
        } else {
            DeedLog.record(killer, DeedRules.Kind.WEAPON, false, "orespawn_integrations.deed.boss_kill", name, bossArg, weapon);
        }
        MondayBoard.onWallKill(party, hit);
    }

    /** The boss as a deed argument: its own translate key when it has one, so each listener reads it in their language. */
    static String bossArgument(LivingEntity dead, Hit hit) {
        Component name = dead.hasCustomName() ? dead.getCustomName() : dead.getType().getDescription();
        if (name != null && name.getContents() instanceof TranslatableContents t) {
            return DeedLog.key(t.getKey());
        }
        return name == null ? hit.key() : name.getString();
    }

    static String weaponArgument(ItemStack stack) {
        if (stack.isEmpty()) {
            return DeedLog.key("orespawn_integrations.truco.weapon.hands");
        }
        return DeedLog.key(stack.getItem().getDescriptionId());
    }

    /** True once anyone on the server has a kill on any boss of {@code row}. */
    public static boolean rowCollected(MinecraftServer server, int row) {
        return Data.get(server).rows.containsKey(row);
    }

    // --- the signs ---

    private static void onChunkLoad(ChunkEvent.Load event) {
        try {
            if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) {
                return;
            }
            Data data = Data.get(level.getServer());
            String dim = level.dimension().location().toString();
            Structure lodge = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(LODGE);
            if (lodge != null && level.dimension() == Level.OVERWORLD) {
                StructureStart start = chunk.getStartForStructure(lodge);
                if (start != null && start.isValid() && data.addLodge(dim, start.getBoundingBox())) {
                    data.setDirty();
                }
            }
            for (Lodge l : lodges(level.getServer(), data)) {
                if (l.dim().equals(dim) && l.box().intersects(chunk.getPos().getMinBlockX(), chunk.getPos().getMinBlockZ(),
                        chunk.getPos().getMaxBlockX(), chunk.getPos().getMaxBlockZ())) {
                    StoryScheduler.schedule(level.getServer(), 1, "wall_signs", () -> refresh(level, l.box()));
                    return;
                }
            }
        } catch (Throwable t) {
            StoryCore.logOnce("wall_chunk", t);
        }
    }

    record Lodge(String dim, BoundingBox box) {}

    /** Every Lodge the Wall knows: the hub's placed one (hub_places) and every generated start seen loading. */
    static List<Lodge> lodges(MinecraftServer server, Data data) {
        List<Lodge> out = new ArrayList<>(data.lodges);
        for (HubPlaces.Place p : HubPlaces.of(server, "hunt_lodge")) {
            BlockPos c = p.pos();
            out.add(new Lodge(p.dim(), new BoundingBox(c.getX() - 32, c.getY() - 8, c.getZ() - 32,
                    c.getX() + 32, c.getY() + 24, c.getZ() + 32)));
        }
        return out;
    }

    /** Rewrites every loaded Wall sign (a sign under a Wanted poster) on every known Lodge. */
    public static void refreshAll(MinecraftServer server) {
        Data data = Data.get(server);
        for (Lodge l : lodges(server, data)) {
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(l.dim())));
            if (level != null) {
                refresh(level, l.box());
            }
        }
    }

    static void refresh(ServerLevel level, BoundingBox box) {
        if (!IntegrationsConfig.on(IntegrationsConfig.storyBossLadder)) {
            return;
        }
        Data data = Data.get(level.getServer());
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (Map.Entry<BlockPos, BlockEntity> e : List.copyOf(chunk.getBlockEntities().entrySet())) {
                    if (!(e.getValue() instanceof SignBlockEntity sign) || !box.isInside(e.getKey())) {
                        continue;
                    }
                    BlockState above = level.getBlockState(e.getKey().above());
                    if (!above.is(HuntBlocks.WANTED_POSTER.get())) {
                        continue;
                    }
                    int row = above.getValue(WantedPosterBlock.ROW);
                    SignText text = signText(data.rows.get(row), row);
                    sign.setText(text, true);
                    sign.setChanged();
                    level.sendBlockUpdated(e.getKey(), sign.getBlockState(), sign.getBlockState(), 3);
                }
            }
        }
    }

    /** A row's sign: "WANTED: ROW n / boss / felled by / name", or "WANTED: ROW n / Felled by: / nobody. Yet." */
    public static SignText signText(Latest latest, int row) {
        Component[] lines;
        if (latest == null) {
            lines = new Component[]{Component.translatable(KEY + "sign.row." + row),
                    Component.translatable(KEY + "sign.felled_by_colon"), Component.translatable(KEY + "sign.nobody_yet"),
                    Component.empty()};
        } else {
            Object boss = DeedLog.argument(latest.boss());
            lines = new Component[]{Component.translatable(KEY + "sign.row." + row),
                    boss instanceof Component c ? c : Component.literal(String.valueOf(boss)),
                    Component.translatable(KEY + "sign.felled_by"), Component.literal(latest.name())};
        }
        return new SignText(lines, lines, net.minecraft.world.item.DyeColor.BLACK, false);
    }

    // --- the SavedData ---

    record Kill(UUID player, String name, int day, int row) {}

    record Latest(String key, String boss, String name, int day) {}

    public static final class Data extends SavedData {
        final Map<String, Kill> kills = new LinkedHashMap<>();
        final Map<Integer, Latest> rows = new LinkedHashMap<>();
        final List<Lodge> lodges = new ArrayList<>();

        public static Data get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(
                    new SavedData.Factory<>(Data::new, Data::load), FILE);
        }

        boolean addLodge(String dim, BoundingBox box) {
            for (Lodge l : lodges) {
                if (l.dim().equals(dim) && l.box().equals(box)) {
                    return false;
                }
            }
            return lodges.size() < 8 && lodges.add(new Lodge(dim, box));
        }

        /** Who first brought down {@code key}, or null. */
        public String firstKiller(String key) {
            Kill k = kills.get(key);
            return k == null ? null : k.name();
        }

        static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data d = new Data();
            ListTag kills = tag.getList("kills", Tag.TAG_COMPOUND);
            for (int i = 0; i < kills.size(); i++) {
                CompoundTag k = kills.getCompound(i);
                if (k.hasUUID("uuid")) {
                    d.kills.put(k.getString("key"), new Kill(k.getUUID("uuid"), k.getString("name"), k.getInt("day"), k.getInt("row")));
                }
            }
            ListTag rows = tag.getList("rows", Tag.TAG_COMPOUND);
            for (int i = 0; i < rows.size(); i++) {
                CompoundTag r = rows.getCompound(i);
                d.rows.put(r.getInt("row"), new Latest(r.getString("key"), r.getString("boss"), r.getString("name"), r.getInt("day")));
            }
            ListTag lodges = tag.getList("lodges", Tag.TAG_COMPOUND);
            for (int i = 0; i < lodges.size(); i++) {
                CompoundTag l = lodges.getCompound(i);
                int[] b = l.getIntArray("box");
                if (b.length == 6) {
                    d.lodges.add(new Lodge(l.getString("dim"), new BoundingBox(b[0], b[1], b[2], b[3], b[4], b[5])));
                }
            }
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag kills = new ListTag();
            this.kills.forEach((key, k) -> {
                CompoundTag t = new CompoundTag();
                t.putString("key", key);
                t.putUUID("uuid", k.player());
                t.putString("name", k.name());
                t.putInt("day", k.day());
                t.putInt("row", k.row());
                kills.add(t);
            });
            tag.put("kills", kills);
            ListTag rows = new ListTag();
            this.rows.forEach((row, r) -> {
                CompoundTag t = new CompoundTag();
                t.putInt("row", row);
                t.putString("key", r.key());
                t.putString("boss", r.boss());
                t.putString("name", r.name());
                t.putInt("day", r.day());
                rows.add(t);
            });
            tag.put("rows", rows);
            ListTag lodges = new ListTag();
            for (Lodge l : this.lodges) {
                CompoundTag t = new CompoundTag();
                t.putString("dim", l.dim());
                BoundingBox b = l.box();
                t.putIntArray("box", new int[]{b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()});
                lodges.add(t);
            }
            tag.put("lodges", lodges);
            return tag;
        }
    }
}
