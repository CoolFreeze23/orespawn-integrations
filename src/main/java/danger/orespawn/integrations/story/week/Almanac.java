package danger.orespawn.integrations.story.week;

import danger.orespawn.integrations.config.IntegrationsConfig;
import danger.orespawn.integrations.story.StoryClock;
import danger.orespawn.integrations.story.StoryCore;
import danger.orespawn.integrations.story.firsthour.FirstHourUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * The Almanac (system-almanac): the world's one daily die and the slot rule for world events.
 *
 * <p><b>The die.</b> At each dawn, first on the Ledger's queue, one seeded roll per pick ({@link AlmanacRules#mix},
 * world seed + Land's day + a fixed salt) is written to the record ({@link AlmanacData}); a restart reads the record
 * and never re-rolls, and {@link #forDay} with tomorrow's day is Tomorrow's Paper. In 0.10.1 the picks have no
 * species to choose from yet (habits from 0.21.0, the folklore nights with their NPCs, the dream from 0.12.0, the dish
 * from 0.16.0, the needle from 0.18.0, which reads 4 until then); their slots are kept so a later slice fills them
 * without moving an earlier pick.</p>
 *
 * <p><b>The slots.</b> One world event per dimension a night, two on the server, at least 15 real minutes apart
 * ({@link #claimNight}). Thursday and Friday the overworld slot belongs to the Land's fixtures (the Mula's road, the
 * Lobisomem's fado) from before their nights ship, so those nights stay quiet and a lower place (the Hoard Fall) is
 * refused with its reason ({@link #refusal}); a higher place (a Coronation's Omens, a dated holiday) still takes it.
 * Day scenes follow the same rule, at most 3 minutes each ({@link #claimDay}).</p>
 *
 * <p><b>The sleeper's path.</b> Dusk beats ({@link #onDuskBeat}) fire at the dusk check or at a player's first bed
 * attempt that evening, whichever comes first ({@code CanPlayerSleepEvent}, read and never changed unless a scene
 * holds the beds); a night skipped by sleep runs its first-light forms at the wake-up ({@link #onFirstLight},
 * {@code PlayerWakeUpEvent} with {@code wakeImmediately} false), once per night.</p>
 */
public final class Almanac {

    static final String KEY = "orespawn_integrations.almanaque.";
    /** A dusk scene holds beds this far from it while it runs. */
    public static final int HOLD_RADIUS = 96;
    /** A day scene's longest run. */
    public static final long SCENE_TICKS = 3L * 60L * 20L;

    /** Today's picks, as the record keeps them. */
    public record Roll(long day, Map<String, String> habits, String folklore, String dish, String dream, int needle,
                       double hoardFall) {}

    private record Beat(String key, Consumer<ServerPlayer> action) {}

    private record FirstLight(String key, BiConsumer<MinecraftServer, Long> action) {}

    private record Hold(ResourceKey<Level> dim, BlockPos pos, long until, Component line) {}

    private static final List<Beat> BEATS = new ArrayList<>();
    private static final List<FirstLight> FIRST_LIGHT = new ArrayList<>();
    private static final List<Hold> HOLDS = new ArrayList<>();

    private Almanac() {
    }

    static void init() {
        LandsWeek.onDawnFirst("almanac.roll", Almanac::dawn);
        StoryClock.everySecondPerPlayer("almanac_dusk", Almanac::duskSecond);
        NeoForge.EVENT_BUS.addListener(Almanac::onBedAttempt);
        NeoForge.EVENT_BUS.addListener(Almanac::onWake);
        WeekCommands.add(root -> root.then(AlmanacCommand.branch()));
    }

    // --- the die ---

    /** A pick's own seeded stream for a Land's day: the same seed, day and salt always draw the same. */
    public static RandomSource stream(MinecraftServer server, long day, int salt) {
        return RandomSource.create(AlmanacRules.mix(server.overworld().getSeed(), day, salt));
    }

    /** The roll for any Land's day, computed from the seed: {@code forDay(today + 1)} is tomorrow's dawn roll. */
    public static Roll forDay(MinecraftServer server, long day) {
        AlmanacData d = AlmanacData.get(server);
        double fall = stream(server, day, AlmanacRules.SALT_HOARD_FALL).nextDouble();
        // Picks with nothing to choose from in 0.10.1 still draw from their own stream, so nothing shifts when a
        // later slice gives them a list.
        stream(server, day, AlmanacRules.SALT_HABITS).nextInt();
        stream(server, day, AlmanacRules.SALT_FOLKLORE).nextInt();
        return new Roll(day, Map.of(), "", "", "", d.needle, fall);
    }

    /** Today's record. */
    public static Roll today(MinecraftServer server) {
        AlmanacData d = AlmanacData.get(server);
        long day = LandsWeek.landDay(server);
        if (d.day != day) {
            dawn(server);
        }
        double fall = stream(server, day, AlmanacRules.SALT_HOARD_FALL).nextDouble();
        return new Roll(d.day, Map.copyOf(d.habits), d.folklore, d.dish, d.dream, d.needle, fall);
    }

    static void dawn(MinecraftServer server) {
        AlmanacData d = AlmanacData.get(server);
        long day = LandsWeek.landDay(server);
        if (d.day == day) {
            return;
        }
        d.day = day;
        if (IntegrationsConfig.on(IntegrationsConfig.aliveAlmanac)) {
            Roll roll = forDay(server, day);
            d.habits.clear();
            d.habits.putAll(roll.habits());
            d.folklore = roll.folklore();
            d.dish = roll.dish();
            d.dream = roll.dream();
        } else {
            d.habits.clear();
            d.folklore = "";
            d.dream = "";
        }
        d.nights.removeIf(s -> s.day() < day);
        d.days.removeIf(s -> s.day() < day);
        d.setDirty();
    }

    // --- the night slots ---

    /**
     * Asks for tonight's overworld world-event slot for {@code eventKey}. True when the slot was free (it is now the
     * caller's) or already held by the same key; false when tonight already has another world event, so the caller
     * doesn't start. Read {@link #refusal} for the line a refused event prints.
     */
    public static boolean claimNight(MinecraftServer server, String eventKey) {
        return claimNight(server, eventKey, Level.OVERWORLD);
    }

    /** The same for any dimension: one event per dimension a night, two on the server, 15 real minutes apart. */
    public static boolean claimNight(MinecraftServer server, String eventKey, ResourceKey<Level> dimension) {
        if (!IntegrationsConfig.on(IntegrationsConfig.aliveAlmanac)) {
            return true;
        }
        AlmanacData d = AlmanacData.get(server);
        long night = LandsWeek.landDay(server);
        String dim = dimension.location().toString();
        int place = AlmanacRules.place(eventKey);
        long now = server.overworld().getGameTime();
        int held = 0;
        long lastStart = Long.MIN_VALUE;
        for (AlmanacData.Slot s : d.nights) {
            if (s.day() != night) {
                continue;
            }
            if (s.dim().equals(dim)) {
                return s.event().equals(eventKey);
            }
            held++;
            lastStart = Math.max(lastStart, s.start());
        }
        if (dimension == Level.OVERWORLD) {
            String fixture = AlmanacRules.fixture(WeekRules.weekday(night));
            if (fixture != null && !fixture.equals(eventKey)
                    && !AlmanacRules.outranks(place, AlmanacRules.PLACE_LAND_FIXTURE)) {
                return false;
            }
        }
        if (!AlmanacRules.serverRoom(held, held == 0 ? Long.MAX_VALUE : now - lastStart)) {
            return false;
        }
        d.nights.add(new AlmanacData.Slot(eventKey, dim, place, night, now));
        d.setDirty();
        return true;
    }

    /**
     * Why tonight refuses {@code eventKey} in the overworld, as the Gazette and the event print it, or null when it
     * would be taken. The Hoard Fall's reasons are the Almanac's lines 9-10: "Tonight's Fall is postponed. Reason: %s."
     */
    public static Component refusal(MinecraftServer server, String eventKey) {
        long night = LandsWeek.landDay(server);
        String reason = AlmanacRules.postponeReason(WeekRules.weekday(night));
        int place = AlmanacRules.place(eventKey);
        if (reason != null && AlmanacRules.outranks(AlmanacRules.PLACE_LAND_FIXTURE, place)) {
            return postponed(reason);
        }
        AlmanacData d = AlmanacData.get(server);
        for (AlmanacData.Slot s : d.nights) {
            if (s.day() == night && s.dim().equals(Level.OVERWORLD.location().toString())
                    && !s.event().equals(eventKey)) {
                return postponed(s.place() <= AlmanacRules.PLACE_CORONATION ? "omen" : "taken");
            }
        }
        return null;
    }

    /** "Tonight's Fall is postponed. Reason: <reason>." with one of the Almanac's reasons. */
    public static Component postponed(String reason) {
        return Component.translatable(KEY + "fall_postponed", Component.translatable(KEY + "reason." + reason));
    }

    /** Who holds tonight's slot in a dimension, or null. */
    public static String tonight(MinecraftServer server, ResourceKey<Level> dimension) {
        AlmanacData d = AlmanacData.get(server);
        long night = LandsWeek.landDay(server);
        for (AlmanacData.Slot s : d.nights) {
            if (s.day() == night && s.dim().equals(dimension.location().toString())) {
                return s.event();
            }
        }
        if (dimension == Level.OVERWORLD) {
            return AlmanacRules.fixture(WeekRules.weekday(night));
        }
        return null;
    }

    /** Books tonight's overworld slot for {@code eventKey} whatever holds it (the operator's force, for tests). */
    static void force(MinecraftServer server, String eventKey) {
        AlmanacData d = AlmanacData.get(server);
        long night = LandsWeek.landDay(server);
        String dim = Level.OVERWORLD.location().toString();
        d.nights.removeIf(s -> s.day() == night && s.dim().equals(dim));
        d.nights.add(new AlmanacData.Slot(eventKey, dim, AlmanacRules.place(eventKey), night,
                server.overworld().getGameTime()));
        d.setDirty();
    }

    // --- the day slots ---

    /**
     * Asks for a day scene's slot: at most one scene a dimension at a time (3 minutes), two on the server, and 15
     * real minutes between starts in a dimension. The first hour's scenes never ask: they are scenes, not slots.
     */
    public static boolean claimDay(MinecraftServer server, String sceneKey, ResourceKey<Level> dimension) {
        if (!IntegrationsConfig.on(IntegrationsConfig.aliveAlmanac)) {
            return true;
        }
        AlmanacData d = AlmanacData.get(server);
        long day = LandsWeek.landDay(server);
        long now = server.overworld().getGameTime();
        String dim = dimension.location().toString();
        int running = 0;
        for (AlmanacData.Slot s : d.days) {
            if (s.day() != day) {
                continue;
            }
            boolean live = now - s.start() < SCENE_TICKS;
            if (s.dim().equals(dim) && now - s.start() < AlmanacRules.SPACING_TICKS) {
                return false;
            }
            if (live) {
                running++;
            }
        }
        if (running >= AlmanacRules.SERVER_SLOTS) {
            return false;
        }
        d.days.add(new AlmanacData.Slot(sceneKey, dim, AlmanacRules.place(sceneKey), day, now));
        d.setDirty();
        return true;
    }

    // --- the sleeper's path ---

    /**
     * Registers a dusk beat: it fires once per player per Land's day, at the dusk check or at that player's first
     * bed attempt of the evening, whichever comes first, and never within 30 s of a ticket handover. Beats fire in
     * registration order; the dusk cry registers first.
     */
    public static void onDuskBeat(String key, Consumer<ServerPlayer> action) {
        BEATS.add(new Beat(key, action));
    }

    /**
     * Registers a first-light form: when sleep skips a night, it runs once at the wake-up with that night's Land's
     * day, so a midnight beat a sleeper would miss plays its short dawn form.
     */
    public static void onFirstLight(String key, BiConsumer<MinecraftServer, Long> action) {
        FIRST_LIGHT.add(new FirstLight(key, action));
    }

    /**
     * Holds the beds within 96 blocks of a running dusk scene for {@code ticks}, so nobody skips the night halfway
     * through it; {@code line} is the scene's own refusal, or null for the Almanac's line 14.
     */
    public static void holdBeds(ServerLevel level, BlockPos pos, int ticks, Component line) {
        HOLDS.add(new Hold(level.dimension(), pos.immutable(), level.getGameTime() + ticks, line));
    }

    private static void duskSecond(ServerPlayer player) {
        if (player.level().dimension() != Level.OVERWORLD) {
            return;
        }
        long time = WeekRules.timeOfDay(player.level().getDayTime());
        if (!WeekRules.inDusk(time) && !pendingAfterWindow(player, time)) {
            return;
        }
        fireBeats(player);
    }

    /** Players whose dusk beats a ticket handover held this evening: they still play after the window closes. */
    private static final java.util.Set<java.util.UUID> heldThisEvening = new java.util.HashSet<>();

    private static boolean pendingAfterWindow(ServerPlayer player, long time) {
        return time >= WeekRules.DUSK_END && time < 23000L && heldThisEvening.contains(player.getUUID());
    }

    /** Fires a player's dusk beats now if they haven't this Land's day (the dusk check, the bed reader, tests). */
    public static void fireBeats(ServerPlayer player) {
        WeekState s = WeekData.of(player);
        int today = (int) LandsWeek.landDay(player.getServer());
        if (s.duskDay == today) {
            return;
        }
        if (!LandsWeek.clearOfHandover(player)) {
            heldThisEvening.add(player.getUUID());
            return;
        }
        heldThisEvening.remove(player.getUUID());
        s.duskDay = today;
        WeekData.touch(player);
        for (Beat beat : BEATS) {
            try {
                beat.action().accept(player);
            } catch (Throwable t) {
                StoryCore.logOnce("almanac.dusk." + beat.key(), t);
            }
        }
    }

    private static void onBedAttempt(CanPlayerSleepEvent event) {
        try {
            ServerPlayer player = event.getEntity();
            Level level = event.getLevel();
            if (level.dimension() == Level.OVERWORLD) {
                long time = WeekRules.timeOfDay(level.getDayTime());
                if (time >= WeekRules.DUSK || event.getProblem() == null) {
                    fireBeats(player);
                }
            }
            if (!HOLDS.isEmpty()) {
                long now = level.getGameTime();
                Iterator<Hold> it = HOLDS.iterator();
                while (it.hasNext()) {
                    Hold hold = it.next();
                    if (now >= hold.until()) {
                        it.remove();
                        continue;
                    }
                    if (hold.dim() == level.dimension()
                            && hold.pos().distSqr(event.getPos()) <= (double) HOLD_RADIUS * HOLD_RADIUS) {
                        event.setProblem(Player.BedSleepingProblem.NOT_SAFE);
                        FirstHourUtil.actionbar(player, hold.line() != null ? hold.line()
                                : Component.translatable(KEY + "bed_held"));
                        break;
                    }
                }
            }
        } catch (Throwable t) {
            StoryCore.logOnce("almanac.bed", t);
        }
    }

    private static void onWake(PlayerWakeUpEvent event) {
        try {
            if (event.wakeImmediately() || !(event.getEntity() instanceof ServerPlayer player)
                    || player.level().dimension() != Level.OVERWORLD) {
                return;
            }
            MinecraftServer server = player.getServer();
            // The skip set the day time before waking the sleepers, and the dawn check has not run yet this tick: the
            // Land's day is still the skipped night's.
            long night = LandsWeek.landDay(server);
            AlmanacData d = AlmanacData.get(server);
            if (d.firstLightNight == night) {
                return;
            }
            d.firstLightNight = night;
            d.setDirty();
            for (FirstLight fl : FIRST_LIGHT) {
                try {
                    fl.action().accept(server, night);
                } catch (Throwable t) {
                    StoryCore.logOnce("almanac.first_light." + fl.key(), t);
                }
            }
        } catch (Throwable t) {
            StoryCore.logOnce("almanac.wake", t);
        }
    }

    /** The forecast line for the Gazette: "No forecast." while the clear-weather timer runs, else null. */
    public static Component forecast(MinecraftServer server) {
        if (server.getWorldData().overworldData().getClearWeatherTime() > 0) {
            return Component.translatable(KEY + "no_forecast");
        }
        return null;
    }
}
