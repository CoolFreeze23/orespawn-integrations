package danger.orespawn.integrations.story.week;

/**
 * The Almanac's die, exactly (system-almanac, "The die, exactly"), plain Java so it can be unit tested. Each pick
 * draws from its own stream seeded {@code mix(worldSeed, day, salt)}, salts fixed in code, so a restart never changes
 * a day's roll, {@code roll(day + 1)} is tomorrow's paper, and a pick added later with a new salt leaves every earlier
 * pick of a saved day as it was.
 */
public final class AlmanacRules {

    public static final int SALT_HABITS = 1;
    public static final int SALT_FOLKLORE = 2;
    public static final int SALT_DISH = 3;
    public static final int SALT_DREAM = 4;
    public static final int SALT_HOARD_FALL = 5;
    public static final int SALT_SCHEDULE = 6;
    /** The Day's Misfile (system-extravio), rolled in the same dawn on its own stream. */
    public static final int SALT_MISFILE = 7;

    /** Night slot places (the world-event priority rule); a lower number wins. */
    public static final int PLACE_RECONSTRUCTION = 10;
    public static final int PLACE_BALANCA = 20;
    public static final int PLACE_CORONATION = 30;
    public static final int PLACE_DATED_HOLIDAY = 35;
    public static final int PLACE_LAND_FIXTURE = 40;
    public static final int PLACE_BABY_SHOWER = 45;
    public static final int PLACE_OBJECTION = 50;
    public static final int PLACE_PROVINCE_SCENE = 60;
    public static final int PLACE_WHO_COMES = 70;
    public static final int PLACE_HABIT = 80;
    public static final int PLACE_HOARD_FALL = 90;
    public static final int PLACE_BALL = 100;
    /** An event this table doesn't know takes the habit's place. */
    public static final int PLACE_DEFAULT = PLACE_HABIT;

    /** Two world events a night on the server, at least 15 real minutes apart, one per dimension. */
    public static final int SERVER_SLOTS = 2;
    public static final long SPACING_TICKS = 15L * 60L * 20L;

    private AlmanacRules() {
    }

    /** The seed of one pick's stream: a SplitMix64 finaliser over the world seed, the day and the salt. */
    public static long mix(long worldSeed, long day, int salt) {
        long z = worldSeed ^ (day * 0x9E3779B97F4A7C15L) ^ ((long) salt * 0xC2B2AE3D27D4EB4FL);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /**
     * The place of a world event key in the priority list. Keys are the cards' ids or short names: the Thursday Mula,
     * the Friday Fado and the Sunday Mapinguari are the Land's fixtures (place 4); the Hoard Fall is place 9.
     */
    public static int place(String eventKey) {
        if (eventKey == null) {
            return PLACE_DEFAULT;
        }
        String k = eventKey.toLowerCase(java.util.Locale.ROOT);
        if (k.contains("reconstitu")) {
            return PLACE_RECONSTRUCTION;
        }
        if (k.contains("noite_da_volta") || k.contains("grande_engolida") || k.contains("balanca")) {
            return PLACE_BALANCA;
        }
        if (k.contains("agouro") || k.contains("coronation") || k.contains("coroacao")) {
            return PLACE_CORONATION;
        }
        if (k.contains("acerto") || k.contains("pascoa") || k.contains("holiday")) {
            return PLACE_DATED_HOLIDAY;
        }
        if (k.contains("mula") || k.contains("fado") || k.contains("mapinguar")) {
            return PLACE_LAND_FIXTURE;
        }
        if (k.contains("cha_de_bebe")) {
            return PLACE_BABY_SHOWER;
        }
        if (k.contains("impugna") || k.contains("objection")) {
            return PLACE_OBJECTION;
        }
        if (k.contains("province") || k.contains("provincia")) {
            return PLACE_PROVINCE_SCENE;
        }
        if (k.contains("audit") || k.contains("heist") || k.contains("hidra") || k.contains("pretend")) {
            return PLACE_WHO_COMES;
        }
        if (k.contains("hoard_fall") || k.contains("queda")) {
            return PLACE_HOARD_FALL;
        }
        if (k.contains("ball") || k.contains("baile")) {
            return PLACE_BALL;
        }
        return PLACE_DEFAULT;
    }

    /**
     * The Land's fixture that holds the overworld's night slot on a weekday (0 Monday ... 6 Sunday), or null. Thursday
     * is the Mula's road and Friday the Lobisomem's, from before their nights ship, so those two nights stay quiet and
     * the Hoard Fall moves off them with its reason.
     */
    public static String fixture(int weekday) {
        return switch (weekday) {
            case 3 -> "event-mula-trail";
            case 4 -> "event-fado-do-lobisomem";
            default -> null;
        };
    }

    /** The Hoard Fall's postponement reason key on a fixture's night, or null. */
    public static String postponeReason(int weekday) {
        return switch (weekday) {
            case 3 -> "thursday";
            case 4 -> "friday";
            default -> null;
        };
    }

    /**
     * Whether a claim may take a night slot: the dimension's slot must be free (or the claimant must outrank its
     * holder, see {@link #outranks}), the server must hold fewer than two, and the last start on the server must be
     * 15 real minutes back.
     */
    public static boolean serverRoom(int heldTonight, long ticksSinceLastStart) {
        return heldTonight < SERVER_SLOTS && (heldTonight == 0 || ticksSinceLastStart >= SPACING_TICKS);
    }

    /** True when an event at {@code place} takes a slot held by an event at {@code heldPlace} (higher place wins). */
    public static boolean outranks(int place, int heldPlace) {
        return place < heldPlace;
    }
}
