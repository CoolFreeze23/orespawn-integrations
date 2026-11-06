package danger.orespawn.integrations.story.deed;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The deed log's rules with no Minecraft classes, so they run under JUnit (system-deed-log): the 16 KB cap with the
 * oldest lines dropped first, the truco ladder (one rung bigger each retelling, rung 4 the top, the Deed of Gift
 * climbing down, the true causo never moving), the Saturday pick, the epithet and the Gazette's headline order.
 */
public final class DeedRules {

    /** At most 16 KB of lines per player. */
    public static final int CAP_BYTES = 16 * 1024;
    /** The ladder's top rung: from there a line is retold as it stands. */
    public static final int TOP_RUNG = 4;
    /** Seu Zé's Saturday lines per listener, inside the chat budget. */
    public static final int SATURDAY_LINES = 4;
    /** Every Saturday sender's lines per player per Saturday; the rest go to Sunday's Gazette. */
    public static final int CHAT_BUDGET = 8;
    /** A boss or legend deed notes the players within this many blocks. */
    public static final int WITNESS_RADIUS = 32;
    /** At most this many witnesses on one line. */
    public static final int MAX_WITNESSES = 4;
    /** The per-day index the Gazette reads holds at most this many lines server-wide. */
    public static final int DAY_INDEX = 64;

    /** How a line climbs the ladder. */
    public enum Kind {
        /** A deed with no weapon: the swell grows ("...and it was raining", "...with one hand"). */
        PLAIN,
        /** A deed with a weapon argument: the weapon shrinks (a stick, a frying pan, a spoon, a toothpick). */
        WEAPON,
        /** The Deed of Gift: the one story the boteco tells smaller (dragon egg, duck egg, chicken egg). */
        GIFT,
        /** A dare done for real: told once, plain, never climbs. */
        TRUE
    }

    /** One line as the rules see it. */
    public static final class Entry {
        public final UUID player;
        public final String key;
        public final List<String> args;
        public final long day;
        public final Kind kind;
        public final List<UUID> witnesses;
        public int rung;
        public boolean printed;
        public boolean retold;
        /** Times retold in all (for the epithet). */
        public int tellings;

        public Entry(UUID player, String key, List<String> args, long day, Kind kind, List<UUID> witnesses) {
            this.player = player;
            this.key = key;
            this.args = List.copyOf(args);
            this.day = day;
            this.kind = kind;
            this.witnesses = List.copyOf(witnesses.subList(0, Math.min(witnesses.size(), MAX_WITNESSES)));
        }

        /** The line's stored size: the key, the arguments, day, rung and bits, and 16 B per witness. */
        public int bytes() {
            int n = 2 + key.length() + 8 + 2;
            for (String a : args) {
                n += 2 + a.length();
            }
            return n + 16 * witnesses.size();
        }
    }

    private DeedRules() {
    }

    /** Drops the oldest lines until {@code lines} fits the cap; returns how many were dropped. */
    public static int trim(List<Entry> lines) {
        int total = 0;
        for (Entry e : lines) {
            total += e.bytes();
        }
        int dropped = 0;
        while (total > CAP_BYTES && !lines.isEmpty()) {
            total -= lines.remove(0).bytes();
            dropped++;
        }
        return dropped;
    }

    /** One retelling: the line moves one rung (the gift moves too, toward the chicken egg); a true causo stays. */
    public static void retell(Entry e) {
        e.retold = true;
        e.tellings++;
        if (e.kind != Kind.TRUE && e.rung < TOP_RUNG) {
            e.rung++;
        }
    }

    /** The gift's step on its way down: 0 dragon egg, 1 duck egg, 2 chicken egg (and there it stays). */
    public static int giftStep(int rung) {
        return Math.min(rung, 2);
    }

    /**
     * Saturday's lines for one listener: their own unretold lines of this week, newest first, then their best older
     * line one rung up (the most-told one, newest on a tie), at most {@link #SATURDAY_LINES}.
     */
    public static List<Entry> saturdayPick(List<Entry> own, long weekStart) {
        List<Entry> out = new ArrayList<>();
        for (int i = own.size() - 1; i >= 0 && out.size() < SATURDAY_LINES; i--) {
            Entry e = own.get(i);
            if (e.day >= weekStart && !e.retold) {
                out.add(e);
            }
        }
        if (out.size() < SATURDAY_LINES) {
            Entry best = null;
            for (int i = own.size() - 1; i >= 0; i--) {
                Entry e = own.get(i);
                if (e.day < weekStart && e.kind != Kind.TRUE && (best == null || e.tellings > best.tellings)) {
                    best = e;
                }
            }
            if (best != null) {
                out.add(best);
            }
        }
        return out;
    }

    /** The rung of the player's most-retold weapon line, from rung 1 up; 0 (no epithet) when there is none. */
    public static int epithetRung(List<Entry> own) {
        Entry best = null;
        for (Entry e : own) {
            if (e.kind == Kind.WEAPON && e.rung >= 1 && (best == null || e.tellings >= best.tellings)) {
                best = e;
            }
        }
        return best == null ? 0 : best.rung;
    }

    /** The Gazette's order: boss kill, Coronation, First Atlas, first sighting of a Court Beast, anything else. */
    public static int priority(String key) {
        String k = key.substring(key.lastIndexOf('.') + 1);
        if (k.startsWith("boss")) {
            return 5;
        }
        if (k.startsWith("coronation")) {
            return 4;
        }
        if (k.startsWith("first_atlas")) {
            return 3;
        }
        if (k.startsWith("sighting")) {
            return 2;
        }
        return 1;
    }

    /** Yesterday's biggest line: the highest priority, the earliest written on a tie. */
    public static Entry headline(List<Entry> day) {
        return day.stream().max(Comparator.comparingInt((Entry e) -> priority(e.key))
                .thenComparing(Comparator.comparingInt(day::indexOf).reversed())).orElse(null);
    }
}
