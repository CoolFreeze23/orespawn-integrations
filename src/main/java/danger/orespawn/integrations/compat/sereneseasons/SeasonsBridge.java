package danger.orespawn.integrations.compat.sereneseasons;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import danger.orespawn.integrations.OreSpawnIntegrations;
import danger.orespawn.integrations.compat.alive.PrinceFlyover;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import sereneseasons.api.season.Season;
import sereneseasons.api.season.SeasonChangedEvent;
import sereneseasons.api.season.SeasonHelper;

/**
 * Serene Seasons bridge: turns the addon's celebrations from real-world clock
 * flavor into in-game season flavor.
 *
 * <p>Two exports:</p>
 * <ul>
 *   <li>{@link #paletteFor(Level)} - the firework star palette for the level's
 *       current season, or {@code null} when Serene Seasons is absent, broken,
 *       or the dimension has no season cycle. Callers (CelebrationHandler)
 *       must treat null as "use your own fallback palette". Safe to call
 *       unconditionally: this outer class never links a Serene Seasons type
 *       (all partner-API touches live in the nested {@link Hooks} and
 *       {@link SeasonChangeListener} classes, which are only classloaded after
 *       {@link #init(IEventBus)} succeeded behind the ModList gate).</li>
 *   <li>A {@code SeasonChangedEvent.Standard} listener that fires a one-off
 *       Prince flyover over the overworld when a full season (not merely a
 *       sub-season) turns - the Royal Court announcing the new season.</li>
 * </ul>
 *
 * <p>POLICY 4 - verified partner surfaces (all javap-verified against the
 * exact pack jars, copied into this project's {@code libs/}):</p>
 * <ul>
 *   <li>{@code sereneseasons.api.season.SeasonHelper.getSeasonState(Level)}
 *       returning {@code ISeasonState} with {@code getSeason()} ->
 *       {@code Season} enum (SPRING/SUMMER/AUTUMN/WINTER) -
 *       SereneSeasons-neoforge-1.21.1-10.1.0.3.jar.</li>
 *   <li>{@code sereneseasons.api.season.SeasonChangedEvent$Standard} (extends
 *       {@code SeasonChangedEvent<Season.SubSeason>} extends
 *       {@code glitchcore.event.Event}; exposes getLevel/getPrevSeason/
 *       getNewSeason) - same jar. Fired by
 *       {@code sereneseasons.season.SeasonHandler.sendSeasonUpdate} whenever
 *       the sub-season rolls over (bytecode-verified).</li>
 *   <li>{@code sereneseasons.init.ModConfig.seasons} (public static) and
 *       {@code sereneseasons.config.SeasonsConfig.isDimensionWhitelisted(
 *       ResourceKey<Level>)} - same jar. Internal-but-public config surface;
 *       guarded by try/catch so a future rename only mutes this bridge.</li>
 *   <li>{@code glitchcore.event.EventManager.addListener(Consumer<T>)} /
 *       {@code fire(T)} - GlitchCore-neoforge-1.21.1-2.1.0.2.jar. addListener
 *       resolves the event class via net.jodah.typetools TypeResolver, which
 *       cannot see through lambdas or method references on modern JVMs -
 *       therefore the listener below is a NAMED class implementing
 *       {@code Consumer<SeasonChangedEvent.Standard>} (generic supertype info
 *       survives in class metadata). {@code fire} dispatches on the event's
 *       exact runtime class, which is {@code SeasonChangedEvent$Standard}.</li>
 * </ul>
 *
 * <p>House defensive style (copied from compat/alive/AliveWorldCompat): every
 * partner-facing body is try/catch with a log-once mute; a broken Serene
 * Seasons surface flips {@link #active} off and the addon silently falls back
 * to its real-world-calendar behavior.</p>
 */
public final class SeasonsBridge {

    /**
     * True once {@link #init(IEventBus)} hooked Serene Seasons successfully;
     * flipped back to false forever if any partner call throws. Readable by
     * other modules as the "seasonal flavor available" flag.
     */
    public static volatile boolean active;

    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();

    // Firework star palettes (RGB), one per season - same color language as
    // CelebrationHandler's GOLD/RED defaults.
    private static final IntList SPRING_PALETTE = IntList.of(0xF7A8C4, 0x6FD46F); // blossom pink + fresh green
    private static final IntList SUMMER_PALETTE = IntList.of(0xF0C334, 0x46A8E0); // gold + clear sky
    private static final IntList AUTUMN_PALETTE = IntList.of(0xE07A2E, 0xB3341E); // harvest orange + maple red
    private static final IntList WINTER_PALETTE = IntList.of(0xF5F5F5, 0x9AD9E8); // snow white + ice blue

    private SeasonsBridge() {
    }

    /**
     * Invoked reflectively by OreSpawnIntegrations only when the
     * "sereneseasons" mod is present (COMPAT table gate).
     */
    public static void init(IEventBus modBus) {
        try {
            Hooks.register();
            active = true;
            OreSpawnIntegrations.LOGGER.info(
                    "[sereneseasons] bridge active - seasonal palettes + season-change flyovers");
        } catch (Throwable t) {
            active = false;
            logOnce("init", t);
        }
    }

    /**
     * Seasonal firework palette for {@code level}'s current season, or
     * {@code null} when unavailable (Serene Seasons absent/broken, or the
     * dimension is not season-whitelisted - the Mining/Chaos/Utopia policy
     * dims deliberately have no season cycle). Null means: use your fallback.
     */
    public static IntList paletteFor(Level level) {
        if (!active || level == null) {
            return null;
        }
        try {
            return Hooks.palette(level);
        } catch (Throwable t) {
            active = false;
            logOnce("palette", t);
            return null;
        }
    }

    private static void logOnce(String key, Throwable t) {
        if (LOGGED.add(key)) {
            OreSpawnIntegrations.LOGGER.error(
                    "[sereneseasons] '{}' failed; muting further reports and disabling seasonal flavor",
                    key, t);
        }
    }

    private static void logMissingOnce(String what) {
        if (LOGGED.add("missing:" + what)) {
            OreSpawnIntegrations.LOGGER.warn(
                    "[sereneseasons] '{}' is not loaded; the feature that needs it stays inert", what);
        }
    }

    // =====================================================================
    // Everything below touches Serene Seasons / GlitchCore classes and is
    // kept in nested classes so the outer class never links partner types.
    // =====================================================================

    /** Partner-API touchpoints; classloaded only from init()/paletteFor(). */
    private static final class Hooks {

        private Hooks() {
        }

        static void register() {
            glitchcore.event.EventManager.addListener(new SeasonChangeListener());
        }

        static IntList palette(Level level) {
            if (!sereneseasons.init.ModConfig.seasons.isDimensionWhitelisted(level.dimension())) {
                return null; // no season cycle here (e.g. orespawn:mining / chaos / utopia)
            }
            Season season = SeasonHelper.getSeasonState(level).getSeason();
            return switch (season) {
                case SPRING -> SPRING_PALETTE;
                case SUMMER -> SUMMER_PALETTE;
                case AUTUMN -> AUTUMN_PALETTE;
                case WINTER -> WINTER_PALETTE;
            };
        }
    }

    /**
     * Named (non-lambda) listener class - required so GlitchCore's
     * TypeResolver can read the event type from the generic interface.
     * Fires a single Prince flyover over the overworld when the season turns.
     */
    private static final class SeasonChangeListener implements Consumer<SeasonChangedEvent.Standard> {

        @Override
        public void accept(SeasonChangedEvent.Standard event) {
            try {
                if (!active) {
                    return;
                }
                if (!(event.getLevel() instanceof ServerLevel level)) {
                    return; // client-side sync fires this too; server drives the show
                }
                if (level.dimension() != Level.OVERWORLD) {
                    return; // one flyover per season turn, over the seasonal world
                }
                Season.SubSeason prev = event.getPrevSeason();
                Season.SubSeason next = event.getNewSeason();
                if (prev == null || next == null || prev.getSeason() == next.getSeason()) {
                    return; // sub-season rollover inside the same season - not a season turn
                }
                if (!ModList.get().isLoaded("orespawn")) {
                    logMissingOnce("orespawn (Prince flyover)");
                    return;
                }
                List<ServerPlayer> players = level.players();
                if (players.isEmpty()) {
                    return;
                }
                ServerPlayer player = players.get(level.random.nextInt(players.size()));
                // Wiring note: PrinceFlyover.triggerNear(ServerLevel, BlockPos) is a
                // trigger the parent must expose on the alive module (the class is
                // currently package-private with no public entry point). Written
                // against that agreed signature; see this module's wiring notes.
                PrinceFlyover.triggerNear(level, player.blockPosition());
                // Season of the King: witnessed by the player the flyover anchors on.
                net.minecraft.advancements.AdvancementHolder seasonAdv =
                        level.getServer().getAdvancements().get(
                                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                        "orespawn_integrations", "alive_world/season_of_the_king"));
                if (seasonAdv != null) {
                    player.getAdvancements().award(seasonAdv, "witnessed");
                }
            } catch (Throwable t) {
                logOnce("season_change", t);
            }
        }
    }
}
