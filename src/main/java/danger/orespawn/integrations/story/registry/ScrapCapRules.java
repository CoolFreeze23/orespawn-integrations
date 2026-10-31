package danger.orespawn.integrations.story.registry;

/**
 * The scrap caps' arithmetic, kept free of Minecraft classes so the unit tests can run it: how many scraps a capped
 * source may still pay, and the Field Notebook's one-per-five-stamps rule. {@link ScrapCaps} applies it to players.
 */
public final class ScrapCapRules {

    public static final int KITE_CAP = 10;
    public static final int NOTEBOOK_CAP = 8;
    public static final int STAMPS_PER_SCRAP = 5;

    private ScrapCapRules() {
    }

    /** How many of {@code wanted} scraps a source may still pay, given {@code paid} so far and its {@code cap}. */
    public static int allowed(int paid, int cap, int wanted) {
        if (wanted <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(wanted, cap - Math.max(0, paid)));
    }

    /** Scraps due for a notebook hand-in of {@code stamps} stamps: one per five, under the notebook cap. */
    public static int notebookDue(int stamps, int paid) {
        return allowed(paid, NOTEBOOK_CAP, Math.max(0, stamps) / STAMPS_PER_SCRAP);
    }
}
