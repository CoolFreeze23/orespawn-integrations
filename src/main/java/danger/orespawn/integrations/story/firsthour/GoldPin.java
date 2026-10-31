package danger.orespawn.integrations.story.firsthour;

import danger.orespawn.integrations.story.StoryCore;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

/**
 * The one gold errand pin a player may have: Fall, then Registry (the Lodge from 0.10.1). Each replaces the last. The
 * pin is a JourneyMap temporary waypoint made for that player alone, by the server commands
 * {@code /jm wp temp create "<name>" <dim> <x y z> gold <player>} and {@code /jm wp delete "<name>" <player>}
 * (both read from JourneyMap 6.0.9's {@code CreateWaypoint}), at most two commands a handover. The name is the pin's
 * key resolved in the player's client language; delete matches by name, so it is sent in that language and in
 * English. Without JourneyMap only the ticket remembers the pin.
 */
public final class GoldPin {

    private static final String[] KEYS = {
            null,
            "orespawn_integrations.senha.pin.fall",
            "orespawn_integrations.senha.pin.registry",
            "orespawn_integrations.senha.pin.lodge",
    };

    private GoldPin() {
    }

    /** Sets {@code kind} at x, y, z in the overworld, deleting whatever gold pin the player had. */
    public static void set(ServerPlayer player, int kind, int x, int y, int z) {
        TicketState s = FirstHourData.ticket(player);
        clear(player);
        s.pin = kind;
        s.pinX = x;
        s.pinZ = z;
        if (journeyMap()) {
            run(player.getServer(), "jm wp temp create " + quote(name(player, kind)) + " minecraft:overworld "
                    + x + " " + y + " " + z + " gold " + player.getGameProfile().getName());
        }
    }

    /** Deletes the player's live gold pin, if any. */
    public static void clear(ServerPlayer player) {
        TicketState s = FirstHourData.ticket(player);
        if (s.pin == TicketState.PIN_NONE) {
            return;
        }
        int kind = s.pin;
        s.pin = TicketState.PIN_NONE;
        if (journeyMap() && kind < KEYS.length) {
            String local = name(player, kind);
            run(player.getServer(), "jm wp delete " + quote(local) + " " + player.getGameProfile().getName());
            String english = FirstHourUtil.translateIn("en_us", KEYS[kind]);
            if (!english.equals(local)) {
                run(player.getServer(), "jm wp delete " + quote(english) + " " + player.getGameProfile().getName());
            }
        }
    }

    /** Deletes the pin only when it is {@code kind}. */
    public static void clearIf(ServerPlayer player, int kind) {
        if (FirstHourData.ticket(player).pin == kind) {
            clear(player);
        }
    }

    private static String name(ServerPlayer player, int kind) {
        return FirstHourUtil.translate(player, KEYS[Math.min(kind, KEYS.length - 1)]);
    }

    private static boolean journeyMap() {
        return ModList.get().isLoaded("journeymap");
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    static void run(MinecraftServer server, String command) {
        if (server == null) {
            return;
        }
        try {
            CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput().withPermission(4);
            server.getCommands().performPrefixedCommand(source, command);
        } catch (Throwable t) {
            StoryCore.logOnce("first_hour_command", t);
        }
    }
}
