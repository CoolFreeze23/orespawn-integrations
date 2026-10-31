package danger.orespawn.integrations.story.week;

import net.minecraft.server.MinecraftServer;

/**
 * The Almanac (0.10.1): one world event a night. This first version carries the call other systems compile against;
 * the Almanac itself follows.
 */
public final class Almanac {

    private Almanac() {
    }

    /** Claims tonight for {@code eventKey}; false when tonight already has a world event. */
    public static boolean claimNight(MinecraftServer server, String eventKey) {
        return true;
    }
}
