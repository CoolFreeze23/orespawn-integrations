package danger.orespawn.integrations.story.firsthour;

/**
 * One player's Priority Ticket ({@code cc_first_hour}), saved as six ints (24 bytes): the masks of steps handed and
 * used, the step and the live gold pin, the online tick of the last handover, the ticket's own online clock, and the
 * pin's x and z. Plain Java, no Minecraft classes, so {@link TicketRules} can be tested without a server.
 *
 * <p>The online clock counts only from the origin screen's close (P00), so a player who lingers on that screen loses
 * no minute of the ticket. {@link #lastDamage} is the one field never saved: the damage hold lasts ten seconds.</p>
 */
public final class TicketState {

    public static final int P00 = 0;
    public static final int P10 = 10;
    /** The queue is closed (60 minutes, Squire, or a player from before 0.10.0 with nothing left to deliver). */
    public static final int CLOSED = 11;
    /** A player from before 0.10.0 waiting for their one catch-up Summons drop. */
    public static final int VET_LETTER = 12;
    /** ... then their Back-Dated Fall. */
    public static final int VET_FALL = 13;
    /** ... then the Field Odete at their opened crate. */
    public static final int VET_ODETE = 14;

    public static final int PIN_NONE = 0;
    public static final int PIN_FALL = 1;
    public static final int PIN_REGISTRY = 2;
    public static final int PIN_LODGE = 3;

    // Flags, in the top bits of the masks word (bits 0-10 handed, 11-21 used).
    public static final int FLAG_REGISTRY_REACHED = 1 << 22;
    public static final int FLAG_REGISTRY_OUT_OF_TURN = 1 << 23;
    public static final int FLAG_DUSK_DONE = 1 << 24;
    public static final int FLAG_CLOSE_SHOWN = 1 << 25;
    public static final int FLAG_EARLY_STAMP = 1 << 26;
    public static final int FLAG_FALL_LANDED = 1 << 27;
    public static final int FLAG_VETERAN = 1 << 28;
    public static final int FLAG_SUMMONS_OPENED = 1 << 29;
    public static final int FLAG_GUIDE_OPENED = 1 << 30;

    public int masks;
    public int step;
    public int pin;
    public int lastHandover;
    public int onlineTicks;
    public int pinX;
    public int pinZ;
    /** Online tick of the last damage taken; not saved. */
    public int lastDamage = Integer.MIN_VALUE / 2;
    /** Online tick the First Fall first came due while damage held it (a newcomer under attack); not saved. */
    public int attackHeldSince = Integer.MIN_VALUE / 2;

    public TicketState() {
    }

    /** A fresh ticket: the origin screen (P00) is on, nothing used yet. */
    public static TicketState fresh() {
        TicketState s = new TicketState();
        s.step = P00;
        s.masks = 1; // P00 is handed at join
        return s;
    }

    public static TicketState closed() {
        TicketState s = new TicketState();
        s.step = CLOSED;
        return s;
    }

    public boolean handed(int p) {
        return (masks & (1 << p)) != 0;
    }

    public boolean used(int p) {
        return (masks & (1 << (11 + p))) != 0;
    }

    public void markHanded(int p) {
        masks |= 1 << p;
    }

    public void markUsed(int p) {
        masks |= 1 << (11 + p);
    }

    public boolean flag(int f) {
        return (masks & f) != 0;
    }

    public void setFlag(int f) {
        masks |= f;
    }

    public boolean open() {
        return step <= P10;
    }

    public boolean veteranPending() {
        return step >= VET_LETTER;
    }

    public int[] toInts() {
        return new int[]{masks, (step & 0xFF) | ((pin & 0xFF) << 8), lastHandover, onlineTicks, pinX, pinZ};
    }

    public static TicketState fromInts(int[] a) {
        TicketState s = new TicketState();
        if (a == null || a.length < 6) {
            return closed();
        }
        s.masks = a[0];
        s.step = a[1] & 0xFF;
        s.pin = (a[1] >> 8) & 0xFF;
        s.lastHandover = a[2];
        s.onlineTicks = a[3];
        s.pinX = a[4];
        s.pinZ = a[5];
        return s;
    }
}
