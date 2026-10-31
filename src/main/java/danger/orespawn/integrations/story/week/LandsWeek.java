package danger.orespawn.integrations.story.week;

import danger.orespawn.integrations.config.IntegrationsConfig;
import danger.orespawn.integrations.story.StoryClock;
import danger.orespawn.integrations.story.StoryCore;
import danger.orespawn.integrations.story.firsthour.FirstHourData;
import danger.orespawn.integrations.story.firsthour.FirstHourUtil;
import danger.orespawn.integrations.story.firsthour.TicketState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The Land's Week (system-lands-week): the Land's own day count and the one dawn job every weekday card hooks.
 *
 * <p><b>The Land's day.</b> The game's day index can run backwards ({@code /time set day} sets the day time to 1000,
 * so {@code time query day} reads 0 again). So the dawn check keeps its own counter, {@code land_day}, in SavedData:
 * it starts at the world's day index the first time OI sees the world (the hub's week goes on where it is), and it
 * rises by exactly one each time the check sees the index change, in either direction. A {@code /time set} is one more
 * day, never a rewind; operators use {@code /time add}. It is mirrored to {@code #dia cc_terra} (the day count) and
 * {@code #semana cc_terra} ({@code land_day % 7}, 0 Monday to 6 Sunday) for functions and loot conditions.</p>
 *
 * <p><b>The dawn job</b>, read on the shared 20-tick clock, never per tick, in this order: the Land's day and its
 * two holders; the weekday line on the actionbar (a player who logs in later that day sees it once, and a ticket
 * handover pushes it to at least 30 s after); then, on the Server's Ledger's queue one job a tick, the Almanac's
 * roll, the Misfile's three lines (the canopy caps, the Day's Misfile, the van), every card's weekday hook in
 * registration order, and the Gazette last, so it prints what the hooks decided.</p>
 *
 * <p>With {@code [story] lands_week} off there are no weekday lines and no weekday hooks, but the Land's day still
 * counts: it is the day number every other card reads.</p>
 */
public final class LandsWeek {

    /** The hand-off API's weekday numbering (Sunday = 0); see {@link WeekRules}. */
    public static final int SUNDAY = 0;
    public static final int MONDAY = 1;
    public static final int TUESDAY = 2;
    public static final int WEDNESDAY = 3;
    public static final int THURSDAY = 4;
    public static final int FRIDAY = 5;
    public static final int SATURDAY = 6;

    public static final String OBJECTIVE = "cc_terra";
    public static final String HOLDER_DAY = "#dia";
    public static final String HOLDER_WEEKDAY = "#semana";
    public static final String HOLDER_MISFILE = "#extravio";
    /** Weekday line keys, {@code week.orespawn_integrations.<0-6>}, 0 Monday (the card's ids). */
    public static final String LINE_KEY = "week.orespawn_integrations.";

    private record Hook(String key, Consumer<MinecraftServer> job) {}

    private static final List<Hook> FIRST = new ArrayList<>();
    private static final List<Hook> HOOKS = new ArrayList<>();
    private static final List<Hook> LAST = new ArrayList<>();

    private LandsWeek() {
    }

    static void init() {
        StoryClock.everySecond("lands_week", LandsWeek::check);
        StoryClock.everySecondPerPlayer("lands_week_line", LandsWeek::playerSecond);
        NeoForge.EVENT_BUS.addListener(LandsWeek::onLogin);
    }

    // --- the API ---

    /** Today's weekday, 0-6 with Sunday = 0. Day 0 of a world is a Monday. */
    public static int today(MinecraftServer server) {
        return WeekRules.sundayFirst(landDay(server));
    }

    /**
     * Registers {@code job} to run once at each overworld dawn, after the Land's day has moved on and the Almanac has
     * rolled, in registration order, one job a tick on the Server's Ledger. Register once, from a system's
     * {@code init}. Weekday hooks are skipped while {@code [story] lands_week} is off.
     */
    public static void onDawn(String key, Consumer<MinecraftServer> job) {
        HOOKS.add(new Hook(key, job));
    }

    /** The Land's day: the day number every card reads (the averbação, the caps, the issue number). */
    public static long landDay(MinecraftServer server) {
        LandsWeekData data = LandsWeekData.get(server);
        if (!data.started) {
            start(server, data);
        }
        return data.landDay;
    }

    /** The card's weekday, {@code land_day % 7}: 0 Monday to 6 Sunday. */
    public static int weekday(MinecraftServer server) {
        return WeekRules.weekday(landDay(server));
    }

    /** The week's own dawn steps that must come before every hook (the Almanac's roll). */
    static void onDawnFirst(String key, Consumer<MinecraftServer> job) {
        FIRST.add(new Hook(key, job));
    }

    /** The week's own dawn steps that come after every hook (the Gazette). */
    static void onDawnLast(String key, Consumer<MinecraftServer> job) {
        LAST.add(new Hook(key, job));
    }

    // --- the dawn check ---

    private static void start(MinecraftServer server, LandsWeekData data) {
        long index = WeekRules.dayIndex(server.overworld().getDayTime());
        data.started = true;
        data.landDay = index;
        data.lastIndex = index;
        data.setDirty();
        writeHolders(server, data.landDay);
    }

    static void check(MinecraftServer server) {
        LandsWeekData data = LandsWeekData.get(server);
        if (!data.started) {
            start(server, data);
            return;
        }
        long index = WeekRules.dayIndex(server.overworld().getDayTime());
        if (index == data.lastIndex) {
            return;
        }
        data.landDay = WeekRules.advance(data.landDay, data.lastIndex, index);
        data.lastIndex = index;
        data.setDirty();
        dawn(server, data.landDay);
    }

    /** Runs one dawn: the holders now, the rest on the Ledger's queue. Public for the operator's test command. */
    public static void dawn(MinecraftServer server, long landDay) {
        writeHolders(server, landDay);
        ServerLedger.beginDawn(landDay);
        for (Hook hook : FIRST) {
            ServerLedger.enqueue(hook.key(), () -> hook.job().accept(server));
        }
        Misfile.queueDawn(server, landDay);
        if (IntegrationsConfig.on(IntegrationsConfig.storyLandsWeek)) {
            for (Hook hook : HOOKS) {
                ServerLedger.enqueue(hook.key(), () -> hook.job().accept(server));
            }
        }
        for (Hook hook : LAST) {
            ServerLedger.enqueue(hook.key(), () -> hook.job().accept(server));
        }
        ServerLedger.dawnQueued();
    }

    /** Forces the next dawn now (operators and tests): the same as the day index moving on by one. */
    public static long forceDawn(MinecraftServer server) {
        LandsWeekData data = LandsWeekData.get(server);
        if (!data.started) {
            start(server, data);
        }
        data.landDay++;
        data.setDirty();
        dawn(server, data.landDay);
        return data.landDay;
    }

    private static void writeHolders(MinecraftServer server, long landDay) {
        Scoreboard board = server.getScoreboard();
        Objective objective = FirstHourUtil.objective(board, OBJECTIVE);
        board.getOrCreatePlayerScore(ScoreHolder.forNameOnly(HOLDER_DAY), objective).set((int) landDay);
        board.getOrCreatePlayerScore(ScoreHolder.forNameOnly(HOLDER_WEEKDAY), objective).set(WeekRules.weekday(landDay));
    }

    // --- the weekday line ---

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        try {
            if (event.getEntity() instanceof ServerPlayer player) {
                WeekState s = WeekData.of(player);
                int today = (int) landDay(player.getServer());
                if (s.weekStart == WeekState.UNSET) {
                    // The join, or a hub veteran's first login on 0.10.1: their first week counts from today.
                    s.weekStart = today;
                }
                s.lastOnline = today;
                WeekData.touch(player);
            }
        } catch (Throwable t) {
            StoryCore.logOnce("lands_week_login", t);
        }
    }

    private static void playerSecond(ServerPlayer player) {
        WeekState s = WeekData.of(player);
        int today = (int) landDay(player.getServer());
        if (s.lastOnline != today) {
            s.lastOnline = today;
            WeekData.touch(player);
        }
        if (s.lineDay == today || !IntegrationsConfig.on(IntegrationsConfig.storyLandsWeek)
                || player.level().dimension() != Level.OVERWORLD || !clearOfHandover(player)) {
            return;
        }
        s.lineDay = today;
        WeekData.touch(player);
        FirstHourUtil.actionbar(player, weekdayLine(today));
        // Tab 3's root opens with the first day name a player sees (system-advancement-tree).
        FirstHourUtil.award(player, "week/root");
    }

    /** The sunrise line for a Land's day: "Wednesday. Market day." */
    public static Component weekdayLine(long landDay) {
        return Component.translatable(LINE_KEY + WeekRules.weekday(landDay));
    }

    /** True when the world's clock may speak to this player: no ticket handover in the last 30 s. */
    public static boolean clearOfHandover(ServerPlayer player) {
        if (!FirstHourData.hasTicket(player)) {
            return true;
        }
        TicketState t = FirstHourData.ticket(player);
        return WeekRules.clearOfHandover(t.open(), t.onlineTicks, t.lastHandover);
    }

    /** The overworld, for readers that need the Land's level. */
    static ServerLevel overworld(MinecraftServer server) {
        return server.overworld();
    }
}
