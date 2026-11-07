package danger.orespawn.integrations.story.shelf9;

import danger.orespawn.integrations.OreSpawnIntegrations;
import danger.orespawn.integrations.config.IntegrationsConfig;
import danger.orespawn.integrations.story.StoryCore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Places Shelf 9 (structure-hatching-ground): once by itself at the first server start of a new world (world age under
 * 200 ticks, no flag in its SavedData), or by an operator's {@code /cc hatching_ground place}. After the template it
 * re-rolls the straw's three larder blocks, props up the outer ring where the ground dips, places and names the
 * waystone, writes the Register's rows and the Four Ways, records everything in SavedData and command storage, and (on
 * a new world, or when the operator asks) moves world spawn onto the straw.
 */
public final class HatchingGroundPlacer {

    public static final ResourceLocation TEMPLATE =
            ResourceLocation.fromNamespaceAndPath(OreSpawnIntegrations.MODID, "hatching_ground");
    static final ResourceKey<StructureProcessorList> PROCESSORS =
            ResourceKey.create(Registries.PROCESSOR_LIST, TEMPLATE);
    /** Template size and the local positions the code needs (see tools/templates/hatching_ground.py). */
    public static final int SIZE = 21;
    static final int GROUND = 3;
    static final BlockPos STRAW_CENTRE = new BlockPos(10, 5, 10);
    static final BlockPos WAYSTONE = new BlockPos(5, 4, 17);
    static final String[] FARM_LARDER = {
            "orespawn:gazelle_spawn_block", "orespawn:cow_spawn_block",
            "orespawn:sheep_spawn_block", "orespawn:chicken_spawn_block"};

    private HatchingGroundPlacer() {
    }

    /**
     * While the server starts (levels loaded, before "Done" and before any player can join): a live placement's waiting
     * Four Ways survey, then, on a new world's first start, the site search and Shelf 9 itself, or the skip logged.
     */
    static void onServerStarting(ServerStartingEvent event) {
        MinecraftServer server = event.getServer();
        try {
            HatchingGroundData data = HatchingGroundData.get(server);
            FourWays.runPending(server, data);
            if (data.firstStartDone) {
                return;
            }
            data.firstStartDone = true;
            data.setDirty();
            ServerLevel level = server.overworld();
            if (!IntegrationsConfig.on(IntegrationsConfig.storyHatchingGround) || data.placed
                    || level.getGameTime() >= 200) {
                return;
            }
            long start = System.nanoTime();
            BlockPos ground = settleFirstStart(level, SiteSearch.LAND, SiteSearch.FAR_RADIUS, SiteSearch.FAR_LIMIT_MS);
            if (ground != null) {
                OreSpawnIntegrations.LOGGER.info("Shelf 9 placed at {} in {} ms (site search, template, four locates)",
                        ground, (System.nanoTime() - start) / 1_000_000L);
            }
        } catch (Throwable t) {
            StoryCore.logOnce("shelf9.first_start", t);
        }
    }

    /**
     * A new world's search and what follows it: Shelf 9 at the site with the world's spawn on its straw, or, with no
     * site, one warning saying why and a world that plays on without it. Returns the site, or null. The rule, reach and
     * limit are parameters so the GameTests can make every search fail.
     */
    public static BlockPos settleFirstStart(ServerLevel level, SiteSearch.Rule rule, int farRadius, long limitMs) {
        Search search = findSite(level, level.getSharedSpawnPos(), rule, farRadius, limitMs);
        if (search.ground() == null) {
            if (search.timedOut()) {
                OreSpawnIntegrations.LOGGER.warn("Shelf 9: the site search reached its {} ms limit before finding a land"
                        + " patch within {} blocks of spawn; the world plays without it", limitMs, farRadius);
            } else {
                OreSpawnIntegrations.LOGGER.warn("Shelf 9: no land patch within {} blocks of spawn, nor by the nearest"
                        + " beach within 128; the world plays without it", farRadius);
            }
            return null;
        }
        place(level, search.ground(), true, "first_start");
        // A new world's spawn radius would scatter newborns over the shell; they wake on the straw instead.
        level.getGameRules().getRule(GameRules.RULE_SPAWN_RADIUS).set(0, level.getServer());
        return search.ground();
    }

    /** How a site search ended: the patch's centre at ground level or null, and whether its time limit stopped it. */
    public record Search(BlockPos ground, boolean timedOut) {
    }

    /**
     * Spirals out up to 48 blocks from {@code spawn} for a 21 x 21 patch of land; failing that, tries the nearest beach
     * within 128 blocks; failing that, spirals on from 48 blocks out to {@code farRadius}, with the time limit on this
     * last step alone (checked between candidates).
     */
    public static Search findSite(ServerLevel level, BlockPos spawn, SiteSearch.Rule rule, int farRadius, long limitMs) {
        BlockPos site = searchAround(level, spawn, SiteSearch.spiral(SiteSearch.RADIUS, 4), rule, Long.MAX_VALUE);
        if (site != null) {
            return new Search(site, false);
        }
        var beach = level.findClosestBiome3d(biome -> biome.is(BiomeTags.IS_BEACH), spawn, 128, 8, 8);
        if (beach != null) {
            site = searchAround(level, beach.getFirst(), SiteSearch.spiral(24, 4), rule, Long.MAX_VALUE);
            if (site != null) {
                return new Search(site, false);
            }
        }
        long started = System.nanoTime();
        site = searchAround(level, spawn, SiteSearch.spiralBeyond(SiteSearch.RADIUS, farRadius, 4), rule, limitMs);
        return new Search(site, site == null && (System.nanoTime() - started) / 1_000_000L >= limitMs);
    }

    private static BlockPos searchAround(ServerLevel level, BlockPos centre, List<int[]> offsets, SiteSearch.Rule rule,
                                         long limitMs) {
        long started = System.nanoTime();
        int n = SiteSearch.OFFSETS.length;
        int[] heights = new int[n * n];
        boolean[] unusable = new boolean[n * n];
        for (int[] offset : offsets) {
            if ((System.nanoTime() - started) / 1_000_000L >= limitMs) {
                return null;
            }
            int cx = centre.getX() + offset[0];
            int cz = centre.getZ() + offset[1];
            int i = 0;
            for (int ox : SiteSearch.OFFSETS) {
                for (int oz : SiteSearch.OFFSETS) {
                    int top = topAt(level, cx + ox, cz + oz);
                    heights[i] = top;
                    unusable[i] = unusableAt(level, cx + ox, top, cz + oz);
                    i++;
                }
            }
            if (rule.fits(cx, cz, heights, unusable)) {
                return new BlockPos(cx, SiteSearch.groundOf(heights), cz);
            }
        }
        return null;
    }

    /**
     * The top solid block at (x, z), generating its chunk first: {@code Level.getHeight} answers the bottom of the world
     * for a chunk that isn't loaded, without loading it.
     */
    public static int topAt(ServerLevel level, int x, int z) {
        return level.getChunk(x >> 4, z >> 4).getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
    }

    /** True when the read at (x, top, z) can't carry Shelf 9: fluid, or a deck with air right under it. */
    public static boolean unusableAt(ServerLevel level, int x, int top, int z) {
        if (!level.getBlockState(new BlockPos(x, top, z)).getFluidState().isEmpty()) {
            return true;
        }
        for (int down = 1; down <= SiteSearch.AIR_UNDER; down++) {
            if (!level.getBlockState(new BlockPos(x, top - down, z)).isAir()) {
                return false;
            }
        }
        return true;
    }

    /** The template's origin for a patch centred on {@code ground} (the shell floor at ground level). */
    public static BlockPos originFor(BlockPos ground) {
        return ground.offset(-SIZE / 2, -GROUND, -SIZE / 2);
    }

    /** True when every chunk of the 21 x 21 footprint around {@code ground} is loaded. */
    public static boolean footprintLoaded(ServerLevel level, BlockPos ground, int size) {
        int half = size / 2;
        for (int x = ground.getX() - half; x <= ground.getX() + half + 15; x += 16) {
            for (int z = ground.getZ() - half; z <= ground.getZ() + half + 15; z += 16) {
                int bx = Math.min(x, ground.getX() + half);
                int bz = Math.min(z, ground.getZ() + half);
                if (!level.hasChunk(bx >> 4, bz >> 4)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Places Shelf 9 centred on {@code ground}; returns the straw's spawn point, or null when the template is missing. */
    public static BlockPos place(ServerLevel level, BlockPos ground, boolean moveSpawn, String placedBy) {
        MinecraftServer server = level.getServer();
        Optional<StructureTemplate> found = level.getStructureManager().get(TEMPLATE);
        if (found.isEmpty()) {
            OreSpawnIntegrations.LOGGER.error("Shelf 9: template {} is missing", TEMPLATE);
            return null;
        }
        StructureTemplate template = found.get();
        BlockPos origin = originFor(ground);
        RandomSource random = RandomSource.create(level.getSeed() ^ origin.asLong());
        StructurePlaceSettings settings = new StructurePlaceSettings().setRandom(random).setIgnoreEntities(true);
        level.registryAccess().registry(Registries.PROCESSOR_LIST).flatMap(r -> r.getHolder(PROCESSORS))
                .map(Holder::value)
                .ifPresent(list -> {
                    for (StructureProcessor processor : list.list()) {
                        settings.addProcessor(processor);
                    }
                });
        template.placeInWorld(level, origin, origin, settings, random, Block.UPDATE_CLIENTS);

        HatchingGroundData data = HatchingGroundData.get(server);
        data.placed = true;
        data.origin = origin;
        data.dimension = level.dimension().location().toString();
        data.placedBy = placedBy;
        readSigns(template, origin, settings, data);
        rollLarder(level, template, origin, settings, data, random);
        propUpRing(level, origin);

        BlockPos spawn = origin.offset(STRAW_CENTRE);
        if (moveSpawn) {
            level.setDefaultSpawnPos(spawn, 0.0F);
        }
        if (ModList.get().isLoaded("waystones")) {
            try {
                data.waystone = WaystoneBridge.place(level, origin.offset(WAYSTONE));
            } catch (Throwable t) {
                StoryCore.logOnce("shelf9.waystone_place", t);
            }
        }
        // Everyone already on the Register gets the stone too (a hub placing Shelf 9 after its players joined).
        if (data.waystone != null) {
            data.pendingWaystone.addAll(data.lines.keySet());
        }
        data.setDirty();
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (data.pendingWaystone.contains(online.getUUID())) {
                activateWaystone(online);
            }
        }
        HubPlaces.writeShelf9(server, spawn, data.dimension);
        HatchRegister.writeAll(server);
        try {
            if ("first_start".equals(placedBy)) {
                FourWays.locateAndWrite(level, origin.offset(10, 4, 10), data);
            } else {
                FourWays.markPending(level, origin.offset(10, 4, 10), data);
            }
        } catch (Throwable t) {
            StoryCore.logOnce("shelf9.ways", t);
        }
        return spawn;
    }

    /** The signs our code writes are found by their "cc_role" marker in the template's block-entity data. */
    private static void readSigns(StructureTemplate template, BlockPos origin, StructurePlaceSettings settings,
                                  HatchingGroundData data) {
        BlockPos[] register = new BlockPos[RegisterLayout.SIGNS];
        List<StructureTemplate.StructureBlockInfo> signs = new ArrayList<>();
        signs.addAll(template.filterBlocks(origin, settings, Blocks.SPRUCE_WALL_SIGN));
        signs.addAll(template.filterBlocks(origin, settings, Blocks.SPRUCE_SIGN));
        for (StructureTemplate.StructureBlockInfo info : signs) {
            if (info.nbt() == null) {
                continue;
            }
            String role = info.nbt().getString("cc_role");
            if (role.startsWith("register_")) {
                int i = Integer.parseInt(role.substring("register_".length()));
                if (i >= 0 && i < register.length) {
                    register[i] = info.pos();
                }
            } else if (role.startsWith("ways_")) {
                int side = "nesw".indexOf(role.charAt(role.length() - 1));
                if (side >= 0) {
                    data.waysSigns[side] = info.pos();
                }
            }
        }
        data.registerSigns.clear();
        for (BlockPos pos : register) {
            if (pos != null) {
                data.registerSigns.add(pos);
            }
        }
    }

    /** The template's chicken larder placeholders become a random farm-class larder block each (never a boss part). */
    private static void rollLarder(ServerLevel level, StructureTemplate template, BlockPos origin,
                                   StructurePlaceSettings settings, HatchingGroundData data, RandomSource random) {
        data.straw.clear();
        data.strawBlocks.clear();
        ResourceLocation placeholder = ResourceLocation.parse(FARM_LARDER[3]);
        if (!BuiltInRegistries.BLOCK.containsKey(placeholder)) {
            return;
        }
        for (StructureTemplate.StructureBlockInfo info :
                template.filterBlocks(origin, settings, BuiltInRegistries.BLOCK.get(placeholder))) {
            String id = FARM_LARDER[random.nextInt(FARM_LARDER.length)];
            Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
            level.setBlock(info.pos(), block.defaultBlockState(), Block.UPDATE_ALL);
            data.straw.add(info.pos());
            data.strawBlocks.add(id);
        }
    }

    /** Where the ground dips under the outer ring (up to 5 blocks across the patch), dirt holds the pieces up. */
    private static void propUpRing(ServerLevel level, BlockPos origin) {
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                double d = Math.hypot(x - 10, z - 10);
                if (d <= 7.0) {
                    continue;
                }
                BlockPos top = origin.offset(x, GROUND + 1, z);
                if (level.getBlockState(top).isAir()) {
                    continue;
                }
                BlockPos below = top.below();
                for (int depth = 0; depth < 6; depth++, below = below.below()) {
                    BlockState state = level.getBlockState(below);
                    if (!state.isAir() && !state.canBeReplaced()) {
                        break;
                    }
                    level.setBlock(below, Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /**
     * Each new player's first join tops the straw back up: any of the three larder spots found empty (or holding
     * something else) gets its block back, so every Hatchling finds three.
     */
    static void topUpStraw(MinecraftServer server) {
        HatchingGroundData data = HatchingGroundData.get(server);
        if (!data.placed) {
            return;
        }
        topUpStraw(server.overworld(), data.straw, data.strawBlocks);
    }

    /** Re-places each larder block missing from its spot; returns how many were put back. */
    public static int topUpStraw(ServerLevel level, List<BlockPos> spots, List<String> ids) {
        int placed = 0;
        for (int i = 0; i < spots.size() && i < ids.size(); i++) {
            BlockPos pos = spots.get(i);
            Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(ids.get(i)));
            if (block == Blocks.AIR || level.getBlockState(pos).is(block)) {
                continue;
            }
            level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
            placed++;
        }
        return placed;
    }

    /** Activates the Shelf 9 waystone for a player; a stone not reachable now is owed at their next login. */
    static void activateWaystone(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null || !ModList.get().isLoaded("waystones")) {
            return;
        }
        HatchingGroundData data = HatchingGroundData.get(server);
        UUID id = data.waystone;
        boolean done = false;
        if (id != null) {
            try {
                done = WaystoneBridge.activate(player, id);
            } catch (Throwable t) {
                StoryCore.logOnce("shelf9.waystone_activate", t);
            }
        }
        boolean changed = done ? data.pendingWaystone.remove(player.getUUID())
                : data.pendingWaystone.add(player.getUUID());
        if (changed) {
            data.setDirty();
        }
    }
}
