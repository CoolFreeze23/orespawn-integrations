package danger.orespawn.integrations.story.week;

import java.util.function.IntUnaryOperator;

/**
 * The Day's Misfile roll (system-extravio, "The Day's Misfile"), plain Java so it can be unit tested. Each dawn rolls
 * one row into {@code #extravio cc_terra}; Tuesday is always row 8 and Sunday row 9; the other days roll the rows whose
 * system has shipped (rows 2, 4, 6 and 7 come with the Shelves in 0.23.0 and row 5 with the parcels in 0.31.0, so
 * they reroll); row 10, Everything in Its Place, comes at 1 in 40 from Land's day 7, and the day after it is row 6, or
 * row 1 before 0.23.0.
 */
public final class MisfileRules {

    public static final int FISH_IN_THE_TREES = 1;
    public static final int RAINING_HATS = 3;
    public static final int NOTHING_MISFILED = 8;
    public static final int SUNDAY_GIVES_BACK = 9;
    public static final int EVERYTHING_IN_PLACE = 10;

    /** The rows of the plain days that ship in 0.10.1. */
    static final int[] SHIPPED = {FISH_IN_THE_TREES, RAINING_HATS};

    private MisfileRules() {
    }

    /**
     * The row for a Land's day.
     *
     * @param landDay     the Land's day being rolled
     * @param previousRow yesterday's row (0 if none)
     * @param nextInt     a seeded {@code bound -> [0, bound)} draw
     */
    public static int row(long landDay, int previousRow, IntUnaryOperator nextInt) {
        int weekday = WeekRules.weekday(landDay);
        if (weekday == 1) {
            return NOTHING_MISFILED;
        }
        if (weekday == 6) {
            return SUNDAY_GIVES_BACK;
        }
        if (previousRow == EVERYTHING_IN_PLACE) {
            return FISH_IN_THE_TREES;
        }
        if (landDay >= 7 && nextInt.applyAsInt(40) == 0) {
            return EVERYTHING_IN_PLACE;
        }
        return SHIPPED[nextInt.applyAsInt(SHIPPED.length)];
    }
}
