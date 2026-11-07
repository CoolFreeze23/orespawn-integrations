package danger.orespawn.integrations.story.hunt;

import danger.orespawn.integrations.OreSpawnIntegrations;
import danger.orespawn.integrations.config.IntegrationsConfig;
import danger.orespawn.integrations.story.firsthour.FirstHourUtil;
import danger.orespawn.integrations.story.shelf9.FourWays;
import danger.orespawn.integrations.story.shelf9.HatchingGroundData;
import danger.orespawn.integrations.story.shelf9.HatchingGroundPlacer;
import danger.orespawn.integrations.story.shelf9.HubPlaces;
import danger.orespawn.integrations.story.shelf9.SiteSearch;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.ArrayList;
import java.util.List;

/**
 * The Hunt Lodge on a new world whose ring found no start (structure-hunt-lodge): about half of tested worlds, mostly a ring
 * point out at sea. At first start only, and only when the same lookup the board uses finds no Lodge, this picks ground in the
 * ring's band around spawn and puts the real structure there with {@code /place structure}, then writes the hub list entry that
 * the errand, the Wall, the Field Odete and the Four Ways read first. It is the operator's {@code /cc hunt_lodge place} done for
 * them, so a placed Lodge is the Lodge the rest of the game already knows.
 *
 * <p>The band is scanned by biome alone, which generates nothing: the card's regions first, then any land. Each candidate's start
 * is built without being placed, so the land rule reads the box of the very Lodge {@code /place} would put there, rotation
 * included. The terrain generator screens each place first; the chunks a promising one reads are generated while the server is
 * still starting, so the search has its own limit.</p>
 */
public final class LodgeFallback {

    /** The ring's band: the card's 300 to 600 blocks from spawn. */
    public static final int BAND_MIN = 300;
    public static final int BAND_MAX = 600;
    private static final int STEP = 48;
    /** The search's limit, on top of Shelf 9's: a handheld can spend it on a first world, and the board has its other homes. */
    public static final long LIMIT_MS = 20_000L;
    private static final int READS = 5;

    private static final TagKey<Biome> REGIONS = TagKey.create(Registries.BIOME, FirstHourUtil.oi("hunt_lodge_preferred"));
    private static final TagKey<Biome> LAND = TagKey.create(Registries.BIOME, FirstHourUtil.oi("has_structure/hunt_lodge"));

    private LodgeFallback() {
    }

    /** One Lodge a world: the search runs only with both switches on, no ring start and nothing on the hub list. */
    public static boolean shouldSearch(boolean switchedOn, boolean ringStart, int onHubList) {
        return switchedOn && !ringStart && onHubList == 0;
    }

    /** Called from the first-start gate that places Shelf 9, after it, so the band is measured from where newborns wake. */
    public static void firstStart(MinecraftServer server) {
        ServerLevel level = server.overworld();
        boolean on = IntegrationsConfig.on(IntegrationsConfig.storyRoyalHunt) && IntegrationsConfig.on(IntegrationsConfig.storyHuntLodge);
        Holder<Structure> lodge = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolder(HuntLodge.LODGE).orElse(null);
        if (lodge == null) {
            return;
        }
        BlockPos spawn = level.getSharedSpawnPos();
        if (!shouldSearch(on, HuntLodge.hasStart(level, spawn) != null, HubPlaces.of(server, "hunt_lodge").size())) {
            return;
        }
        long started = System.nanoTime();
        List<ChunkPos> regions = new ArrayList<>();
        List<ChunkPos> land = new ArrayList<>();
        for (int[] offset : SiteSearch.spiralBeyond(BAND_MIN, BAND_MAX, STEP)) {
            int x = spawn.getX() + offset[0];
            int z = spawn.getZ() + offset[1];
            Holder<Biome> biome = level.getUncachedNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(level.getSeaLevel()), QuartPos.fromBlock(z));
            if (biome.is(REGIONS)) {
                regions.add(new ChunkPos(new BlockPos(x, 0, z)));
            } else if (biome.is(LAND)) {
                land.add(new ChunkPos(new BlockPos(x, 0, z)));
            }
        }
        regions.addAll(land);
        int tried = 0;
        for (ChunkPos chunk : regions) {
            if ((System.nanoTime() - started) / 1_000_000L >= LIMIT_MS) {
                OreSpawnIntegrations.LOGGER.warn("Hunt Lodge: the search reached its {} ms limit after {} of {} places; the board"
                        + " keeps its tents and desks, and an operator can run /cc hunt_lodge place", LIMIT_MS, tried, regions.size());
                return;
            }
            tried++;
            StructureStart start = lodge.value().generate(level.registryAccess(), level.getChunkSource().getGenerator(),
                    level.getChunkSource().getGenerator().getBiomeSource(), level.getChunkSource().randomState(),
                    level.getStructureManager(), level.getSeed(), chunk, 0, level, biome -> true);
            if (start.isValid() && looksFlat(level, start.getBoundingBox()) && fits(level, start.getBoundingBox())) {
                place(server, level, chunk, start.getBoundingBox(), started, tried);
                return;
            }
        }
        OreSpawnIntegrations.LOGGER.warn("Hunt Lodge: no ground for it among {} places {} to {} blocks from spawn; the board keeps its"
                + " tents and desks, and an operator can run /cc hunt_lodge place", regions.size(), BAND_MIN, BAND_MAX);
    }

    /**
     * The land rule's first pass from the terrain generator alone, which generates no chunk: flat enough, and dry, going by
     * the surface and the sea floor. Most of the band fails here, so {@link #fits} only pays for chunks on ground worth reading.
     */
    private static boolean looksFlat(ServerLevel level, BoundingBox box) {
        var generator = level.getChunkSource().getGenerator();
        var random = level.getChunkSource().randomState();
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int a = 0; a < READS; a++) {
            for (int b = 0; b < READS; b++) {
                int x = box.minX() + (box.maxX() - box.minX()) * a / (READS - 1);
                int z = box.minZ() + (box.maxZ() - box.minZ()) * b / (READS - 1);
                int top = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, random);
                if (top != generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random)) {
                    return false;
                }
                min = Math.min(min, top);
                max = Math.max(max, top);
                if (max - min > SiteSearch.MAX_SPREAD) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Shelf 9's land rule across the box of this Lodge, stopping at the first read that rules it out. */
    private static boolean fits(ServerLevel level, BoundingBox box) {
        int n = READS * READS;
        int[] heights = new int[n];
        boolean[] unusable = new boolean[n];
        int i = 0;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int a = 0; a < READS; a++) {
            for (int b = 0; b < READS; b++) {
                int x = box.minX() + (box.maxX() - box.minX()) * a / (READS - 1);
                int z = box.minZ() + (box.maxZ() - box.minZ()) * b / (READS - 1);
                int top = HatchingGroundPlacer.topAt(level, x, z);
                if (HatchingGroundPlacer.unusableAt(level, x, top, z)
                        || HatchingGroundPlacer.inAStructurePiece(level, new BlockPos(x, top, z))) {
                    return false;
                }
                min = Math.min(min, top);
                max = Math.max(max, top);
                if (max - min > SiteSearch.MAX_SPREAD) {
                    return false;
                }
                heights[i] = top;
                unusable[i] = false;
                i++;
            }
        }
        return SiteSearch.fits(heights, unusable);
    }

    private static void place(MinecraftServer server, ServerLevel level, ChunkPos chunk, BoundingBox box, long started, int tried) {
        for (ChunkPos c : (Iterable<ChunkPos>) ChunkPos.rangeClosed(new ChunkPos(box.minX() >> 4, box.minZ() >> 4),
                new ChunkPos(box.maxX() >> 4, box.maxZ() >> 4))::iterator) {
            level.getChunk(c.x, c.z);
        }
        BlockPos at = chunk.getWorldPosition();
        try {
            server.getCommands().getDispatcher().execute("place structure " + HuntLodge.LODGE.location() + " " + at.getX() + " 64 " + at.getZ(),
                    server.createCommandSourceStack().withLevel(level).withPermission(2).withSuppressedOutput());
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            OreSpawnIntegrations.LOGGER.warn("Hunt Lodge: /place refused {} at {}: {}; the board keeps its tents and desks",
                    HuntLodge.LODGE.location(), at, e.getMessage());
            return;
        }
        int cx = (box.minX() + box.maxX()) / 2;
        int cz = (box.minZ() + box.maxZ()) / 2;
        BlockPos middle = new BlockPos(cx, HatchingGroundPlacer.topAt(level, cx, cz) + 1, cz);
        HubPlaces.add(server, "hunt_lodge", middle, level.dimension().location().toString());
        HatchingGroundData shelf9 = HatchingGroundData.get(server);
        if (shelf9.placed) {
            FourWays.queueResurvey(shelf9);
        }
        OreSpawnIntegrations.LOGGER.info("Hunt Lodge placed at {} in {} ms ({} places tried; the ring had no start)", middle,
                (System.nanoTime() - started) / 1_000_000L, tried);
    }
}
