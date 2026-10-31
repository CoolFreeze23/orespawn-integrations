package danger.orespawn.integrations.story.deed;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

/**
 * The deed log (0.10.1): the world's memory of what each player did, retold on Saturday, printed in the Gazette and
 * read by the court. Each deed is a lang key with its arguments, so every reader hears it in their own language.
 * This first version carries the two calls other systems compile against; the store behind them follows.
 */
public final class DeedLog {

    /**
     * One line of the log: whose deed it is, its lang key and arguments, the Land's day it happened, how far up the
     * truco ladder it has been retold (0-4), the cordel's printed bit, whether it was retold this week, and the
     * players within 32 blocks on boss and legend deeds.
     */
    public record Deed(UUID player, String playerName, String key, List<String> args, long day, int rung,
                       boolean printed, boolean retold, List<UUID> witnesses) {

        /** The line as the listener's own client will read it. */
        public Component text() {
            return Component.translatable(key, args.stream().map(DeedLog::argument).toArray());
        }
    }

    private DeedLog() {
    }

    /** Writes one deed for {@code player}; each argument is kept as text (a name, a number). */
    public static void record(ServerPlayer player, String deedKey, Object... args) {
    }

    /**
     * An argument that is itself a lang key, so a tab name or an epithet reads in the listener's language too:
     * {@code "#key"} or {@code "#key|arg|arg"}. A player name never starts with '#'.
     */
    public static String key(String key, Object... args) {
        StringBuilder out = new StringBuilder("#").append(key);
        for (Object arg : args) {
            out.append('|').append(arg);
        }
        return out.toString();
    }

    /** One stored argument as a chat component: plain text, or a lang key written by {@link #key}. */
    public static Object argument(String arg) {
        if (arg == null || !arg.startsWith("#") || arg.length() < 2) {
            return arg == null ? "" : arg;
        }
        String[] parts = arg.substring(1).split("[|]", -1);
        Object[] inner = new Object[parts.length - 1];
        System.arraycopy(parts, 1, inner, 0, inner.length);
        return Component.translatable(parts[0], inner);
    }

    /** Every player's deeds from the current Land week, oldest first. */
    public static List<Deed> week(MinecraftServer server) {
        return List.of();
    }
}
