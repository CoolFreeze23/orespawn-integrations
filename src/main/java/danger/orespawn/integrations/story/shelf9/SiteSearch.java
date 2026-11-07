package danger.orespawn.integrations.story.shelf9;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Shelf 9's site rule, as pure logic (unit tested): a 21 x 21 patch fits when its 25 heightmap reads (a 5 x 5 grid
 * across the patch) spread by 5 blocks or less and none of them is unusable (fluid, or a deck with air under it, as a
 * sky-ship's is). Candidates are tried nearest first, out to
 * 48 blocks from world spawn, then, after the beach, from there out to 192 under a time limit.
 */
public final class SiteSearch {

    public static final int MAX_SPREAD = 5;
    public static final int RADIUS = 48;
    /** The last step's reach: a new world's spawn moves to the straw, so the player never sees the distance. */
    public static final int FAR_RADIUS = 192;
    /** The last step's limit: each read of an ungenerated chunk generates it while the server is still starting. */
    public static final long FAR_LIMIT_MS = 30_000L;
    /** The read offsets from the patch centre: -10, -5, 0, 5, 10 on each axis. */
    public static final int[] OFFSETS = {-10, -5, 0, 5, 10};
    /** A read with this many blocks of air right under it is standing on something built in the sky, not on land. */
    public static final int AIR_UNDER = 4;

    private SiteSearch() {
    }

    /** Whether the reads at candidate centre (x, z) make a site; {@link #LAND} is the world's own rule. */
    @FunctionalInterface
    public interface Rule {
        boolean fits(int x, int z, int[] heights, boolean[] unusable);
    }

    public static final Rule LAND = (x, z, heights, unusable) -> fits(heights, unusable);

    /** True when the 25 reads fit: spread at most {@link #MAX_SPREAD}, none unusable. */
    public static boolean fits(int[] heights, boolean[] unusable) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int i = 0; i < heights.length; i++) {
            if (unusable[i]) {
                return false;
            }
            min = Math.min(min, heights[i]);
            max = Math.max(max, heights[i]);
        }
        return max - min <= MAX_SPREAD;
    }

    /** The ground level a fitting patch is placed at: the median read, so one stump or hollow can't lift or sink it. */
    public static int groundOf(int[] heights) {
        int[] sorted = heights.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

    /** Candidate centre offsets (dx, dz) within {@code radius}, every {@code step} blocks, nearest first. */
    public static List<int[]> spiral(int radius, int step) {
        List<int[]> out = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx += step) {
            for (int dz = -radius; dz <= radius; dz += step) {
                if (dx * dx + dz * dz <= radius * radius) {
                    out.add(new int[]{dx, dz});
                }
            }
        }
        out.sort(Comparator.comparingInt(o -> o[0] * o[0] + o[1] * o[1]));
        return out;
    }

    /** {@link #spiral} without the candidates an earlier search out to {@code inner} has already turned down. */
    public static List<int[]> spiralBeyond(int inner, int radius, int step) {
        List<int[]> out = spiral(radius, step);
        out.removeIf(o -> o[0] * o[0] + o[1] * o[1] <= inner * inner);
        return out;
    }
}
