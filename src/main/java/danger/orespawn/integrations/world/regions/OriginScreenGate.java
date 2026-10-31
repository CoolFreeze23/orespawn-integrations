package danger.orespawn.integrations.world.regions;

import com.cyberday1.neoorigins.attachment.OriginAttachments;
import net.minecraft.server.level.ServerPlayer;

/**
 * Whether a player's origin screen (P00, Neo Origins) is still open: true until every layer has an origin. Only
 * loaded behind {@code ModList.isLoaded("neoorigins")}.
 */
final class OriginScreenGate {

    private OriginScreenGate() {
    }

    static boolean open(ServerPlayer player) {
        return !player.getData(OriginAttachments.originData()).isHadAllOrigins();
    }
}
