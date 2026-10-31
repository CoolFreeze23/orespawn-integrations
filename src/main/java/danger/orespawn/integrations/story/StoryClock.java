package danger.orespawn.integrations.story;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The shared 20-tick timer. Region names, the First Fall's pending-player check and the Priority Ticket's gate all
 * run here, once a second, instead of each keeping a timer of its own. Listeners register once, from their system's
 * {@code init}; each runs inside its own try, so one failing system never stops the others.
 */
public final class StoryClock {

    public static final int PERIOD = 20;

    private record Listener(String key, Consumer<MinecraftServer> action) {}

    private record PlayerListener(String key, Consumer<ServerPlayer> action) {}

    private static final List<Listener> SERVER = new ArrayList<>();
    private static final List<PlayerListener> PLAYERS = new ArrayList<>();

    private StoryClock() {
    }

    /** Runs {@code action} once every 20 ticks. */
    public static void everySecond(String key, Consumer<MinecraftServer> action) {
        SERVER.add(new Listener(key, action));
    }

    /** Runs {@code action} for each online player once every 20 ticks. Keep it O(1) per player. */
    public static void everySecondPerPlayer(String key, Consumer<ServerPlayer> action) {
        PLAYERS.add(new PlayerListener(key, action));
    }

    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % PERIOD != 0) {
            return;
        }
        for (Listener listener : SERVER) {
            try {
                listener.action().accept(server);
            } catch (Throwable t) {
                StoryCore.logOnce(listener.key(), t);
            }
        }
        if (PLAYERS.isEmpty()) {
            return;
        }
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            for (PlayerListener listener : PLAYERS) {
                try {
                    listener.action().accept(player);
                } catch (Throwable t) {
                    StoryCore.logOnce(listener.key(), t);
                }
            }
        }
    }
}
