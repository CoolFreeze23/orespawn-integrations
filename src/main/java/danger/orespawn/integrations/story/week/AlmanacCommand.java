package danger.orespawn.integrations.story.week;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

/**
 * {@code /cc almanac show|tomorrow|force <event>} (system-almanac's operator command, for tests): today's record,
 * tomorrow's roll (which equals the next dawn's), and tonight's overworld slot booked for an event whatever holds it.
 */
final class AlmanacCommand {

    private static final String KEY = WeekCommands.KEY + "almanac.";

    private AlmanacCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> branch() {
        return Commands.literal("almanac")
                .then(Commands.literal("show").executes(ctx -> show(ctx.getSource(), false)))
                .then(Commands.literal("tomorrow").executes(ctx -> show(ctx.getSource(), true)))
                .then(Commands.literal("force")
                        .then(Commands.argument("event", StringArgumentType.word())
                                .executes(ctx -> force(ctx.getSource(), StringArgumentType.getString(ctx, "event")))));
    }

    private static int show(CommandSourceStack source, boolean tomorrow) {
        MinecraftServer server = source.getServer();
        Almanac.Roll roll = tomorrow ? Almanac.forDay(server, LandsWeek.landDay(server) + 1) : Almanac.today(server);
        String slot = tomorrow ? "-" : String.valueOf(Almanac.tonight(server, Level.OVERWORLD));
        source.sendSuccess(() -> Component.translatable(KEY + "show", roll.day(),
                dash(roll.folklore()), dash(roll.dish()), dash(roll.dream()), roll.needle(),
                String.format(java.util.Locale.ROOT, "%.4f", roll.hoardFall()), slot), false);
        return (int) roll.day();
    }

    private static String dash(String s) {
        return s == null || s.isEmpty() ? "-" : s;
    }

    private static int force(CommandSourceStack source, String event) {
        Almanac.force(source.getServer(), event);
        source.sendSuccess(() -> Component.translatable(KEY + "forced", event), true);
        return 1;
    }
}
