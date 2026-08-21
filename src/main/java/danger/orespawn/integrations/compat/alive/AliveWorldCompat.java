package danger.orespawn.integrations.compat.alive;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import danger.orespawn.integrations.OreSpawnIntegrations;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Entry point for the "alive world" ambience layer: boss-kill celebrations,
 * meteor nights, Prince flyovers, and calendar-day festivities. Only
 * classloaded when the "orespawn" mod is present -
 * {@link danger.orespawn.integrations.OreSpawnIntegrations} invokes
 * {@link #init(IEventBus)} reflectively after a ModList check, so referencing
 * port classes ({@code danger.orespawn.entity.ThePrinceAdult},
 * {@code danger.orespawn.util.SeasonalDates}) directly from this package is
 * safe.
 *
 * <p>House defensive style: every game-event handler body is wrapped in
 * try/catch with a log-once mute so a single broken ambience feature can never
 * crash a tick loop or spam the log. All scheduled work runs through
 * {@link AliveScheduler}, which is drained on the server thread and wiped on
 * server stop.</p>
 */
public final class AliveWorldCompat {

    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();

    private AliveWorldCompat() {
    }

    public static void init(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(AliveScheduler::onServerTick);
        NeoForge.EVENT_BUS.addListener(AliveWorldCompat::onServerStopped);
        NeoForge.EVENT_BUS.addListener(CelebrationHandler::onBossDeath);
        NeoForge.EVENT_BUS.addListener(CelebrationHandler::onLivingChangeTarget);
        NeoForge.EVENT_BUS.addListener(CelebrationHandler::onNewYearTick);
        NeoForge.EVENT_BUS.addListener(MeteorEvent::onLevelTick);
        NeoForge.EVENT_BUS.addListener(PrinceFlyover::onLevelTick);
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        AliveScheduler.reset();
        CelebrationHandler.reset();
        PrinceFlyover.reset();
    }

    // --- defensive plumbing shared by the whole package ---

    static void logOnce(String key, Throwable t) {
        if (LOGGED.add(key)) {
            OreSpawnIntegrations.LOGGER.error(
                    "[alive] handler '{}' failed; muting further reports of this failure", key, t);
        }
    }

    static void logMissingOnce(String what) {
        if (LOGGED.add("missing:" + what)) {
            OreSpawnIntegrations.LOGGER.warn(
                    "[alive] '{}' is not registered/loaded; the feature that needs it stays inert", what);
        }
    }

    /**
     * Resolves an entity type by id, or null (log-once) if absent. Uses
     * containsKey because ENTITY_TYPE is a defaulted registry and a bare get()
     * would silently hand back the default entry.
     */
    static EntityType<?> entityType(String namespace, String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, path);
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            logMissingOnce("entity " + id);
            return null;
        }
        return BuiltInRegistries.ENTITY_TYPE.get(id);
    }

    /**
     * Resolves a block's default state by id, or null (log-once) if absent.
     * containsKey guard for the same defaulted-registry reason as above.
     */
    static BlockState blockState(String namespace, String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, path);
        if (!BuiltInRegistries.BLOCK.containsKey(id)) {
            logMissingOnce("block " + id);
            return null;
        }
        return BuiltInRegistries.BLOCK.get(id).defaultBlockState();
    }

    /**
     * Grants criterion "witnessed" of one of this addon's advancements to every
     * player within {@code radius} of the given point. Missing advancement
     * (datapack module not landed / stripped) logs once and no-ops.
     */
    static void grantWitnessed(ServerLevel level, String advancementPath, double x, double y, double z,
                               double radius) {
        AdvancementHolder holder = level.getServer().getAdvancements()
                .get(ResourceLocation.fromNamespaceAndPath(OreSpawnIntegrations.MODID, advancementPath));
        if (holder == null) {
            logMissingOnce("advancement " + advancementPath);
            return;
        }
        double radiusSq = radius * radius;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(x, y, z) <= radiusSq) {
                player.getAdvancements().award(holder, "witnessed");
            }
        }
    }
}
