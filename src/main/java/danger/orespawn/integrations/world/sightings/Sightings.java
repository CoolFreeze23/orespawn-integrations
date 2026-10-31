package danger.orespawn.integrations.world.sightings;

import danger.orespawn.integrations.OreSpawnIntegrations;
import danger.orespawn.integrations.config.IntegrationsConfig;
import danger.orespawn.integrations.story.StoryCore;
import danger.orespawn.integrations.story.shelf9.HubPlaces;
import danger.orespawn.integrations.world.WorldUtil;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Sightings (system-sightings). Every 10 ticks, for each player whose spyglass is raised ({@code Player#isScoping},
 * true server-side), one block ray and one entity sweep of 112 blocks along the look, through loaded chunks only: the
 * ray stops at the first chunk that isn't loaded, and the structure lookup reads {@code getChunkNow} for the hit
 * chunk and for each reference's start chunk, skipping any that isn't loaded, so looking never loads, generates or
 * reads a chunk from disk. A target seen 32 blocks off or farther (24 for a Scout) on two checks in a row (one
 * second) is stamped: its start joins the player's ring of sighted starts, its family's hidden advancement
 * {@code avistamentos/<family>} is granted (the Notebook page's lock; the first one also teaches the spyglass recipe),
 * the actionbar names it with the distance and, with JourneyMap loaded, a grey pin goes on the player's map. Land Ho!
 * ({@code avistamentos/terra_a_vista}) comes at the fortieth stamped family, whichever they are. A friend within 8
 * blocks scoping at the same check gets the same stamp.
 *
 * <p>Off with {@code story.sightings = false}: the spyglass is vanilla.
 */
public final class Sightings {

    public static final TagKey<EntityType<?>> SIGHTABLE_ENTITIES = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(OreSpawnIntegrations.MODID, "sightable"));
    public static final TagKey<Block> SIGHTABLE_BLOCKS = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(OreSpawnIntegrations.MODID, "sightable"));
    /** Stamped families, a dummy score the Registry's hand-in dialog reads. */
    public static final String STAMPS_SCORE = "cc_carimbos";

    private static final String KEY = "orespawn_integrations.avistamento.";
    private static final String ADVANCEMENT = OreSpawnIntegrations.MODID + ":avistamentos/";
    private static final String LAND_HO = ADVANCEMENT + "terra_a_vista";
    private static final double COOP_RADIUS_SQ = 8 * 8;
    private static final double HUB_RADIUS_SQ = 20 * 20;
    private static final int PERIOD = 10;
    /** The three butterflies of family 49, by bit. */
    private static final List<String> BUTTERFLIES =
            List.of("orespawn:butterfly", "naturalist:butterfly", "distantlandsmc:butterfly");
    private static final String HOARD_FALL_TAG = "cc_hoard_fall";

    /**
     * What a check saw. {@code key} names the target for the one-second rule; {@code family} 0 is a pin with no
     * stamp; {@code butterfly} is 0-2 for family 49's bookkeeping, else -1.
     */
    public record Target(String key, int family, double distance, BlockPos pin, String structure, long start,
                         String nameKey, int butterfly) {}

    private record HubSpot(int family, BlockPos pos, String dim, String thing) {}

    private static final Map<UUID, String> LAST = new HashMap<>();
    private static final Map<UUID, String> STAMPED = new HashMap<>();
    private static List<HubSpot> hubCache = List.of();
    private static long hubCacheTick = -1;

    private Sightings() {
    }

    public static void init(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(Sightings::onServerTick);
        NeoForge.EVENT_BUS.addListener(Sightings::onLoggedOut);
    }

    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST.remove(event.getEntity().getUUID());
        STAMPED.remove(event.getEntity().getUUID());
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        // Offset from the shared 20-tick clock, so the two never land on the same tick.
        if (server.getTickCount() % PERIOD != 5 || !IntegrationsConfig.on(IntegrationsConfig.storySightings)) {
            return;
        }
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        for (int i = 0; i < players.size(); i++) {
            ServerPlayer player = players.get(i);
            try {
                check(player);
            } catch (Throwable t) {
                StoryCore.logOnce("sightings", t);
            }
        }
    }

    private static void check(ServerPlayer player) {
        UUID id = player.getUUID();
        if (!player.isScoping()) {
            LAST.remove(id);
            STAMPED.remove(id);
            return;
        }
        Target target = cast(player);
        String previous = LAST.put(id, target == null ? null : target.key());
        if (target == null || !SightingRules.secondOnTarget(previous, target.key())
                || target.key().equals(STAMPED.get(id))) {
            return;
        }
        STAMPED.put(id, target.key());
        stamp(player, target);
        // Co-op: a friend within 8 blocks, scoping at this same check, gets the same stamp ("look, there!").
        for (ServerPlayer friend : player.serverLevel().players()) {
            if (friend != player && friend.isScoping() && friend.distanceToSqr(player) <= COOP_RADIUS_SQ
                    && !target.key().equals(STAMPED.get(friend.getUUID()))) {
                STAMPED.put(friend.getUUID(), target.key());
                double d = target.pin() == null ? target.distance()
                        : Math.sqrt(friend.distanceToSqr(Vec3.atCenterOf(target.pin())));
                stamp(friend, new Target(target.key(), target.family(), d, target.pin(), target.structure(),
                        target.start(), target.nameKey(), target.butterfly()));
            }
        }
    }

    /** One ray from the player's eyes; null when nothing sightable is in view. Public for the GameTests. */
    public static Target cast(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(SightingRules.MAX_DISTANCE));
        int min = SightingRules.minimum(isScout(player), IntegrationsConfig.get(IntegrationsConfig.sightingsMinDistance),
                IntegrationsConfig.get(IntegrationsConfig.sightingsScoutDistance));
        Ray ray = new Ray(level, eye, end);
        BlockHitResult hit = BlockGetter.traverseBlocks(eye, end, ray, Sightings::visit, r -> null);
        double limit = SightingRules.MAX_DISTANCE;
        if (hit == Ray.STOP) {
            limit = Math.min(limit, eye.distanceTo(Vec3.atCenterOf(ray.stoppedAt)));
            hit = null;
        } else if (hit != null) {
            limit = eye.distanceTo(hit.getLocation());
        }
        Target entity = entityTarget(player, level, eye, look, end, min, limit);
        if (entity != null) {
            return entity;
        }
        if (hit == null || !SightingRules.inWindow(limit, min)) {
            return null;
        }
        return blockTarget(level, hit.getBlockPos(), limit);
    }

    /** The block ray's walk: loaded chunks only, the collision shape as {@code COLLIDER} would give it. */
    private static final class Ray {
        static final BlockHitResult STOP = BlockHitResult.miss(Vec3.ZERO, Direction.UP, BlockPos.ZERO);
        final ServerChunkCache chunks;
        final Vec3 eye;
        final Vec3 end;
        LevelChunk chunk;
        int chunkX = Integer.MIN_VALUE;
        int chunkZ;
        BlockPos stoppedAt;

        Ray(ServerLevel level, Vec3 eye, Vec3 end) {
            chunks = level.getChunkSource();
            this.eye = eye;
            this.end = end;
        }
    }

    private static BlockHitResult visit(Ray ray, BlockPos pos) {
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        if (cx != ray.chunkX || cz != ray.chunkZ) {
            ray.chunk = ray.chunks.getChunkNow(cx, cz);
            ray.chunkX = cx;
            ray.chunkZ = cz;
        }
        if (ray.chunk == null) {
            ray.stoppedAt = pos.immutable();
            return Ray.STOP;
        }
        BlockState state = ray.chunk.getBlockState(pos);
        if (state.isAir()) {
            return null;
        }
        // The shape is read against the chunk itself, so no neighbour lookup can reach another chunk.
        VoxelShape shape = state.getCollisionShape(ray.chunk, pos);
        if (shape.isEmpty()) {
            return null;
        }
        return shape.clip(ray.eye, ray.end, pos);
    }

    private static boolean isScout(ServerPlayer player) {
        for (String tag : player.getTags()) {
            if (tag.startsWith("cc_class.") && tag.endsWith("scout")) {
                return true;
            }
        }
        return false;
    }

    private static boolean sightable(Entity entity) {
        if (entity instanceof FallingBlockEntity falling) {
            return falling.getBlockState().is(Blocks.MAGMA_BLOCK) || falling.getTags().contains(HOARD_FALL_TAG);
        }
        return entity.getType().is(SIGHTABLE_ENTITIES);
    }

    /** The nearest sightable entity along the ray between {@code min} and {@code limit}, swept in 16-block boxes. */
    private static Target entityTarget(ServerPlayer player, ServerLevel level, Vec3 eye, Vec3 look, Vec3 end, int min,
                                       double limit) {
        for (double a = min; a < limit; a += 16.0) {
            double b = Math.min(a + 16.0, limit);
            AABB box = new AABB(eye.add(look.scale(a)), eye.add(look.scale(b))).inflate(2.0);
            Entity best = null;
            double bestDistance = Double.MAX_VALUE;
            for (Entity e : level.getEntities(player, box, Sightings::sightable)) {
                Optional<Vec3> at = e.getBoundingBox().inflate(0.5).clip(eye, end);
                if (at.isPresent()) {
                    double d = eye.distanceTo(at.get());
                    if (d >= min && d <= limit && d < bestDistance) {
                        best = e;
                        bestDistance = d;
                    }
                }
            }
            if (best != null) {
                return entityTarget(level, best, bestDistance);
            }
        }
        return null;
    }

    private static Target entityTarget(ServerLevel level, Entity entity, double distance) {
        String uuid = entity.getStringUUID();
        BlockPos at = entity.blockPosition();
        if (entity instanceof FallingBlockEntity) {
            return family(SightingFamilies.HOARD_FALL, "e:" + uuid, distance, at);
        }
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        int butterfly = BUTTERFLIES.indexOf(id);
        if (butterfly == 0 && SightingRules.isDusk(level.getDayTime())
                && level.getEntities((Entity) null, entity.getBoundingBox().inflate(4.0),
                        e -> e.getType() == entity.getType()).size() >= 3) {
            return family(SightingFamilies.DOOR_CLOUD, "e:" + uuid, distance, at);
        }
        if (butterfly >= 0) {
            return new Target("e:" + uuid, 0, distance, null, null, 0L, null, butterfly);
        }
        SightingFamilies.Family f = SightingFamilies.get().entity(id);
        return f == null ? null : family(f.number(), "e:" + uuid, distance, at);
    }

    private static Target family(int number, String key, double distance, BlockPos pin) {
        SightingFamilies.Family f = SightingFamilies.get().number(number);
        return new Target(key, number, distance, pin, null, 0L, KEY + "family." + f.slug(), -1);
    }

    private static Target blockTarget(ServerLevel level, BlockPos pos, double distance) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) {
            return null;
        }
        BlockState state = chunk.getBlockState(pos);
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        SightingFamilies.Family f = SightingFamilies.get().block(blockId);
        if (f != null) {
            return family(f.number(), "b:" + pos.asLong(), distance, pos);
        }
        if (state.is(SIGHTABLE_BLOCKS)) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            return new Target("p:" + pos.asLong(), 0, distance, pos, null, 0L,
                    KEY + "pin." + id.getNamespace() + "." + id.getPath(), -1);
        }
        StructureHit structure = structureAt(level, pos);
        if (structure != null) {
            SightingFamilies.Family sf = SightingFamilies.get().number(structure.family());
            BlockPos pin = new ChunkPos(structure.start()).getMiddleBlockPosition(pos.getY());
            return new Target("s:" + structure.id() + "@" + structure.start(), sf.number(), distance, pin,
                    structure.id(), structure.start(), KEY + "family." + sf.slug(), -1);
        }
        HubSpot hub = hubAt(level, pos);
        if (hub != null) {
            return new Target("h:" + hub.thing() + "@" + hub.pos().asLong(), hub.family(), distance, hub.pos(), null, 0L,
                    KEY + "family." + SightingFamilies.get().number(hub.family()).slug(), -1);
        }
        if ("orespawn:chaos".equals(level.dimension().location().toString())) {
            return family(SightingFamilies.RED_ISLANDS, "c:red_islands", distance, null);
        }
        return null;
    }

    /** A structure start found at a block: the family, the structure id and the start chunk. */
    public record StructureHit(int family, String id, long start) {}

    /**
     * The family structure whose piece holds {@code pos}, read only from loaded chunks: the hit chunk's references,
     * then each reference's start chunk by {@code getChunkNow}; a reference whose start chunk isn't loaded is skipped.
     * Only structures of a sighting family are looked at. Null when nothing is found or the chunk isn't loaded.
     */
    public static StructureHit structureAt(ServerLevel level, BlockPos pos) {
        ServerChunkCache chunks = level.getChunkSource();
        LevelChunk chunk = chunks.getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null || !chunk.hasAnyStructureReferences()) {
            return null;
        }
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (Map.Entry<Structure, LongSet> entry : chunk.getAllReferences().entrySet()) {
            ResourceLocation id = registry.getKey(entry.getKey());
            SightingFamilies.Family f = id == null ? null : SightingFamilies.get().structure(id.toString());
            if (f == null) {
                continue;
            }
            LongIterator refs = entry.getValue().iterator();
            while (refs.hasNext()) {
                long ref = refs.nextLong();
                LevelChunk startChunk = chunks.getChunkNow(ChunkPos.getX(ref), ChunkPos.getZ(ref));
                if (startChunk == null) {
                    continue;
                }
                StructureStart start = startChunk.getStartForStructure(entry.getKey());
                if (start == null || !start.isValid() || !start.getBoundingBox().isInside(pos)) {
                    continue;
                }
                for (StructurePiece piece : start.getPieces()) {
                    if (piece.getBoundingBox().isInside(pos)) {
                        return new StructureHit(f.number(), id.toString(), ref);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Places put down by command record no structure start, so the hub list ({@code hub_places}) and Shelf 9's own
     * storage count as starts: a hit within 20 blocks of an entry whose thing is a family. Read once a second.
     */
    private static HubSpot hubAt(ServerLevel level, BlockPos pos) {
        MinecraftServer server = level.getServer();
        long now = server.getTickCount() / 20;
        if (now != hubCacheTick) {
            hubCacheTick = now;
            List<HubSpot> spots = new ArrayList<>();
            for (HubPlaces.Place place : HubPlaces.all(server)) {
                SightingFamilies.Family f = SightingFamilies.get().hubThing(place.thing());
                if (f != null) {
                    spots.add(new HubSpot(f.number(), place.pos(), place.dim(), place.thing()));
                }
            }
            CompoundTag shelf = server.getCommandStorage().get(HubPlaces.SHELF9_STORAGE);
            int[] p = shelf.getIntArray("pos");
            SightingFamilies.Family shelfFamily = SightingFamilies.get().hubThing("hatching_ground");
            if (shelf.getBoolean("placed") && p.length == 3 && shelfFamily != null) {
                spots.add(new HubSpot(shelfFamily.number(), new BlockPos(p[0], p[1], p[2]),
                        shelf.getString("dim"), "hatching_ground"));
            }
            hubCache = spots;
        }
        String dim = level.dimension().location().toString();
        for (HubSpot spot : hubCache) {
            if (spot.dim().equals(dim) && spot.pos().distSqr(pos) <= HUB_RADIUS_SQ) {
                return spot;
            }
        }
        return null;
    }

    /** Records a stamp for {@code player}. Public for the GameTests. */
    public static void stamp(ServerPlayer player, Target target) {
        SightingsData data = SightingsData.get(player.server);
        SightingsData.Player record = data.player(player.getUUID());
        boolean news = false;
        if (target.structure() != null) {
            news = record.starts.add(target.start(), data.structureId(target.structure()));
            if (news) {
                data.setDirty();
            }
        }
        int family = target.family();
        if (target.butterfly() >= 0) {
            family = butterflies(player, data, record, target.butterfly()) ? SightingFamilies.THREE_BUTTERFLIES : 0;
            if (family == 0) {
                return;
            }
        }
        if (family > 0 && stampFamily(player, data, record, family)) {
            news = true;
        }
        String nameKey = family > 0 ? KEY + "family." + SightingFamilies.get().number(family).slug() : target.nameKey();
        boolean pinned = false;
        if (target.pin() != null && nameKey != null) {
            String name = SightingNames.resolve(player, nameKey) + " · " + target.pin().getX() + ", " + target.pin().getZ();
            pinned = JourneyMapPins.add(player, data, record, name, target.pin());
        }
        if (!news && !pinned) {
            return;
        }
        Component line;
        if (family == SightingFamilies.BAD_EGG) {
            line = Component.translatable(KEY + "bad_egg");
        } else if (family == SightingFamilies.OIL_SPILL) {
            line = Component.translatable(KEY + "oil_spill");
        } else {
            line = Component.translatable(KEY + "sighted", Component.translatable(nameKey),
                    Mth.floor(target.distance()));
        }
        player.displayClientMessage(line, true);
    }

    /** Sets a family's bit and grants its page; true when it was new. Public for the GameTests. */
    public static boolean stampFamily(ServerPlayer player, SightingsData data, SightingsData.Player record, int family) {
        if (record.has(family)) {
            return false;
        }
        record.families |= SightingRules.bit(family);
        data.setDirty();
        WorldUtil.grant(player, ADVANCEMENT + SightingFamilies.get().number(family).slug());
        WorldUtil.setScore(player, STAMPS_SCORE, record.stamps());
        if (record.stamps() >= SightingRules.LAND_HO) {
            WorldUtil.grant(player, LAND_HO);
        }
        return true;
    }

    /** Family 49: all three butterflies sighted in one dusk. True when this one completes the set. */
    private static boolean butterflies(ServerPlayer player, SightingsData data, SightingsData.Player record, int which) {
        long dayTime = player.serverLevel().getDayTime();
        if (!SightingRules.isDusk(dayTime)) {
            return false;
        }
        long day = SightingRules.day(dayTime);
        if (record.butterflyDay != day) {
            record.butterflyDay = day;
            record.butterflyMask = 0;
        }
        record.butterflyMask |= 1 << which;
        data.setDirty();
        return record.butterflyMask == 0b111;
    }
}
