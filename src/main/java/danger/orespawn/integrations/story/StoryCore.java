package danger.orespawn.integrations.story;

import danger.orespawn.integrations.OreSpawnIntegrations;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.HashSet;
import java.util.Set;

/**
 * The road's shared plumbing (0.10.0 on): the one 20-tick clock every first-hour system rides
 * ({@link StoryClock}), a server-thread task queue ({@link StoryScheduler}) and a log-once helper. Always loaded:
 * it needs no partner mod. Each system on the road has its own class with its own {@code init(IEventBus)},
 * listed in {@code OreSpawnIntegrations.COMPAT} under the mod it needs.
 */
public final class StoryCore {

    private static final Set<String> LOGGED = new HashSet<>();

    private StoryCore() {
    }

    public static void init(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(StoryClock::onServerTick);
        NeoForge.EVENT_BUS.addListener(StoryScheduler::onServerTick);
        NeoForge.EVENT_BUS.addListener(StoryCore::onServerStopped);
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        StoryScheduler.reset();
        LOGGED.clear();
    }

    /** Logs a failure once per key and server run, so a broken hook never floods the log. */
    /** True when any system whose key starts with {@code prefix} has logged a failure. */
    public static boolean hasFailed(String prefix) {
        return LOGGED.stream().anyMatch(key -> key.startsWith(prefix));
    }

    public static void logOnce(String key, Throwable t) {
        if (LOGGED.add(key)) {
            OreSpawnIntegrations.LOGGER.error("Story system '{}' failed; it keeps running, further errors are not logged", key, t);
        }
    }
}
