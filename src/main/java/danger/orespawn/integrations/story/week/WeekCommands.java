package danger.orespawn.integrations.story.week;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The week's operator commands, merged into the {@code /cc} root (permission 2; Brigadier merges a second
 * {@code cc} literal into the first, and a literal child wins over the root's {@code <thing>} argument):
 * <ul>
 *   <li>{@code /cc week show}: the Land's day, the weekday and the day index; {@code /cc week dawn}: runs the next
 *   dawn now (tests);</li>
 *   <li>{@code /cc livrocaixa dawn}: the last dawn's queue length and its slowest job (system-livro-caixa-do-servidor);</li>
 *   <li>the Almanac's and the Gazette's own subcommands, added by their classes through {@link #add}.</li>
 * </ul>
 */
public final class WeekCommands {

    static final String KEY = "orespawn_integrations.week.cc.";
    private static final List<Consumer<LiteralArgumentBuilder<CommandSourceStack>>> EXTRA = new ArrayList<>();

    private WeekCommands() {
    }

    /** Lets a week system add its own branch under {@code /cc}; call from {@code init}. */
    static void add(Consumer<LiteralArgumentBuilder<CommandSourceStack>> branch) {
        EXTRA.add(branch);
    }

    static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("cc")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("week")
                        .then(Commands.literal("show").executes(ctx -> show(ctx.getSource())))
                        .then(Commands.literal("dawn").executes(ctx -> dawn(ctx.getSource()))))
                .then(Commands.literal("livrocaixa")
                        .then(Commands.literal("dawn").executes(ctx -> ledger(ctx.getSource()))));
        for (Consumer<LiteralArgumentBuilder<CommandSourceStack>> branch : EXTRA) {
            branch.accept(root);
        }
        dispatcher.register(root);
    }

    private static int show(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        long day = LandsWeek.landDay(server);
        long index = WeekRules.dayIndex(server.overworld().getDayTime());
        source.sendSuccess(() -> Component.translatable(KEY + "show", day,
                LandsWeek.weekdayLine(day), index), false);
        return (int) day;
    }

    private static int dawn(CommandSourceStack source) {
        long day = LandsWeek.forceDawn(source.getServer());
        source.sendSuccess(() -> Component.translatable(KEY + "dawn", day), true);
        return (int) day;
    }

    private static int ledger(CommandSourceStack source) {
        Object[] last = ServerLedger.lastDawn(source.getServer());
        source.sendSuccess(() -> Component.translatable(KEY + "ledger", last), false);
        return (int) last[1];
    }
}
