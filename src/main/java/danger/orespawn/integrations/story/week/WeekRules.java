package danger.orespawn.integrations.story.week;

/**
 * The Land's Week's timing rules as plain Java, so they can be unit tested without a server (system-lands-week,
 * event-dusk-cry, system-senha-da-primeira-hora's first week).
 *
 * <p>Two weekday numberings live side by side. The card's own holder {@code #semana cc_terra} and the weekday lang
 * keys count {@code land_day % 7}, 0 Monday to 6 Sunday ({@link #weekday}); the hand-off API other groups read,
 * {@link LandsWeek#today}, counts 0 Sunday to 6 Saturday ({@link #sundayFirst}). Day 0 of a world is a Monday in
 * both.</p>
 */
public final class WeekRules {

    public static final long DAY = 24000L;
    /** The dusk check: game time 12000 (event-dusk-cry, the Almanac's dusk). */
    public static final long DUSK = 12000L;
    /** The end of the dusk window the per-second check reads; a beat that is due but held still plays after it. */
    public static final long DUSK_END = 13800L;
    /** The world's clock slides to at least 30 s from a ticket handover (system-senha-da-primeira-hora rule 3). */
    public static final int HANDOVER_SLIDE = 30 * 20;
    /** The canopy's line waits 10 s behind a ticket handover that is still showing (system-extravio). */
    public static final int CANOPY_HOLD = 10 * 20;
    /** The first week: a player's paced explainers and Newcomer's Edition last until their eighth dawn. */
    public static final int FIRST_WEEK_DAWNS = 8;

    private WeekRules() {
    }

    /** The overworld's day index for a day time: what {@code time query day} reads. */
    public static long dayIndex(long dayTime) {
        return Math.floorDiv(dayTime, DAY);
    }

    /** The time of day, 0-23999. */
    public static long timeOfDay(long dayTime) {
        return Math.floorMod(dayTime, DAY);
    }

    /**
     * The Land's day after the check saw {@code nowIndex} where it last saw {@code lastIndex}: one more whenever the
     * index changed, in either direction, so {@code /time set day} (which rewinds the index to 0) counts as one new
     * day and never as a rewind.
     */
    public static long advance(long landDay, long lastIndex, long nowIndex) {
        return nowIndex == lastIndex ? landDay : landDay + 1;
    }

    /** The card's weekday, {@code land_day % 7}: 0 Monday ... 5 Saturday, 6 Sunday. */
    public static int weekday(long landDay) {
        return (int) Math.floorMod(landDay, 7L);
    }

    /** The hand-off API's weekday: 0 Sunday, 1 Monday ... 6 Saturday. */
    public static int sundayFirst(long landDay) {
        return (weekday(landDay) + 1) % 7;
    }

    /** True inside the dusk window the per-second check reads. */
    public static boolean inDusk(long timeOfDay) {
        return timeOfDay >= DUSK && timeOfDay < DUSK_END;
    }

    /** True once {@code onlineTicks} is at least 30 s past the ticket's last handover (or the ticket is closed). */
    public static boolean clearOfHandover(boolean ticketOpen, int onlineTicks, int lastHandover) {
        return !ticketOpen || onlineTicks - lastHandover >= HANDOVER_SLIDE;
    }

    /** True while a handover is still within the canopy's 10 s hold. */
    public static boolean canopyHeld(boolean ticketOpen, int onlineTicks, int lastHandover) {
        return ticketOpen && onlineTicks - lastHandover < CANOPY_HOLD;
    }

    /** True before the player's eighth dawn after {@code weekStart} (the join, or a veteran's first 0.10.1 login). */
    public static boolean inFirstWeek(long weekStart, long landDay) {
        return weekStart >= 0 && landDay - weekStart < FIRST_WEEK_DAWNS;
    }
}
