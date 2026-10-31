package danger.orespawn.integrations.world.sightings;

import danger.orespawn.integrations.config.IntegrationsConfig;
import danger.orespawn.integrations.story.StoryCore;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Grey JourneyMap pins for sightings, by JourneyMap's own server command (checked in its jar:
 * {@code /jm wp create <name> <dimension> <x y z> <color> <players> [announce]} and
 * {@code /jm wp delete <name> <players> [announce]}), run from the server's source with output suppressed. Only when
 * JourneyMap is loaded; at most {@code sightings.auto_pins} per player per dimension, the oldest pin deleted first.
 * Never a gold pin: gold is the ticket's errand colour.
 */
final class JourneyMapPins {

    private JourneyMapPins() {
    }

    static boolean available() {
        return ModList.get().isLoaded("journeymap") && IntegrationsConfig.get(IntegrationsConfig.sightingsAutoPins) > 0;
    }

    /** Adds a pin named {@code name} unless this player already has it; true when a pin was created. */
    static boolean add(ServerPlayer player, SightingsData data, SightingsData.Player record, String name, BlockPos pos) {
        if (!available()) {
            return false;
        }
        String dim = player.serverLevel().dimension().location().toString();
        Deque<String> ring = record.pins.computeIfAbsent(dim, k -> new ArrayDeque<>());
        if (ring.contains(name)) {
            return false;
        }
        int cap = IntegrationsConfig.get(IntegrationsConfig.sightingsAutoPins);
        while (ring.size() >= cap) {
            run(player.server, "jm wp delete " + quote(ring.pollFirst()) + " " + player.getStringUUID() + " false");
        }
        run(player.server, "jm wp create " + quote(name) + " " + dim + " " + pos.getX() + " " + pos.getY() + " "
                + pos.getZ() + " gray " + player.getStringUUID() + " false");
        ring.addLast(name);
        data.setDirty();
        return true;
    }

    private static void run(MinecraftServer server, String command) {
        try {
            server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack().withSuppressedOutput().withPermission(4), command);
        } catch (Throwable t) {
            StoryCore.logOnce("sightings_journeymap", t);
        }
    }

    /** A brigadier quoted string: backslashes and quotes escaped. */
    static String quote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
