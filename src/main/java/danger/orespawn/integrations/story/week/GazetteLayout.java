package danger.orespawn.integrations.story.week;

import java.util.ArrayList;
import java.util.List;

/**
 * The Gazette's page budget (item-diario-oficial, "The pages" and "The overflow rule"), plain Java so it can be unit
 * tested. A page is at most 120 words in either language; slots are filled in their order (masthead, headline, the
 * once-only block, the Misfile box, ...) and a slot that does not fit is never cut: it waits for the next dawn, where
 * it prints under "Held over from yesterday", and a line that has waited three dawns is dropped (it was already on its
 * own card's surface).
 */
public final class GazetteLayout {

    public static final int PAGE_WORDS = 120;
    public static final int MAX_WAITS = 3;
    /** A page's JSON never passes the book format's limit. */
    public static final int PAGE_JSON_LIMIT = 32767;

    /** One slot: its words (the larger of the two languages), whether it must print, and its payload. */
    public record Slot<T>(int words, boolean mandatory, T payload) {}

    /** What fit on a page and what waits. */
    public record Fill<T>(List<T> kept, List<T> held, int words) {}

    private GazetteLayout() {
    }

    /** Fills one page in slot order: mandatory slots always print; the rest print while the budget lasts. */
    public static <T> Fill<T> fill(List<Slot<T>> slots, int budget) {
        int used = 0;
        for (Slot<T> s : slots) {
            if (s.mandatory()) {
                used += s.words();
            }
        }
        List<T> kept = new ArrayList<>();
        List<T> held = new ArrayList<>();
        for (Slot<T> s : slots) {
            if (s.mandatory()) {
                kept.add(s.payload());
            } else if (used + s.words() <= budget) {
                used += s.words();
                kept.add(s.payload());
            } else {
                held.add(s.payload());
            }
        }
        return new Fill<>(kept, held, used);
    }

    /** Words as a reader counts them: runs of non-space characters. */
    public static int words(String text) {
        String t = text == null ? "" : text.trim();
        return t.isEmpty() ? 0 : t.split("\\s+").length;
    }

    /** The masthead's year: 77 plus the Serene years since world day 0 (a Serene year is 96 days by default). */
    public static int year(long landDay) {
        return 77 + (int) Math.floorDiv(Math.max(0, landDay), 96L);
    }
}
