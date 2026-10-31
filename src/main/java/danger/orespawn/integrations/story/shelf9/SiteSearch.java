package danger.orespawn.integrations.story.shelf9;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Shelf 9's site rule, as pure logic (unit tested): a 21 x 21 patch fits when its 25 heightmap reads (a 5 x 5 grid
 * across the patch) spread by 5 blocks or less and none of them is fluid. Candidates are tried nearest first, out to
 * 48 blocks from world spawn.
 */
public final class SiteSearch {

    public static final int MAX_SPREAD = 5;
    public static final int RADIUS = 48;
    /** The read offsets from the patch centre: -10, -5, 0, 5, 10 on each axis. */
    public static final int[] OFFSETS = {-10, -5, 0, 5, 10};

    private SiteSearch() {
    }

    /** True when the 25 reads fit: spread at most {@link #MAX_SPREAD}, no fluid. */
    public static boolean fits(int[] heights, boolean[] fluid) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int i = 0; i < heights.length; i++) {
            if (fluid[i]) {
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
}
