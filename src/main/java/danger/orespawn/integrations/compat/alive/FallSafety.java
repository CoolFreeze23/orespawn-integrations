package danger.orespawn.integrations.compat.alive;

import danger.orespawn.integrations.story.firsthour.FallRecord;
import danger.orespawn.integrations.story.firsthour.FirstHourUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a falling thing from the Hoard may land, the same rule word for word for the First Fall and the night's
 * Hoard Fall: a surface spot in a loaded chunk, open sky (no canopy), no fluid, natural ground, and never within 64
 * blocks of a respawn point, a signed Parish Stone or a block entity whose block is in
 * {@code #orespawn_integrations:homely}. Vanilla records no placer, so "someone lives here" is read from those blocks,
 * by one pass over the block entities already loaded in range, at landing only. An opened First Fall crate that is
 * empty is skipped; with anything stored in it, it counts like any chest. Nothing here loads or generates a chunk.
 */
final class FallSafety {

    static final int HOME_RADIUS = 64;
    static final int MAX_TRIES = 24;
    static final TagKey<Block> HOMELY = TagKey.create(Registries.BLOCK, FirstHourUtil.oi("homely"));
    /** Signed Parish Stones, once the Freguesia record exists (0.10.1+): list {@code stones} of {pos, dim}. */
    private static final net.minecraft.resources.ResourceLocation PARISH_STORAGE = FirstHourUtil.oi("freguesia");

    private FallSafety() {
    }

    /** Every "someone lives here" point within {@code reach} blocks of x, z, in one pass. */
    static List<BlockPos> homes(ServerLevel level, double x, double z, int reach) {
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
                    if (!near(pos, x, z, reachSq) || !be.getBlockState().is(HOMELY)) {
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

    /**
     * Up to {@link #MAX_TRIES} spots {@code minDist}-{@code maxDist} from the player, inside {@code coneDegrees}
     * around the look direction (360 for all around). Returns the surface position (the air above the ground) or
     * null when none passes.
     */
    static BlockPos findSpot(ServerLevel level, ServerPlayer player, double minDist, double maxDist,
                             double coneDegrees, RandomSource random) {
        double cx = player.getX();
        double cz = player.getZ();
        List<BlockPos> homes = homes(level, cx, cz, (int) maxDist + HOME_RADIUS);
        double look = Math.toRadians(player.getYRot() + 90.0F); // Minecraft yaw 0 faces +z
        double half = Math.toRadians(coneDegrees) / 2.0;
        for (int i = 0; i < MAX_TRIES; i++) {
            double angle = coneDegrees >= 360 ? random.nextDouble() * Math.PI * 2
                    : look + (random.nextDouble() * 2.0 - 1.0) * half;
            double dist = minDist + random.nextDouble() * (maxDist - minDist);
            int x = Mth.floor(cx + Math.cos(angle) * dist);
            int z = Mth.floor(cz + Math.sin(angle) * dist);
            BlockPos spot = surface(level, x, z);
            if (spot != null && farFromHomes(spot, homes)) {
                return spot;
            }
        }
        return null;
    }

    /** The air block over natural, dry, open-sky ground at x, z in a loaded chunk, or null. */
    static BlockPos surface(ServerLevel level, int x, int z) {
        if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) {
            return null;
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (y != level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) || y <= level.getMinBuildHeight() + 2) {
            return null; // under a canopy, or the void
        }
        BlockPos top = new BlockPos(x, y, z);
        BlockState ground = level.getBlockState(top.below());
        if (!level.getFluidState(top).isEmpty() || !level.getFluidState(top.below()).isEmpty()
                || !natural(ground)) {
            return null;
        }
        return top;
    }

    static boolean farFromHomes(BlockPos spot, List<BlockPos> homes) {
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
