package danger.orespawn.integrations.story.week;

/**
 * One player's week record ({@code orespawn_integrations:cc_week}), saved as eight ints (32 bytes), every day field a
 * Land's day or -1. Plain Java, so its rules can be tested without a server.
 *
 * <p>The first week's two day stamps ({@code week_day}, the day the week counts from, and {@code explainer_day}, the
 * last day an explainer played) are the fields system-senha-da-primeira-hora names on {@code cc_first_hour}; they sit
 * here, beside the week's other per-player days, so the ticket's own six-int record keeps its saved shape.</p>
 */
public final class WeekState {

    public static final int UNSET = -1;

    /** Bit 0: the "explained tomorrow" line was shown; bit 1: the dusk cry's first-heard advancement was granted. */
    public static final int FLAG_DEFERRED_SHOWN = 1;
    public static final int FLAG_FIRST_CRY = 2;
    /** Bit 2: Seu Zé's R18 line at the first Saturday reading heard. */
    public static final int FLAG_R18 = 4;

    /** The Land's day whose weekday line this player saw. */
    public int lineDay = UNSET;
    /** The Land's day whose dusk line this player heard. */
    public int duskDay = UNSET;
    /** The Land's day the first week counts from (the join, or a veteran's first login on 0.10.1). */
    public int weekStart = UNSET;
    /** The last Land's day a first-time explainer played. */
    public int explainerDay = UNSET;
    /** The Saturday (its Land's day) whose truco this player heard, live or at a Seu Zé afterwards. */
    public int heardSaturday = UNSET;
    /** The Saturday (its Land's day) whose reading this player was in range for at dusk. */
    public int presentSaturday = UNSET;
    /** The last Land's day this player was online. */
    public int lastOnline = UNSET;
    public int flags;

    public WeekState() {
    }

    public boolean flag(int f) {
        return (flags & f) != 0;
    }

    public void setFlag(int f) {
        flags |= f;
    }

    public int[] toInts() {
        return new int[]{lineDay, duskDay, weekStart, explainerDay, heardSaturday, presentSaturday, lastOnline, flags};
    }

    public static WeekState fromInts(int[] a) {
        WeekState s = new WeekState();
        if (a == null) {
            return s;
        }
        int[] v = new int[8];
        java.util.Arrays.fill(v, UNSET);
        System.arraycopy(a, 0, v, 0, Math.min(a.length, 8));
        s.lineDay = v[0];
        s.duskDay = v[1];
        s.weekStart = v[2];
        s.explainerDay = v[3];
        s.heardSaturday = v[4];
        s.presentSaturday = v[5];
        s.lastOnline = v[6];
        s.flags = a.length > 7 ? v[7] : 0;
        return s;
    }
}
