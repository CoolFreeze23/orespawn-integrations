package danger.orespawn.integrations.story.freguesia;

import java.util.List;

/**
 * The Freguesia's arithmetic, kept free of game types so it can be tested on its own (system-freguesia, "Stages" and
 * "Keys and counts"). Standing is kept in half points, so a Mutirão's 1.5x stays exact: a lone signer's placement is
 * 2 halves, a work party's 3, and a stone takes at most 200 points (400 halves) a game day.
 */
public final class StandingRules {

    public static final int RADIUS = 48;
    public static final int BED_RADIUS = 64;
    public static final int NEIGHBOUR_RADIUS = 256;
    public static final int DAILY_CAP = 200;
    public static final int MAX_SIGNERS = 4;
    public static final int MAX_TODAY_POSITIONS = 200;
    /** A Freguesia whose signers stay away this many game days is skipped at dawn until one logs in. */
    public static final int ABSENT_DAYS = 28;

    public static final int HOUSE = 0;
    public static final int HOMESTEAD = 1;
    public static final int HAMLET = 2;
    public static final int PARISH = 3;
    public static final int TOWN = 4;

    private static final int[] DAYS = {0, 0, 7, 14, 28};

    /** What stops the next stage. */
    public enum Missing { NONE, STANDING, DAYS, DEED, KEY, TOP }

    /** The configured numbers: four thresholds (Homestead to Town) and two builder's keys (Parish, Town). */
    public record Numbers(int[] thresholds, int[] builderKeys) {
        public static final Numbers DEFAULT = new Numbers(new int[]{60, 150, 300, 600}, new int[]{450, 900});

        public static Numbers of(List<? extends Integer> thresholds, List<? extends Integer> keys) {
            int[] t = DEFAULT.thresholds.clone();
            for (int i = 0; i < Math.min(4, thresholds.size()); i++) {
                t[i] = thresholds.get(i);
            }
            int[] k = DEFAULT.builderKeys.clone();
            for (int i = 0; i < Math.min(2, keys.size()); i++) {
                k[i] = keys.get(i);
            }
            return new Numbers(t, k);
        }
    }

    private StandingRules() {
    }

    /** The standing stage {@code stage} needs (1-4). */
    public static int threshold(int stage, Numbers n) {
        return n.thresholds()[Math.max(1, Math.min(TOWN, stage)) - 1];
    }

    /** The stone days stage {@code stage} needs. */
    public static int daysNeeded(int stage) {
        return DAYS[Math.max(0, Math.min(TOWN, stage))];
    }

    /** The builder's key for Parish (3) or Town (4); 0 for the stages that need no key. */
    public static int builderKey(int stage, Numbers n) {
        return stage == PARISH ? n.builderKeys()[0] : stage == TOWN ? n.builderKeys()[1] : 0;
    }

    /**
     * What the stone still lacks for the stage after {@code stage}: standing first, then the stone days, then the
     * Hamlet's deed, then the key (a signer's boss kill or the builder's key for Parish; a Knight signer or the
     * builder's key for Town).
     */
    public static Missing missing(int stage, int standing, int stoneDays, boolean deed, boolean bossKill,
                                  boolean knight, Numbers n) {
        int next = stage + 1;
        if (next > TOWN) {
            return Missing.TOP;
        }
        if (standing < threshold(next, n)) {
            return Missing.STANDING;
        }
        if (stoneDays < daysNeeded(next)) {
            return Missing.DAYS;
        }
        if (next == HAMLET && !deed) {
            return Missing.DEED;
        }
        if (next == PARISH && !bossKill && standing < builderKey(PARISH, n)) {
            return Missing.KEY;
        }
        if (next == TOWN && !knight && standing < builderKey(TOWN, n)) {
            return Missing.KEY;
        }
        return Missing.NONE;
    }

    /** Half points one decor placement is worth: 2 alone, 3 in a Mutirão (two or more signers in the radius). */
    public static int placementHalves(int signersInRadius) {
        return signersInRadius >= 2 ? 3 : 2;
    }

    /** How many of {@code halves} the day's cap still lets in, given {@code todayHalves} already counted. */
    public static int capped(int todayHalves, int halves) {
        return Math.max(0, Math.min(halves, DAILY_CAP * 2 - todayHalves));
    }

    /** True when a fallen stone's record still waits for a signer's new stone. */
    public static boolean withinGrace(long fallenDay, long today, int graceDays) {
        return fallenDay >= 0 && today - fallenDay <= graceDays;
    }

    /** One house in the Wednesday pick: standing gained since last Wednesday, its total, and its last two visits. */
    public record Candidate(int id, int weekGain, int total, int visits) {
        boolean visitedTwiceRunning() {
            return (visits & 3) == 3;
        }
    }

    /**
     * The week's best-kept house: the most standing gained since the last Wednesday dawn (a house that gained nothing
     * never wins), a tie to the lower total, and a house the market visited on both of the last two Wednesdays yields
     * to the next one when another qualifies. Returns the winner's id, or -1.
     */
    public static int pick(List<Candidate> candidates) {
        Candidate best = null;
        Candidate second = null;
        for (Candidate c : candidates) {
            if (c.weekGain() <= 0) {
                continue;
            }
            if (best == null || better(c, best)) {
                second = best;
                best = c;
            } else if (second == null || better(c, second)) {
                second = c;
            }
        }
        if (best == null) {
            return -1;
        }
        if (best.visitedTwiceRunning() && second != null) {
            return second.id();
        }
        return best.id();
    }

    private static boolean better(Candidate a, Candidate b) {
        return a.weekGain() != b.weekGain() ? a.weekGain() > b.weekGain() : a.total() < b.total();
    }

    /** The visit bits after a Wednesday: bit 0 this Wednesday, bit 1 the one before. */
    public static int shiftVisits(int visits, boolean visitedNow) {
        return ((visits << 1) | (visitedNow ? 1 : 0)) & 3;
    }
}
