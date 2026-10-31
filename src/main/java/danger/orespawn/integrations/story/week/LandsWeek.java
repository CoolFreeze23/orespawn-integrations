package danger.orespawn.integrations.story.week;

import net.minecraft.server.MinecraftServer;

import java.util.function.Consumer;

/**
 * The Land's Week (0.10.1): the Land's day and the one dawn job every weekly system hooks. This first version carries
 * the two calls other systems compile against; the week itself follows.
 */
public final class LandsWeek {

    private LandsWeek() {
    }

    /** The Land's weekday, 0-6, Sunday = 0. */
    public static int today(MinecraftServer server) {
        return 0;
    }

    /** Runs {@code action} once at every dawn of the Land's day, inside the one dawn job. */
    public static void onDawn(String key, Consumer<MinecraftServer> action) {
    }
}
