package danger.orespawn.integrations.world.sightings;

/**
 * The sighting rules that need no game (system-sightings): the distance window, the one-second rule, the dusk window
 * and the ring of sighted starts. Unit tested.
 */
public final class SightingRules {

    /** The ray's reach: the radius always sent at view distance 8 (seven whole chunks). */
    public static final double MAX_DISTANCE = 112.0;
    /** The most sighted starts kept per player; the oldest drops first. */
    public static final int MAX_STARTS = 512;
    /** The most stamps the Notebook counts toward Land Ho!. */
    public static final int LAND_HO = 40;
    /** Dusk, for the door cloud and the three butterflies: time of day 12,000 to 14,000. */
    public static final int DUSK_START = 12000;
    public static final int DUSK_END = 14000;

    private SightingRules() {
    }

    /** True when a target {@code distance} blocks away can be sighted by someone whose closest is {@code min}. */
    public static boolean inWindow(double distance, int min) {
        return distance >= min && distance <= MAX_DISTANCE;
    }

    /** A Scout sights from {@code scout} blocks, everyone else from {@code min}. */
    public static int minimum(boolean scout, int min, int scoutMin) {
        return scout ? Math.min(min, scoutMin) : min;
    }

    /** The one second: a target stamps when the previous 10-tick check saw the same key. */
    public static boolean secondOnTarget(String previous, String now) {
        return now != null && now.equals(previous);
    }

    public static boolean isDusk(long dayTime) {
        long t = Math.floorMod(dayTime, 24000L);
        return t >= DUSK_START && t < DUSK_END;
    }

    /** The game day a dusk belongs to, so "one dusk" is one day number. */
    public static long day(long dayTime) {
        return Math.floorDiv(dayTime, 24000L);
    }

    /** Bit for a family number (1-63) in the 64-bit stamped set. */
    public static long bit(int family) {
        return family >= 1 && family <= 63 ? 1L << family : 0L;
    }

    /** A fixed-size ring of longs: adding past the cap overwrites the oldest. */
    public static final class Ring {
        private final long[] values;
        private final int[] tags;
        private int size;
        private int head;

        public Ring(int capacity) {
            values = new long[capacity];
            tags = new int[capacity];
        }

        public int size() {
            return size;
        }

        public int capacity() {
            return values.length;
        }

        public boolean contains(long value, int tag) {
            for (int i = 0; i < size; i++) {
                if (values[i] == value && tags[i] == tag) {
                    return true;
                }
            }
            return false;
        }

        /** Adds {@code value} (with its tag) unless present; returns true when it was new. */
        public boolean add(long value, int tag) {
            if (contains(value, tag)) {
                return false;
            }
            values[head] = value;
            tags[head] = tag;
            head = (head + 1) % values.length;
            size = Math.min(size + 1, values.length);
            return true;
        }

        /** Oldest first. */
        public long valueAt(int i) {
            return values[index(i)];
        }

        public int tagAt(int i) {
            return tags[index(i)];
        }

        private int index(int i) {
            int start = size < values.length ? 0 : head;
            return (start + i) % values.length;
        }
    }
}
