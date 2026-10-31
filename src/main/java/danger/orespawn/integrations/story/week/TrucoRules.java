package danger.orespawn.integrations.story.week;

import java.util.Locale;

/**
 * Saturday truco's rules as plain Java (wonder-saturday-truco, system-deed-log, npc-seu-ze): the reading's range and
 * size, which Saturday a missed reading belongs to, and the Gazette's order of deeds.
 */
public final class TrucoRules {

    /** Seu Zé tells at most four lines a listener from the counter. */
    public static final int LINES = 4;
    /** Within 32 blocks of a boteco or a D&T tavern. */
    public static final int RADIUS = 32;
    /** A causo of more than one chat line goes out in parts 3 s apart. */
    public static final int PART_GAP_TICKS = 60;
    /** The card weekday of Saturday ({@code land_day % 7}, 0 Monday). */
    public static final int SATURDAY = 5;

    private TrucoRules() {
    }

    /**
     * The Saturday whose reading is the latest one: today if it is Saturday and its dusk has come, else the last
     * Saturday before today. A missed reading is heard at a Seu Zé until the next Saturday's dusk.
     */
    public static long lastReading(long landDay, long timeOfDay) {
        int weekday = WeekRules.weekday(landDay);
        if (weekday == SATURDAY && timeOfDay >= WeekRules.DUSK) {
            return landDay;
        }
        int back = Math.floorMod(weekday - SATURDAY, 7);
        if (back == 0) {
            back = 7;
        }
        return landDay - back;
    }

    /** True on a Saturday. */
    public static boolean saturday(long landDay) {
        return WeekRules.weekday(landDay) == SATURDAY;
    }

    /**
     * The Gazette's order of deeds (item-diario-oficial, the headline): a boss kill, then a Coronation, then a First
     * Atlas, then the first sighting of a Court Beast, then anything else. Lower is bigger.
     */
    public static int priority(String deedKey) {
        String k = deedKey == null ? "" : deedKey.toLowerCase(Locale.ROOT);
        if (k.contains("boss") || k.contains("chefe")) {
            return 0;
        }
        if (k.contains("coronation") || k.contains("coroac")) {
            return 1;
        }
        if (k.contains("atlas")) {
            return 2;
        }
        if (k.contains("court_beast") || k.contains("fera")) {
            return 3;
        }
        return 4;
    }
}
