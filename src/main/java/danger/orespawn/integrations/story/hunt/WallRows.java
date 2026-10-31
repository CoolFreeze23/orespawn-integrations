package danger.orespawn.integrations.story.hunt;

import java.util.Map;

/**
 * The Wall of the Wanted's rules with no Minecraft classes (system-boss-ladder, "The numbers"): the trial bosses'
 * entity-tag prefixes, "your row", and the three Monday rows. The plain bosses are the entity-type tags
 * {@code #orespawn_integrations:wall/row_1} to {@code row_7}; the bosses no type names (the Apotheosis elites and
 * invaders, the Pharaoh, the Furnace Governor, the proctors) are matched in {@link WallOfTheWanted}.
 */
public final class WallRows {

    public static final int ROWS = 7;

    /**
     * The D&T trial bosses carry NBT entity tags, not entity-type tags, so the kill handler matches
     * {@code entity.getTags()} by prefix: prefix to {boss key, row}.
     */
    public static final Map<String, Proctor> PROCTORS = Map.of(
            "cave_spider_boss", new Proctor("nova_structures:toxic_lair#cave_spider_boss", 2),
            "breeze_boss", new Proctor("nova_structures:lone_citadel#breeze_boss", 3),
            "shulker_boss", new Proctor("nova_structures:end_castle#shulker_boss", 3),
            "shulker_miniboss", new Proctor("nova_structures:end_castle#shulker_boss", 3),
            "elder_guardian_boss", new Proctor("nova_structures:trident_trial_monument#elder_guardian_boss", 4),
            "phantom_boss", new Proctor("nova_structures:end_castle#phantom_boss", 5));

    public record Proctor(String key, int row) {}

    /** The bosses matched by code, each with its row. */
    public static final String PHARAOH = "betterdeserttemples:pharaoh";
    public static final String GOVERNOR = "incendium:hovering_inferno";
    public static final String INVADER = "apotheosis:invader";
    public static final String UNDEAD_KNIGHT = "apotheosis:overworld/undead_knight";
    public static final String WITHERING_ARCHER = "apotheosis:the_nether/withering_archer";
    public static final String CRAIG = "apotheosis:overworld/craig";

    public static final Map<String, Integer> CODED = Map.of(
            PHARAOH, 1, UNDEAD_KNIGHT, 1, WITHERING_ARCHER, 2, INVADER, 3, GOVERNOR, 5, CRAIG, 6);

    private WallRows() {
    }

    /** The proctor a tag names, by prefix, or null. */
    public static Proctor proctor(String tag) {
        for (Map.Entry<String, Proctor> e : PROCTORS.entrySet()) {
            if (tag.startsWith(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }

    /** "Your row": the highest row with a kill (bit {@code row - 1} of {@code killedMask}), plus one; capped at 7. */
    public static int yourRow(int killedMask) {
        int highest = 0;
        for (int row = 1; row <= ROWS; row++) {
            if ((killedMask & (1 << (row - 1))) != 0) {
                highest = row;
            }
        }
        return Math.min(ROWS, Math.max(1, highest + 1));
    }

    /** Monday's three rows for {@code row}: your row, the row below (never below 1), the row above (never above 7). */
    public static int[] mondayRows(int row) {
        return new int[]{row, Math.max(1, row - 1), Math.min(ROWS, row + 1)};
    }

    /** The pay multiplier for bounty slot 0 (your row), 1 (the row below) or 2 (the row above): 1x, 1x, 3x. */
    public static int multiplier(int slot) {
        return slot == 2 ? 3 : 1;
    }
}
