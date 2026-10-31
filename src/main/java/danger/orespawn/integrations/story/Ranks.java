package danger.orespawn.integrations.story;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.Scoreboard;

/**
 * Court rank checks, read from the {@code cc_court} score (system-court-ranks, 0.10.1): 0 Hatchling, 1 Squire,
 * 2 Knight, 3 Fidalgo, 4 Regent. Every player is backfilled at their first login on 0.10.1 from the Apotheosis tier
 * they hold; a player whose score is still unset (not logged in since) reads that same tier, Squire = {@code frontier},
 * Knight = {@code ascent}, Fidalgo = {@code summit}, Regent = {@code pinnacle}, so a check never disagrees with the
 * backfill it is waiting for.
 */
public final class Ranks {

    public static final int HATCHLING = 0;
    public static final int SQUIRE = 1;
    public static final int KNIGHT = 2;
    public static final int FIDALGO = 3;
    public static final int REGENT = 4;

    public static final String SCORE = "cc_court";

    private static final String[] TIER_ADVANCEMENTS = {
            null,
            "apotheosis:progression/frontier",
            "apotheosis:progression/ascent",
            "apotheosis:progression/summit",
            "apotheosis:progression/pinnacle",
    };

    private Ranks() {
    }

    /** True when the player holds at least {@code rank}; {@link #HATCHLING} is always true. */
    public static boolean atLeast(ServerPlayer player, int rank) {
        if (rank <= HATCHLING) {
            return true;
        }
        return of(player) >= rank;
    }

    /** The player's rank: {@code cc_court}, or the Apotheosis tier they hold while the score is unset. */
    public static int of(ServerPlayer player) {
        Scoreboard board = player.getScoreboard();
        Objective objective = board.getObjective(SCORE);
        ReadOnlyScoreInfo info = objective == null ? null : board.getPlayerScoreInfo(player, objective);
        if (info != null) {
            return Math.max(HATCHLING, info.value());
        }
        for (int r = TIER_ADVANCEMENTS.length - 1; r > HATCHLING; r--) {
            if (hasAdvancement(player, TIER_ADVANCEMENTS[r])) {
                return r;
            }
        }
        return HATCHLING;
    }

    /** True when the player has completed the advancement {@code id}; false when it doesn't exist (mod absent). */
    public static boolean hasAdvancement(ServerPlayer player, String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null || player.getServer() == null) {
            return false;
        }
        AdvancementHolder holder = player.getServer().getAdvancements().get(location);
        return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
    }
}
