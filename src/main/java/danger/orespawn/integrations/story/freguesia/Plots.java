package danger.orespawn.integrations.story.freguesia;

import danger.orespawn.integrations.story.StoryCore;
import danger.orespawn.integrations.story.firsthour.FirstHourUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

import java.util.Iterator;
import java.util.Optional;

/**
 * Plots (item-marco-da-freguesia, "Staking"): a signer's Plot Stake within 48 blocks of a signed stone joins that
 * stone's open stakes, and four of them on an axis-aligned rectangle's corners close a plot. A stake that leaves four
 * or more open stakes and closes nothing plays its line. No stakes, no structure: the Hamlet's tent goes up only on a
 * closed plot at least 8 by 8 (the shipped Monster Hunter tent, 8x8x7, placed by code), and only when nothing but air,
 * plants and the stakes stand where it would go. The tent's own villager is the Freguesia's Hunter. The 7x7 kiosk comes
 * with 0.14.0.
 */
final class Plots {

    private static final int MAX_STAKES = 16;
    private static final int TENT_MIN = 8;
    private static final String[] TENT_COLOURS = {"blue", "brown", "cyan", "dark_gray", "gray", "green", "purple",
            "red", "white"};

    private Plots() {
    }

    static void onStakePlaced(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (!StoneRecords.enabled()) {
            return;
        }
        try {
            FreguesiaData data = FreguesiaData.get(level.getServer());
            FreguesiaData.Stone stone = null;
            for (FreguesiaData.Stone s : data.near(StoneRecords.dim(level), pos.getX(), pos.getZ(), StandingRules.RADIUS)) {
                if (s.standing && s.isSigner(player.getUUID())) {
                    stone = s;
                    break;
                }
            }
            if (stone == null || stone.stakes.size() >= MAX_STAKES) {
                return;
            }
            stone.stakes.add(pos.immutable());
            data.setDirty();
            if (!close(stone, pos) && stone.stakes.size() >= 4) {
                FirstHourUtil.actionbar(player, Component.translatable(StoneRecords.K + "marco.stake_open"));
            }
        } catch (Throwable t) {
            StoryCore.logOnce("freguesia.stake", t);
        }
    }

    static void onStakeRemoved(ServerLevel level, BlockPos pos) {
        try {
            FreguesiaData data = FreguesiaData.get(level.getServer());
            for (FreguesiaData.Stone s : data.near(StoneRecords.dim(level), pos.getX(), pos.getZ(), StandingRules.RADIUS)) {
                if (s.stakes.remove(pos)) {
                    data.setDirty();
                }
                Iterator<int[]> it = s.plots.iterator();
                while (it.hasNext()) {
                    int[] p = it.next();
                    if (p[5] == 0 && corner(p, pos)) {
                        it.remove(); // an unused plot that loses a corner is open again; its other stakes stay open
                        for (BlockPos c : corners(p)) {
                            if (!c.equals(pos) && level.getBlockState(c).is(FreguesiaBlocks.ESTACA.get())) {
                                s.stakes.add(c);
                            }
                        }
                        data.setDirty();
                    }
                }
            }
        } catch (Throwable t) {
            StoryCore.logOnce("freguesia.stake_removed", t);
        }
    }

    /** Closes a plot on {@code pos} with three open stakes, if they make a rectangle; true when it did. */
    static boolean close(FreguesiaData.Stone s, BlockPos pos) {
        for (BlockPos q : s.stakes) {
            if (q.getX() == pos.getX() || q.getZ() == pos.getZ()) {
                continue;
            }
            BlockPos a = find(s, pos.getX(), q.getZ());
            BlockPos b = find(s, q.getX(), pos.getZ());
            if (a != null && b != null) {
                int[] plot = {Math.min(pos.getX(), q.getX()), Math.min(pos.getZ(), q.getZ()),
                        Math.max(pos.getX(), q.getX()), Math.max(pos.getZ(), q.getZ()),
                        Math.min(Math.min(pos.getY(), q.getY()), Math.min(a.getY(), b.getY())), 0};
                s.stakes.remove(pos);
                s.stakes.remove(q);
                s.stakes.remove(a);
                s.stakes.remove(b);
                s.plots.add(plot);
                return true;
            }
        }
        return false;
    }

    private static BlockPos find(FreguesiaData.Stone s, int x, int z) {
        for (BlockPos p : s.stakes) {
            if (p.getX() == x && p.getZ() == z) {
                return p;
            }
        }
        return null;
    }

    private static boolean corner(int[] p, BlockPos pos) {
        return (pos.getX() == p[0] || pos.getX() == p[2]) && (pos.getZ() == p[1] || pos.getZ() == p[3]);
    }

    private static BlockPos[] corners(int[] p) {
        return new BlockPos[]{new BlockPos(p[0], p[4], p[1]), new BlockPos(p[0], p[4], p[3]),
                new BlockPos(p[2], p[4], p[1]), new BlockPos(p[2], p[4], p[3])};
    }

    /** The plot's width and depth, corner stakes included. */
    static int width(int[] p) {
        return p[2] - p[0] + 1;
    }

    static int depth(int[] p) {
        return p[3] - p[1] + 1;
    }

    /** True when a plot of this size can take the tent. */
    static boolean takesTent(int width, int depth) {
        return width >= TENT_MIN && depth >= TENT_MIN;
    }

    // --- the Hamlet's tent ---

    static void raiseTent(ServerLevel level, FreguesiaData.Stone s) {
        for (int[] p : s.plots) {
            if (p[5] != 0 || !takesTent(width(p), depth(p))) {
                continue;
            }
            String colour = TENT_COLOURS[level.random.nextInt(TENT_COLOURS.length)];
            Optional<StructureTemplate> template = level.getStructureManager().get(
                    ResourceLocation.fromNamespaceAndPath("monster_hunter_villager", "monster_hunter_tent_" + colour));
            if (template.isEmpty()) {
                return;
            }
            Vec3i size = template.get().getSize();
            BlockPos origin = new BlockPos(p[0], p[4] - 1, p[1]); // the template's floor is the ground layer
            if (size.getX() > width(p) || size.getZ() > depth(p) || !clear(level, origin, size)) {
                continue;
            }
            p[5] = 1; // used before the stakes come out, so their removal doesn't reopen it
            for (BlockPos c : corners(p)) {
                if (level.getBlockState(c).is(FreguesiaBlocks.ESTACA.get())) {
                    level.setBlock(c, Blocks.AIR.defaultBlockState(), 3);
                }
            }
            template.get().placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.random, 2);
            AABB box = new AABB(origin).expandTowards(size.getX(), size.getY(), size.getZ()).inflate(1.0);
            for (Entity e : level.getEntities((Entity) null, box, e -> e instanceof Villager)) {
                Arrivals.tag(e, Arrivals.HUNTER);
            }
            return;
        }
    }

    /** Nothing but air, plants, snow and our stakes above the ground layer of the footprint. */
    private static boolean clear(ServerLevel level, BlockPos origin, Vec3i size) {
        for (BlockPos p : BlockPos.betweenClosed(origin.above(), origin.offset(size.getX() - 1, size.getY() - 1,
                size.getZ() - 1))) {
            if (!level.isLoaded(p)) {
                return false;
            }
            BlockState there = level.getBlockState(p);
            if (!there.isAir() && !there.canBeReplaced() && !there.is(FreguesiaBlocks.ESTACA.get())) {
                return false;
            }
        }
        return true;
    }
}
