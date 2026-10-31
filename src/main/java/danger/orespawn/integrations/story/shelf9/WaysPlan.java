package danger.orespawn.integrations.story.shelf9;

import java.util.List;

/**
 * The Four Ways as pure logic (unit tested): each side of Shelf 9 names the nearest found thing whose bearing falls on
 * that side (north, east, south or west, by the larger axis), within 512 blocks; a side with nothing reads
 * "...nothing · Yet."
 */
public final class WaysPlan {

    public static final int MAX_DISTANCE = 512;
    public static final String[] SIDES = {"north", "east", "south", "west"};
    public static final String[] ARROWS = {"↑", "→", "↓", "←"};

    /** A located thing: its kind (village, henge, tower, bar, hunt_lodge) and its offset from Shelf 9. */
    public record Found(String kind, int dx, int dz) {
        public int distance() {
            return (int) Math.round(Math.sqrt((double) dx * dx + (double) dz * dz));
        }
    }

    private WaysPlan() {
    }

    /** Side index of an offset: 0 north (-z), 1 east (+x), 2 south (+z), 3 west (-x). */
    public static int side(int dx, int dz) {
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? 1 : 3;
        }
        return dz >= 0 ? 2 : 0;
    }

    /** For each side, the nearest thing on it within {@link #MAX_DISTANCE}, or null. */
    public static Found[] assign(List<Found> found) {
        Found[] sides = new Found[4];
        for (Found f : found) {
            if (f == null || f.distance() > MAX_DISTANCE || (f.dx() == 0 && f.dz() == 0)) {
                continue;
            }
            int s = side(f.dx(), f.dz());
            if (sides[s] == null || f.distance() < sides[s].distance()) {
                sides[s] = f;
            }
        }
        return sides;
    }

    /** The distance as signed: rounded to the nearest 10 blocks ("~{distance} blocks"). */
    public static int roundedDistance(Found f) {
        return Math.max(10, (int) Math.round(f.distance() / 10.0) * 10);
    }
}
