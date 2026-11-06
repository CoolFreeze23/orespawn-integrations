package danger.orespawn.integrations.compat.alive;

/**
 * The Hoard Fall's numbers (event-hoard-fall, "Trigger and frequency"), kept free of game types so they can be tested
 * on their own. The shipped roll was 1 in 24,000 per night tick over a 12,000-tick night, about 0.39 a night; it is
 * one seeded roll a night now (world seed and night), so tomorrow's chance can be printed, while the hour and the
 * player it falls around stay unseeded. A postponed Fall comes back the next allowed night with its own chance plus
 * the usual one, capped at 0.9, and still at most one lands.
 */
public final class HoardFallRules {

    /** 1 - (1 - 1/24000)^12000. */
    public static final double NIGHT_CHANCE = 1.0 - Math.pow(1.0 - 1.0 / 24000.0, 12000.0);
    public static final double CAP = 0.9;
    /** The landing hour, drawn unseeded between these day times (night only). */
    public static final int EARLIEST = 13000;
    public static final int LATEST = 22000;
    /** The warning and the streak: 30 s ahead. */
    public static final int STREAK_TICKS = 30 * 20;
    public static final int WARN_RADIUS = 512;
    public static final int SAMPLER_DRAWS = 8;
    private static final long SALT = 0x5EEDFA11L;

    private HoardFallRules() {
    }

    /** Tonight's chance: the usual one, plus the usual one again when a Fall was postponed, capped at 0.9. */
    public static double chance(boolean carried) {
        return Math.min(CAP, carried ? NIGHT_CHANCE * 2 : NIGHT_CHANCE);
    }

    /** The seeded draw for {@code night} in [0, 1): the same world and night always draw the same. */
    public static double draw(long seed, long night) {
        long x = seed ^ (night * 0x9E3779B97F4A7C15L) ^ SALT;
        x ^= x >>> 33;
        x *= 0xff51afd7ed558ccdL;
        x ^= x >>> 33;
        x *= 0xc4ceb9fe1a85ec53L;
        x ^= x >>> 33;
        return (x >>> 11) * 0x1.0p-53;
    }

    /** True when the night's Fall rolls (the draw under tonight's chance). */
    public static boolean falls(long seed, long night, boolean carried) {
        return draw(seed, night) < chance(carried);
    }

    /** The night a day time belongs to: a night and the morning after share one index, so a slept night is known. */
    public static long night(long dayTime) {
        return Math.floorDiv(dayTime - 12000L, 24000L);
    }

    /** The compass quarter of a landing around its centre player ("north" is -z): north, east, south or west. */
    public static String quarter(double dx, double dz) {
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx > 0 ? "east" : "west";
        }
        return dz > 0 ? "south" : "north";
    }

    /** The hallmark band of a rank (Hatchling 1 to Fidalgo 4; Regent's 5 comes with the Regency). */
    public static int band(int rank) {
        return Math.max(1, Math.min(5, rank + 1));
    }
}
