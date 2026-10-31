package danger.orespawn.integrations.story.firsthour;

/**
 * The Priority Ticket's rules, in one place and with no Minecraft classes, so they run under JUnit.
 *
 * <p>For a player's first hour online the world hands over one paper or one verb at a time (P01-P10). The next one
 * comes when the last is used, or after 3 minutes, whichever is first; never within 90 s of the previous handover,
 * within 10 s of damage, or during a fall of more than 3 blocks. The 90 s gap wins every collision. The queue closes
 * at 60 minutes online or at Squire. {@link #decide} names the step to hand over now (or none, or the close); the
 * caller does the handing and then calls {@link #recordHandover}. A step that cannot run (its system is off, or its
 * verb was already done before its turn) is passed silently, with no number and no gap.</p>
 *
 * <p>Steps, with what calls them:</p>
 * <ul>
 *   <li>P00 the origin screen; its close hands P01 at once and starts the ticket's clock.</li>
 *   <li>P01 the Eggshell (already in the inventory): used when opened.</li>
 *   <li>P02 the kit's casket. P03 the field Correction, only while a scrap is held (else it waits, and the rest of
 *   the queue goes on: the First Fall's crate brings a scrap).</li>
 *   <li>P04 the Letter: at least 120 s online, and forced at 300 s, at the first moment the gap allows.</li>
 *   <li>P05 the First Fall (held while it finds no spot: nothing later is issued). P06 the Field Odete, once the
 *   crate is opened (or 3 minutes after the landing, when she walks to the player instead).</li>
 *   <li>P07 the larder egg. P08 the Guide's p.4, early on an anthill, butterfly or henge stamp.</li>
 *   <li>P09 the Registry, when reached (out of turn if earlier steps are still waiting), else at minute 40.</li>
 *   <li>P10 the Worn Notebook, else at minute 45.</li>
 * </ul>
 */
public final class TicketRules {

    public static final int NONE = -1;
    public static final int CLOSE = -2;

    public static final int GAP = 90 * 20;
    public static final int DAMAGE_HOLD = 10 * 20;
    /** A newcomer under attack: the damage hold keeps their own First Fall back for at most this long. */
    public static final int ATTACK_HOLD_MAX = 60 * 20;
    public static final int FALLBACK = 180 * 20;
    public static final int LETTER_MIN = 120 * 20;
    public static final int LETTER_FORCE = 300 * 20;
    public static final int P09_FALLBACK = 40 * 60 * 20;
    public static final int P10_FALLBACK = 45 * 60 * 20;
    public static final int CLOSE_AT = 60 * 60 * 20;

    /** What the world says about the player this second. */
    public record Conditions(boolean ticketOn, boolean originChosen, boolean falling, boolean scrapHeld,
                             boolean letterOn, boolean fallOn, boolean squire, boolean daylight,
                             boolean notebookHeld) {
    }

    private TicketRules() {
    }

    /** Advances the ticket's clock by one pass of the 20-tick timer. */
    public static void tick(TicketState s, int ticks) {
        if (s.used(TicketState.P00) || s.veteranPending()) {
            s.onlineTicks += ticks;
        }
    }

    /**
     * A newcomer under attack (event-first-fall): when damage alone holds a First Fall that is otherwise due, the hold
     * lasts at most {@link #ATTACK_HOLD_MAX}; then P05 goes ahead, and the Fall lands in the ring away from the attacker.
     */
    static boolean fallUnderAttack(TicketState s, Conditions c) {
        boolean damageOnly = s.onlineTicks - s.lastHandover >= GAP && !c.falling()
                && s.onlineTicks - s.lastDamage < DAMAGE_HOLD;
        // P03, the field Correction, may wait behind the Fall here, as it already does for a player with no scrap.
        boolean fallDue = c.fallOn() && s.handed(2) && s.handed(4) && !s.handed(5)
                && (s.used(s.step) || s.onlineTicks - s.lastHandover >= FALLBACK);
        if (!damageOnly || !fallDue) {
            return false;
        }
        if (s.attackHeldSince < 0) {
            s.attackHeldSince = s.onlineTicks;
        }
        return s.onlineTicks - s.attackHeldSince >= ATTACK_HOLD_MAX;
    }

    /** True while a handover may not happen: inside the 90 s gap, 10 s after damage, or falling. */
    public static boolean blocked(TicketState s, Conditions c) {
        return s.onlineTicks - s.lastHandover < GAP
                || s.onlineTicks - s.lastDamage < DAMAGE_HOLD
                || c.falling();
    }

    /** The step to hand over now, {@link #NONE}, or {@link #CLOSE}. May silently pass steps that cannot run. */
    public static int decide(TicketState s, Conditions c) {
        if (s.step == TicketState.CLOSED) {
            return NONE;
        }
        if (s.veteranPending()) {
            return decideVeteran(s, c);
        }
        if (!s.used(TicketState.P00)) {
            return c.originChosen() ? 1 : NONE;
        }
        if (s.onlineTicks >= CLOSE_AT || c.squire()) {
            return CLOSE;
        }
        if (!c.ticketOn()) {
            return decideOff(s, c);
        }
        if (blocked(s, c)) {
            return fallUnderAttack(s, c) ? 5 : NONE;
        }
        s.attackHeldSince = Integer.MIN_VALUE / 2;
        if (s.flag(TicketState.FLAG_REGISTRY_REACHED) && !s.handed(9)) {
            return 9;
        }
        if (c.letterOn() && !s.handed(4) && s.onlineTicks >= LETTER_FORCE) {
            return 4;
        }
        boolean rule = s.used(s.step) || s.onlineTicks - s.lastHandover >= FALLBACK;
        for (int p = 2; p <= TicketState.P10; p++) {
            if (s.handed(p)) {
                continue;
            }
            if (passes(s, c, p)) {
                s.markHanded(p);
                continue;
            }
            switch (p) {
                case 2, 7:
                    return rule ? p : NONE;
                case 3:
                    if (!c.scrapHeld()) {
                        continue; // waits for a scrap; the rest of the queue goes on
                    }
                    return rule ? p : NONE;
                case 4:
                    return rule && s.onlineTicks >= LETTER_MIN ? p : NONE;
                case 5:
                    return rule ? p : NONE; // a Fall that finds no spot is held by its caller
                case 6:
                    if (!s.flag(TicketState.FLAG_FALL_LANDED)) {
                        return NONE;
                    }
                    return s.used(5) || s.onlineTicks - s.lastHandover >= FALLBACK ? p : NONE;
                case 8:
                    return rule || s.flag(TicketState.FLAG_EARLY_STAMP) ? p : NONE;
                case 9:
                    return s.onlineTicks >= P09_FALLBACK ? p : NONE;
                case 10:
                    return rule || s.onlineTicks >= P10_FALLBACK ? p : NONE;
                default:
                    return NONE;
            }
        }
        return NONE;
    }

    /** A step that is passed with no handover: its system is off, or its verb was done before its turn. */
    private static boolean passes(TicketState s, Conditions c, int p) {
        return switch (p) {
            case 2, 3, 7 -> s.used(p);
            case 4 -> !c.letterOn();
            case 5 -> !c.fallOn();
            case 6 -> !c.fallOn() || s.used(6);
            case 10 -> c.notebookHeld();
            default -> false;
        };
    }

    /** With the ticket off: no numbers and no gaps; the Letter and the Fall on their old timers. */
    private static int decideOff(TicketState s, Conditions c) {
        if (c.letterOn() && !s.handed(4)) {
            return s.onlineTicks >= LETTER_MIN ? 4 : NONE;
        }
        if (c.fallOn() && !s.handed(5)) {
            return s.onlineTicks - s.lastHandover >= FALLBACK ? 5 : NONE;
        }
        if (c.fallOn() && !s.handed(6) && s.flag(TicketState.FLAG_FALL_LANDED) && s.used(5)) {
            return 6;
        }
        return NONE;
    }

    private static int decideVeteran(TicketState s, Conditions c) {
        if (s.step == TicketState.VET_LETTER && !c.letterOn()) {
            s.step = TicketState.VET_FALL;
        }
        if (s.step == TicketState.VET_FALL && !c.fallOn()) {
            s.step = TicketState.CLOSED;
            return NONE;
        }
        if (blocked(s, c)) {
            return NONE;
        }
        return switch (s.step) {
            case TicketState.VET_LETTER -> TicketState.VET_LETTER;
            case TicketState.VET_FALL -> c.daylight() && (s.flag(TicketState.FLAG_SUMMONS_OPENED)
                    || s.onlineTicks - s.lastHandover >= FALLBACK) ? TicketState.VET_FALL : NONE;
            case TicketState.VET_ODETE -> s.used(5) ? TicketState.VET_ODETE : NONE;
            default -> NONE;
        };
    }

    /** Records a handover that happened: P01-P10, or a veteran's step (which moves them on). */
    public static void recordHandover(TicketState s, int step) {
        s.lastHandover = s.onlineTicks;
        if (step == 1 && !s.used(TicketState.P00)) {
            s.markUsed(TicketState.P00);
            s.onlineTicks = 0;
            s.lastHandover = 0;
        }
        if (step >= 1 && step <= TicketState.P10) {
            if (step == 9 && anyEarlierWaiting(s)) {
                s.setFlag(TicketState.FLAG_REGISTRY_OUT_OF_TURN);
            }
            s.markHanded(step);
            s.step = step;
            return;
        }
        s.step = switch (step) {
            case TicketState.VET_LETTER -> TicketState.VET_FALL;
            case TicketState.VET_FALL -> TicketState.VET_ODETE;
            default -> TicketState.CLOSED;
        };
    }

    private static boolean anyEarlierWaiting(TicketState s) {
        for (int p = 2; p < 9; p++) {
            if (!s.handed(p)) {
                return true;
            }
        }
        return false;
    }

    /** Marks a step used. Papers count only once handed; verbs (P02, P03, P06, P07) may be done early. */
    public static void markUsed(TicketState s, int step) {
        boolean verb = step == 2 || step == 3 || step == 5 || step == 6 || step == 7;
        if (verb || s.handed(step)) {
            s.markUsed(step);
        }
    }

    public static void close(TicketState s) {
        s.step = TicketState.CLOSED;
    }
}
