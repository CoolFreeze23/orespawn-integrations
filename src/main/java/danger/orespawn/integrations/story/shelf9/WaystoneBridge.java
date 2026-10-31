package danger.orespawn.integrations.story.shelf9;

import danger.orespawn.integrations.compat.waystones.WaystonesCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * The one door from Shelf 9 into the Waystones compat. Called only behind {@code ModList.isLoaded("waystones")}, so
 * the Waystones classes behind it are never loaded without the mod; only vanilla types cross it.
 */
final class WaystoneBridge {

    private WaystoneBridge() {
    }

    static UUID place(ServerLevel level, BlockPos pos) {
        return WaystonesCompat.placeShelfNine(level, pos);
    }

    static boolean activate(ServerPlayer player, UUID id) {
        return WaystonesCompat.activateFor(player, id);
    }
}
