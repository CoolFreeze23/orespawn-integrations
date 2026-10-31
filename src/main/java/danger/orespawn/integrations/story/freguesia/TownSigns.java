package danger.orespawn.integrations.story.freguesia;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The Town's two signs. The Tale Plaque raised at the stone's free ground the dawn its Freguesia becomes a Town
 * (system-placas-de-causo, kind {@code vila}; the plaque register itself comes with 0.10.2), once per record: a waxed
 * oak sign of four translate lines on natural ground, with no block entity in the 7x5x7 box around it. And the Royal
 * Rail stop: one sign beside the first Railcraft track within 16 blocks, looked for once, on the Town dawn.
 */
final class TownSigns {

    private static final String K = StoneRecords.K + "plaque.";
    private static final int NAME_CUT = 5;

    private TownSigns() {
    }

    /** True when the plaque stands (or there is no free ground yet: it is tried again next dawn). */
    static boolean plaque(ServerLevel level, FreguesiaData.Stone s) {
        Component thing = thing(s);
        for (int ring = 3; ring <= 8; ring++) {
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4;
                int x = s.pos.getX() + (int) Math.round(Math.cos(a) * ring);
                int z = s.pos.getZ() + (int) Math.round(Math.sin(a) * ring);
                BlockPos at = freeGround(level, x, z);
                if (at != null) {
                    sign(level, at, new Component[]{
                            Component.translatable(K + "vila.line1", thing),
                            Component.translatable(K + "vila.line2", thing),
                            Component.translatable(K + "vila.line3", thing),
                            Component.translatable(K + "vila.line4", thing)});
                    return true;
                }
            }
        }
        return false;
    }

    /** The town's short name on the plaque: an epithet's thing, a name tag's name, or the founder's (cut to fit). */
    private static Component thing(FreguesiaData.Stone s) {
        if (s.customName != null) {
            return Component.literal(cut(s.customName, 8));
        }
        if (s.epithetKey != null) {
            return Component.translatable(s.epithetKey);
        }
        return Component.translatable(K + "vila.default_thing", cut(s.founderName(), NAME_CUT));
    }

    private static String cut(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n);
    }

    /** Free ground: air over natural ground in a loaded chunk, with no block entity in the 7x5x7 box around it. */
    private static BlockPos freeGround(ServerLevel level, int x, int z) {
        BlockPos top = Arrivals.surface(level, x, z);
        if (top == null || !level.getBlockState(top).isAir()) {
            return null;
        }
        BlockState ground = level.getBlockState(top.below());
        if (!(ground.is(BlockTags.DIRT) || ground.is(BlockTags.SAND) || ground.is(BlockTags.BASE_STONE_OVERWORLD))) {
            return null;
        }
        LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
        if (chunk == null) {
            return null;
        }
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            BlockPos p = be.getBlockPos();
            if (Math.abs(p.getX() - x) <= 3 && Math.abs(p.getZ() - z) <= 3 && Math.abs(p.getY() - top.getY()) <= 2) {
                return null;
            }
        }
        return top;
    }

    private static void sign(ServerLevel level, BlockPos at, Component[] lines) {
        level.setBlockAndUpdate(at, Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION,
                level.random.nextInt(16)));
        if (!(level.getBlockEntity(at) instanceof SignBlockEntity sign)) {
            return;
        }
        SignText text = new SignText();
        for (int i = 0; i < 4 && i < lines.length; i++) {
            text = text.setMessage(i, lines[i]);
        }
        sign.setText(text, true);
        sign.setWaxed(true);
        sign.setChanged();
        level.sendBlockUpdated(at, sign.getBlockState(), sign.getBlockState(), 3);
    }

    /** The Royal Rail stop: one sign on free ground beside the first Railcraft track within 16 blocks. */
    static void railStop(ServerLevel level, FreguesiaData.Stone s) {
        BlockPos c = s.pos;
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-16, -4, -16), c.offset(16, 4, 16))) {
            if (!level.isLoaded(p)) {
                continue;
            }
            BlockState state = level.getBlockState(p);
            if (!state.is(BlockTags.RAILS) || !"railcraft".equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace())) {
                continue;
            }
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos at = p.relative(d, 2);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
                BlockPos spot = new BlockPos(at.getX(), y, at.getZ());
                if (Math.abs(y - p.getY()) <= 1 && level.getBlockState(spot).isAir()
                        && level.getBlockState(spot.below()).isFaceSturdy(level, spot.below(), Direction.UP)) {
                    sign(level, spot, new Component[]{
                            Component.translatable(StoneRecords.K + "rail_stop.line1"),
                            Component.translatable(StoneRecords.K + "rail_stop.line2")});
                    return;
                }
            }
            return;
        }
    }
}
