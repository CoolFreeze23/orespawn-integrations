package danger.orespawn.integrations.story.week;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import danger.orespawn.integrations.config.IntegrationsConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /cc gazeta assinar|cancelar|correio <player>}: what the Odete's Gazette options run (dialog 711, with
 * {@code @dp} the player), and what an operator can run by hand. Each answers in the Odete's voice: the stamp, the
 * cancellation in triplicate, and the desk rack's copies for a subscriber with no mailbox. With
 * {@code [alive] gazette} off the options answer nothing.
 */
final class GazetteCommand {

    private GazetteCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> branch() {
        return Commands.literal("gazeta")
                .then(Commands.literal("assinar").then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> subscribe(ctx))))
                .then(Commands.literal("cancelar").then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> cancel(ctx))))
                .then(Commands.literal("correio").then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> post(ctx))));
    }

    private static ServerPlayer target(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return EntityArgument.getPlayer(ctx, "player");
    }

    private static int subscribe(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!IntegrationsConfig.on(IntegrationsConfig.aliveGazette)) {
            return 0;
        }
        ServerPlayer player = target(ctx);
        Gazette.subscribe(player);
        player.sendSystemMessage(Component.translatable(Gazette.KEY + "odete.subscribed"));
        return 1;
    }

    private static int cancel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!IntegrationsConfig.on(IntegrationsConfig.aliveGazette)) {
            return 0;
        }
        ServerPlayer player = target(ctx);
        Gazette.cancel(player);
        player.sendSystemMessage(Component.translatable(Gazette.KEY + "odete.cancelled"));
        return 1;
    }

    private static int post(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!IntegrationsConfig.on(IntegrationsConfig.aliveGazette)) {
            return 0;
        }
        ServerPlayer player = target(ctx);
        int copies = Gazette.collect(player);
        player.sendSystemMessage(Component.translatable(Gazette.KEY + (copies > 0 ? "odete.rack" : "odete.no_post")));
        return copies;
    }
}
