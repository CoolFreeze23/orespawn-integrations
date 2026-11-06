package danger.orespawn.integrations.story.court;

import java.util.List;
import java.util.Map;

/**
 * Court ranks' rules with no Minecraft classes, so they run under JUnit: the five titles, which Apotheosis tier makes
 * which rank, the nine provinces, the one-stamp-a-day rule and the rank-up ceremony's order (system-court-ranks).
 */
public final class RankRules {

    public static final int HATCHLING = 0;
    public static final int SQUIRE = 1;
    public static final int KNIGHT = 2;
    public static final int FIDALGO = 3;
    public static final int REGENT = 4;

    /** Apotheosis's tier advancements, by the rank each one makes. */
    public static final List<String> TIERS = List.of(
            "",
            "apotheosis:progression/frontier",
            "apotheosis:progression/ascent",
            "apotheosis:progression/summit",
            "apotheosis:progression/pinnacle");

    /** The Bumblezone's last Queen's Desire advancement, a Fidalgo deed (decision H2). */
    public static final String JOURNEYS_END = "the_bumblezone:the_queens_desire/journeys_end";

    /** The nine provinces (dimension id to the path of its stamp advancement); the overworld, Chaos and the End are not. */
    public static final Map<String, String> PROVINCES = Map.of(
            "orespawn:utopia", "utopia",
            "orespawn:mining", "mining",
            "orespawn:village", "village",
            "orespawn:islands", "islands",
            "orespawn:crystal", "crystal",
            "minecraft:the_nether", "the_nether",
            "aether:the_aether", "the_aether",
            "twilightforest:twilight_forest", "twilight_forest",
            "the_bumblezone:the_bumblezone", "the_bumblezone");

    /** The seven shipped entry advancements the backfill reads, each with the province it stands for. */
    public static final Map<String, String> ENTRY_ADVANCEMENTS = Map.of(
            "orespawn_integrations:ant_farm_frontier/utopia_visit", "utopia",
            "orespawn_integrations:ant_farm_frontier/village_visit", "village",
            "orespawn_integrations:mining_dimension/mining_dim_entry", "mining",
            "orespawn_integrations:crystal_ascension/crystal_entry", "crystal",
            "orespawn_integrations:tower_challenges/islands_entry", "islands",
            "minecraft:story/enter_the_nether", "the_nether",
            "aether:enter_aether", "the_aether");

    /** What one province entry does to a player's stamp (court/carimbo; the data function is the live one). */
    public enum Stamp {
        /** No stamp yet: today is stored ("1 of 2 toward Squire"). */
        FIRST,
        /** A stamp from an earlier Land's day: two provinces, so Squire. */
        SQUIRE,
        /** A second province on the stamp's own day: its advancement is re-armed and the player waits. */
        SAME_DAY
    }

    private RankRules() {
    }

    /** The rank an Apotheosis tier advancement makes, or -1 for any other id. */
    public static int rankOfTier(String advancementId) {
        int i = TIERS.indexOf(advancementId);
        return i <= 0 ? -1 : i;
    }

    /** The province path of a dimension id, or null when that dimension is no province. */
    public static String province(String dimensionId) {
        return PROVINCES.get(dimensionId);
    }

    /**
     * One province entry, read the way court/carimbo reads it: {@code stampDay} is the stored stamp (null when none),
     * {@code today} the Land's day.
     */
    public static Stamp stamp(Integer stampDay, int today) {
        if (stampDay == null) {
            return Stamp.FIRST;
        }
        return stampDay < today ? Stamp.SQUIRE : Stamp.SAME_DAY;
    }

    /**
     * The waiting rule at login and at any province entry: a player who saw a second province on the stamp's day
     * ({@code waiting}) is Squire in any province on a later Land's day.
     */
    public static boolean waitingCompletes(boolean waiting, Integer stampDay, int today, boolean inProvince) {
        return waiting && inProvince && stampDay != null && stampDay < today;
    }

    /**
     * The ranks one rise grants, lowest first: every rank above {@code current} up to {@code target}, so a rank-0
     * player given a Fidalgo deed gets Squire, Knight and Fidalgo in that order. Empty when nothing rises.
     */
    public static int[] steps(int current, int target) {
        int from = Math.max(current, HATCHLING - 1) + 1;
        int to = Math.min(target, REGENT);
        if (to < from) {
            return new int[0];
        }
        int[] out = new int[to - from + 1];
        for (int i = 0; i < out.length; i++) {
            out[i] = from + i;
        }
        return out;
    }

    /** True when a rise skips at least one rank, so the ceremony announces "several ranks at once". */
    public static boolean skips(int current, int target) {
        return target - Math.max(current, HATCHLING) >= 2;
    }

    /** The keycard level a rank's card carries: rank + 1 (Hatchling level 1 ... Regent level 5). */
    public static int keycardLevel(int rank) {
        return Math.max(HATCHLING, Math.min(rank, REGENT)) + 1;
    }
}
