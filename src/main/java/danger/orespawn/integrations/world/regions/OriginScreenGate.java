package danger.orespawn.integrations.world.regions;

import danger.orespawn.integrations.story.firsthour.FirstHourScenes;
import net.minecraft.server.level.ServerPlayer;

/**
 * Whether a player's origin screen (P00, Neo Origins) is still open: true until the origin and class layers both hold
 * one. It asks the same question as the first-hour ticket's P00, so an origin set by an operator's command (which
 * leaves Neo Origins' own "had all origins" flag unset) also lets the titles through. Only loaded behind
 * {@code ModList.isLoaded("neoorigins")}.
 */
public final class OriginScreenGate {

    private OriginScreenGate() {
    }

    public static boolean open(ServerPlayer player) {
        return !FirstHourScenes.originChosen.test(player);
    }
}
