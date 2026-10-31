package danger.orespawn.integrations.story.week;

import danger.orespawn.integrations.story.Ranks;
import danger.orespawn.integrations.story.StoryClock;
import danger.orespawn.integrations.story.StoryCore;
import danger.orespawn.integrations.story.firsthour.FirstHourUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The first week (system-senha-da-primeira-hora, OI 0.10.1): the ticket paces the first hour, and the first seven game
 * days after a player's join (for a hub veteran, after their first login on 0.10.1) keep a lighter form of it. The
 * world keeps its clock; only what is pushed to that player is paced.
 *
 * <p>One shared call for every system's first-time explainer (the dare board, the Night Log, Tale Plaques, the Fixer,
 * the Saturday auction): {@link #explain} plays it at once, at most one a Land's day; a second waits for the next dawn,
 * like a senha, with one "explained tomorrow" line the first time. The system itself works at once. The week ends at
 * the player's eighth dawn or at Squire, whichever comes first ({@link #isNewcomer}).</p>
 */
public final class FirstWeek {

    private static final String KEY = "orespawn_integrations.senha.";
    private static final Map<String, Consumer<ServerPlayer>> EXPLAINERS = new HashMap<>();

    private FirstWeek() {
    }

    static void init() {
        StoryClock.everySecondPerPlayer("first_week_explainers", FirstWeek::second);
    }

    /** Registers a system's first-time explainer under its key, once, from the system's {@code init}. */
    public static void register(String system, Consumer<ServerPlayer> explainer) {
        EXPLAINERS.put(system, explainer);
    }

    /** True for a player in their first week: before their eighth dawn and below Squire. */
    public static boolean isNewcomer(ServerPlayer player) {
        WeekState s = WeekData.of(player);
        long today = LandsWeek.landDay(player.getServer());
        return WeekRules.inFirstWeek(s.weekStart, today) && !Ranks.atLeast(player, Ranks.SQUIRE);
    }

    /**
     * A player just walked into {@code system} for the first time: plays its registered explainer now if the
     * player's day still has room, else files it for the next dawn. Returns true when it played now.
     */
    public static boolean explain(ServerPlayer player, String system) {
        Consumer<ServerPlayer> explainer = EXPLAINERS.get(system);
        if (explainer == null) {
            return false;
        }
        if (!isNewcomer(player)) {
            explainer.accept(player);
            return true;
        }
        WeekState s = WeekData.of(player);
        int today = (int) LandsWeek.landDay(player.getServer());
        if (s.explainerDay != today) {
            s.explainerDay = today;
            WeekData.touch(player);
            explainer.accept(player);
            return true;
        }
        List<String> pending = player.getData(WeekData.EXPLAINERS.get());
        if (!pending.contains(system)) {
            pending.add(system);
            player.setData(WeekData.EXPLAINERS.get(), pending);
        }
        if (!s.flag(WeekState.FLAG_DEFERRED_SHOWN)) {
            s.setFlag(WeekState.FLAG_DEFERRED_SHOWN);
            WeekData.touch(player);
            FirstHourUtil.actionbar(player, Component.translatable(KEY + "explainer_deferred"));
        }
        return false;
    }

    /** On a new Land's day, the oldest waiting explainer plays, once the ticket's handover is 30 s behind. */
    private static void second(ServerPlayer player) {
        List<String> pending = player.getData(WeekData.EXPLAINERS.get());
        if (pending.isEmpty()) {
            return;
        }
        WeekState s = WeekData.of(player);
        int today = (int) LandsWeek.landDay(player.getServer());
        if (s.explainerDay == today || !LandsWeek.clearOfHandover(player)) {
            return;
        }
        String system = pending.remove(0);
        player.setData(WeekData.EXPLAINERS.get(), pending);
        Consumer<ServerPlayer> explainer = EXPLAINERS.get(system);
        if (explainer == null) {
            return;
        }
        s.explainerDay = today;
        WeekData.touch(player);
        try {
            explainer.accept(player);
        } catch (Throwable t) {
            StoryCore.logOnce("first_week_explainer_" + system, t);
        }
    }
}
