package danger.orespawn.integrations.story.shelf9;

/**
 * Where a Hatch Register line goes: eight signs of four rows, filled in join order, line 1 on the first sign's top
 * row. From line 33 on there is no row left, so the back of the last sign carries the overflow count instead
 * ("...and {n} more"). Pure logic, unit tested.
 */
public final class RegisterLayout {

    public static final int SIGNS = 8;
    public static final int ROWS = 4;
    public static final int CAPACITY = SIGNS * ROWS;

    private RegisterLayout() {
    }

    /** The sign (0-7) for line {@code line} (1-based), or -1 for an overflow line. */
    public static int sign(int line) {
        return line >= 1 && line <= CAPACITY ? (line - 1) / ROWS : -1;
    }

    /** The row (0-3) on that sign, or -1 for an overflow line. */
    public static int row(int line) {
        return line >= 1 && line <= CAPACITY ? (line - 1) % ROWS : -1;
    }

    /** How many players the overflow line counts once {@code linesUsed} lines are given out. */
    public static int overflow(int linesUsed) {
        return Math.max(0, linesUsed - CAPACITY);
    }
}
