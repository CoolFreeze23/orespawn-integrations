package danger.orespawn.integrations.story;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Court rank checks. Ranks (the {@code cc_court} score) arrive in 0.10.1; until then each check reads the Apotheosis
 * progression tier that rank will be backfilled from: Squire = {@code frontier}, Knight = {@code ascent}, Fidalgo =
 * {@code summit}. An unset score is rank 0 (Hatchling), never "matches 0", which an unset score fails.
 */
public final class Ranks {

    public static final int HATCHLING = 0;
    public static final int SQUIRE = 1;
    public static final int KNIGHT = 2;
    public static final int FIDALGO = 3;

    private static final String[] TIER_ADVANCEMENTS = {
            null,
            "apotheosis:progression/frontier",
            "apotheosis:progression/ascent",
            "apotheosis:progression/summit",
    };

    private Ranks() {
    }

    /** True when the player holds at least {@code rank}; {@link #HATCHLING} is always true. */
    public static boolean atLeast(ServerPlayer player, int rank) {
        if (rank <= HATCHLING) {
            return true;
        }
        int index = Math.min(rank, TIER_ADVANCEMENTS.length - 1);
        return hasAdvancement(player, TIER_ADVANCEMENTS[index]);
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
