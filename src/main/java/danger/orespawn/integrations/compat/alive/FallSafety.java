package danger.orespawn.integrations.compat.alive;

import danger.orespawn.integrations.story.firsthour.FallRecord;
import danger.orespawn.integrations.story.firsthour.FirstHourUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a falling thing from the Hoard may land, the same rule word for word for the First Fall and the night's
 * Hoard Fall: a surface spot in a loaded chunk, open sky (no canopy), no fluid, natural ground, and never within 64
 * blocks of a respawn point, a signed Parish Stone or a block entity whose block is in
 * {@code #orespawn_integrations:homely}. Vanilla records no placer, so "someone lives here" is read from those blocks,
 * by one pass over the block entities already loaded in range, at landing only. An opened First Fall crate that is
 * empty is skipped; with anything stored in it, it counts like any chest. Nothing here loads or generates a chunk.
 *
 * <p>Only what someone could live by counts (BOSS ruling, 2026-10-31): a Lootr container, or any container still holding
 * its unrolled loot table, is a structure's and never a home; and a homely block entity counts only from
 * {@link #BELOW_SURFACE} blocks under to {@link #ABOVE_SURFACE} over its column's surface, so a buried dungeon or a sky
 * ship never keeps a Fall away (the surface is the column's natural ground, as the generator makes it). A looted container, or one a player placed, counts like any other inside that window.
 * Respawn points and signed Parish Stones count at any height.</p>
 *
 * <p>A newcomer near spawn (event-first-fall): for a new player's own First Fall only, world spawn, their own respawn
 * point and the homely blocks they placed in the last game day need {@link #NEWCOMER_RADIUS} blocks instead of 64;
 * everyone else's keep the full 64. Its second try looks 24-48 blocks around the player, on open ground they can see.</p>
 */
public final class FallSafety {

    public static final int HOME_RADIUS = 64;
    public static final int NEWCOMER_RADIUS = 24;
    public static final int BELOW_SURFACE = 16;
    public static final int ABOVE_SURFACE = 32;
    static final int RING_MIN = 24;
    static final int RING_MAX = 48;

    /** A point someone lives at, and how far a Fall must keep from it. */
    public record Home(BlockPos pos, int radius) {
    }

    /** Why the candidates of one try were turned down, for the log line of a try that found nothing. */
    public static final class Tally {
        int unloaded, canopy, wet, built, home, view;

        @Override
        public String toString() {
            return "unloaded " + unloaded + ", canopy " + canopy + ", water " + wet + ", built " + built
                    + ", near a home " + home + ", out of view " + view;
        }
    }
    static final int MAX_TRIES = 24;
    public static final TagKey<Block> HOMELY = TagKey.create(Registries.BLOCK, FirstHourUtil.oi("homely"));
    /** Signed Parish Stones, once the Freguesia record exists (0.10.1+): list {@code stones} of {pos, dim}. */
    private static final net.minecraft.resources.ResourceLocation PARISH_STORAGE = FirstHourUtil.oi("freguesia");

    private FallSafety() {
    }

    /** Every "someone lives here" point within {@code reach} blocks of x, z, in one pass. */
    public static List<BlockPos> homes(ServerLevel level, double x, double z, int reach) {
        List<BlockPos> homes = new ArrayList<>();
        int reachSq = reach * reach;
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            BlockPos respawn = p.getRespawnPosition();
            if (respawn != null && p.getRespawnDimension() == level.dimension() && near(respawn, x, z, reachSq)) {
                homes.add(respawn);
            }
        }
        CompoundTag parish = level.getServer().getCommandStorage().get(PARISH_STORAGE);
        for (Tag t : parish.getList("stones", Tag.TAG_COMPOUND)) {
            CompoundTag stone = (CompoundTag) t;
            int[] pos = stone.getIntArray("pos");
            if (pos.length == 3 && level.dimension().location().toString().equals(stone.getString("dim"))) {
                BlockPos at = new BlockPos(pos[0], pos[1], pos[2]);
                if (near(at, x, z, reachSq)) {
                    homes.add(at);
                }
            }
        }
        FallRecord record = FallRecord.get(level.getServer());
        int minCx = Mth.floor(x - reach) >> 4;
        int maxCx = Mth.floor(x + reach) >> 4;
        int minCz = Mth.floor(z - reach) >> 4;
        int maxCz = Mth.floor(z + reach) >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockPos pos = be.getBlockPos();
                    if (!near(pos, x, z, reachSq) || !be.getBlockState().is(HOMELY) || natural(be)
                            || !inSurfaceWindow(pos.getY(), naturalSurface(level, pos.getX(), pos.getZ()))) {
                        continue;
                    }
                    if (record.isOpenedCrate(pos) && be instanceof Container c && c.isEmpty()) {
                        continue; // an emptied First Fall crate keeps no Fall away
                    }
                    homes.add(pos);
                }
            }
        }
        return homes;
    }

    /** A structure's container nobody lives by: Lootr's, or one whose loot table has not been rolled yet. */
    static boolean natural(BlockEntity be) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock());
        if ("lootr".equals(id.getNamespace())) {
            return true;
        }
        return be instanceof RandomizableContainer loot && loot.getLootTable() != null;
    }

    /**
     * Whether a block entity at {@code y} sits where a surface crater could matter: from {@link #BELOW_SURFACE} under
     * to {@link #ABOVE_SURFACE} over the column's natural surface (see {@link #naturalSurface}).
     */
    public static boolean inSurfaceWindow(int y, int surface) {
        return y >= surface - BELOW_SURFACE && y <= surface + ABOVE_SURFACE;
    }

    /**
     * The first free block over the column's natural ground, as the generator makes it, without structures or builds.
     * The live heightmap would not do: over a sky ship's furnace it reads the ship's own roof, so the furnace would sit
     * "at the surface". One noise column per homely block entity, read only on a landing try.
     */
    static int naturalSurface(ServerLevel level, int x, int z) {
        return level.getChunkSource().getGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level,
                level.getChunkSource().randomState());
    }

    /**
     * The homes a new player's own First Fall keeps from: world spawn, their own respawn point and the homely blocks
     * they placed in the last game day at {@link #NEWCOMER_RADIUS}; every other home at {@link #HOME_RADIUS}.
     */
    public static List<Home> homesFor(ServerLevel level, ServerPlayer newcomer, double x, double z, int reach) {
        List<Home> out = new ArrayList<>();
        BlockPos own = newcomer.getRespawnDimension() == level.dimension() ? newcomer.getRespawnPosition() : null;
        FallRecord.Entry entry = FallRecord.get(level.getServer()).find(newcomer.getUUID());
        long now = level.getGameTime();
        for (BlockPos pos : homes(level, x, z, reach)) {
            boolean mine = pos.equals(own) || (entry != null && entry.placedRecently(pos, now));
            out.add(new Home(pos, mine ? NEWCOMER_RADIUS : HOME_RADIUS));
        }
        if (level.dimension() == Level.OVERWORLD) {
            out.add(new Home(level.getSharedSpawnPos(), NEWCOMER_RADIUS));
        }
        return out;
    }

    /** {@code homes} at the full {@link #HOME_RADIUS}, for every Fall but a newcomer's own. */
    static List<Home> atFullRadius(List<BlockPos> homes) {
        List<Home> out = new ArrayList<>(homes.size());
        for (BlockPos pos : homes) {
            out.add(new Home(pos, HOME_RADIUS));
        }
        return out;
    }

    /**
     * Up to {@link #MAX_TRIES} spots {@code minDist}-{@code maxDist} from the player, inside {@code coneDegrees}
     * around the look direction (360 for all around). Returns the surface position (the air above the ground) or
     * null when none passes.
     */
    static BlockPos findSpot(ServerLevel level, ServerPlayer player, double minDist, double maxDist,
                             double coneDegrees, RandomSource random) {
        return findSpot(level, player, minDist, maxDist, coneDegrees, random,
                atFullRadius(homes(level, player.getX(), player.getZ(), (int) maxDist + HOME_RADIUS)), new Tally());
    }

    static BlockPos findSpot(ServerLevel level, ServerPlayer player, double minDist, double maxDist,
                             double coneDegrees, RandomSource random, List<Home> homes, Tally tally) {
        double cx = player.getX();
        double cz = player.getZ();
        double look = Math.toRadians(player.getYRot() + 90.0F); // Minecraft yaw 0 faces +z
        double half = Math.toRadians(coneDegrees) / 2.0;
        for (int i = 0; i < MAX_TRIES; i++) {
            double angle = coneDegrees >= 360 ? random.nextDouble() * Math.PI * 2
                    : look + (random.nextDouble() * 2.0 - 1.0) * half;
            double dist = minDist + random.nextDouble() * (maxDist - minDist);
            int x = Mth.floor(cx + Math.cos(angle) * dist);
            int z = Mth.floor(cz + Math.sin(angle) * dist);
            BlockPos spot = surface(level, x, z, tally);
            if (spot != null && farFrom(spot, homes, tally)) {
                return spot;
            }
        }
        return null;
    }

    /**
     * A newcomer's second try: up to {@link #MAX_TRIES} spots {@link #RING_MIN}-{@link #RING_MAX} blocks all around
     * the player, on open ground they can see (one ray from their eyes per candidate that passes the rest).
     */
    public static BlockPos ringInView(ServerLevel level, ServerPlayer player, List<Home> homes, RandomSource random,
                                      Tally tally) {
        Vec3 eyes = player.getEyePosition();
        for (int i = 0; i < MAX_TRIES; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = RING_MIN + random.nextDouble() * (RING_MAX - RING_MIN);
            int x = Mth.floor(player.getX() + Math.cos(angle) * dist);
            int z = Mth.floor(player.getZ() + Math.sin(angle) * dist);
            BlockPos spot = surface(level, x, z, tally);
            if (spot == null || !farFrom(spot, homes, tally)) {
                continue;
            }
            if (inView(level, eyes, spot)) {
                return spot;
            }
            tally.view++;
        }
        return null;
    }

    /**
     * Whether nothing opaque stands between the eyes and the spot: one walk along the ray through every block it
     * crosses, stopped by the first block that occludes. See-through blocks (glass, leaves, barriers) do not hide it.
     */
    static boolean inView(ServerLevel level, Vec3 eyes, BlockPos spot) {
        Vec3 target = Vec3.atCenterOf(spot);
        Boolean seen = BlockGetter.traverseBlocks(eyes, target, level, (lvl, pos) -> {
            if (pos.equals(spot)) {
                return Boolean.TRUE;
            }
            return lvl.getBlockState(pos).canOcclude() ? Boolean.FALSE : null;
        }, lvl -> Boolean.TRUE);
        return seen == null || seen;
    }

    /** The air block over natural, dry, open-sky ground at x, z in a loaded chunk, or null. */
    static BlockPos surface(ServerLevel level, int x, int z) {
        return surface(level, x, z, new Tally());
    }

    static BlockPos surface(ServerLevel level, int x, int z, Tally tally) {
        if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) {
            tally.unloaded++;
            return null;
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (y != level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) || y <= level.getMinBuildHeight() + 2) {
            tally.canopy++;
            return null; // under a canopy, or the void
        }
        BlockPos top = new BlockPos(x, y, z);
        BlockState ground = level.getBlockState(top.below());
        if (!level.getFluidState(top).isEmpty() || !level.getFluidState(top.below()).isEmpty()) {
            tally.wet++;
            return null;
        }
        if (!natural(ground)) {
            tally.built++;
            return null;
        }
        return top;
    }

    static boolean farFrom(BlockPos spot, List<Home> homes, Tally tally) {
        for (Home home : homes) {
            if (near(home.pos(), spot.getX(), spot.getZ(), home.radius() * home.radius())) {
                tally.home++;
                return false;
            }
        }
        return true;
    }

    public static boolean farFromHomes(BlockPos spot, List<BlockPos> homes) {
        int sq = HOME_RADIUS * HOME_RADIUS;
        for (BlockPos home : homes) {
            if (near(home, spot.getX(), spot.getZ(), sq)) {
                return false;
            }
        }
        return true;
    }

    /** Ground nobody built: dirt, sand, stone, gravel, clay, terracotta, snow and the plants on them. */
    static boolean natural(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.TERRACOTTA) || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY)
                || state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.SANDSTONE)
                || state.is(Blocks.RED_SANDSTONE) || state.is(Blocks.GRASS_BLOCK);
    }

    /** Plants and snow layers that may be cleared along with natural ground. */
    static boolean clearable(BlockState state) {
        return state.isAir() || state.is(BlockTags.REPLACEABLE_BY_TREES) || state.is(BlockTags.FLOWERS)
                || state.is(Blocks.SNOW);
    }

    private static boolean near(BlockPos pos, double x, double z, int distSq) {
        double dx = pos.getX() + 0.5 - x;
        double dz = pos.getZ() + 0.5 - z;
        return dx * dx + dz * dz <= distSq;
    }
}
