package danger.orespawn.integrations.story.deed;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;

/**
 * {@code /cc deeds show <player> | add <player> <key> | clear <player>} (operators, permission 2): read, add to and
 * clear one player's deed log, for testing Saturday and the Gazette. It joins the shared {@code /cc} root (Brigadier
 * merges literal nodes of the same name), so the root's own command class stays untouched.
 */
final class DeedCommand {

    private static final String KEY = "orespawn_integrations.deed.cmd.";

    private DeedCommand() {
    }

    static void init() {
        NeoForge.EVENT_BUS.addListener(DeedCommand::register);
    }

    private static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("cc").requires(s -> s.hasPermission(2))
                .then(Commands.literal("deeds")
                        .then(Commands.literal("show").then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> show(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("add").then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("key", StringArgumentType.greedyString())
                                        .executes(ctx -> add(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"),
                                                StringArgumentType.getString(ctx, "key"))))))
                        .then(Commands.literal("clear").then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> clear(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))));
    }

    private static int show(CommandSourceStack source, ServerPlayer player) {
        List<DeedLog.Deed> deeds = DeedLog.of(source.getServer(), player.getUUID());
        source.sendSuccess(() -> Component.translatable(KEY + "count", player.getGameProfile().getName(), deeds.size()), false);
        for (DeedLog.Deed d : deeds) {
            source.sendSuccess(() -> Component.translatable(KEY + "line", d.day(), d.rung(), d.text()), false);
        }
        return deeds.size();
    }

    private static int add(CommandSourceStack source, ServerPlayer player, String key) {
        DeedLog.record(player, key.trim(), player.getGameProfile().getName());
        source.sendSuccess(() -> Component.translatable(KEY + "added", player.getGameProfile().getName(), key), true);
        return 1;
    }

    private static int clear(CommandSourceStack source, ServerPlayer player) {
        int n = DeedLog.clear(source.getServer(), player.getUUID());
        source.sendSuccess(() -> Component.translatable(KEY + "cleared", player.getGameProfile().getName(), n), true);
        return n;
    }
}
