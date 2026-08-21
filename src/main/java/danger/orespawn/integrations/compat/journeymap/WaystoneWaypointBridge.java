package danger.orespawn.integrations.compat.journeymap;

import danger.orespawn.integrations.OreSpawnIntegrations;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.event.MappingEvent;
import journeymap.api.v2.common.event.ClientEventRegistry;
import journeymap.api.v2.common.waypoint.Waypoint;
import journeymap.api.v2.common.waypoint.WaypointFactory;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystoneOrigin;
import net.blay09.mods.waystones.api.WaystoneTypes;
import net.blay09.mods.waystones.api.event.WaystoneRemoveReceivedEvent;
import net.blay09.mods.waystones.api.event.WaystoneUpdateReceivedEvent;
import net.blay09.mods.waystones.api.event.WaystonesListReceivedEvent;
import net.blay09.mods.waystones.config.WaystonesConfig;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Client-only bridge between Waystones and JourneyMap.
 *
 * Waystones 21.1.x already ships its own JourneyMap plugin (modId
 * "waystones") that creates a client waypoint for every waystone the player
 * knows, so this bridge deliberately does NOT duplicate that. Instead, when
 * the client learns about a DUNGEON-origin waystone (our arena shrines) it:
 *   - restyles the waypoint Waystones created with a distinct boss-red
 *     color, or
 *   - creates its own waypoint under our modId only if Waystones' built-in
 *     JourneyMap management is disabled in its config.
 *
 * Waystone knowledge arrives via Balm's client-received events
 * (WaystonesListReceivedEvent / WaystoneUpdateReceivedEvent, fired from the
 * KnownWaystones/UpdateWaystone packets; origin IS part of the network codec,
 * verified in WaystoneImpl bytecode). Since Waystones' own plugin handles the
 * same events and defers work until JourneyMap is ready, we never act inline:
 * pending waystones are drained on the client tick after mapping has started,
 * which guarantees the built-in waypoint exists before we go looking for it.
 */
final class WaystoneWaypointBridge {

    /** Distinct color for dungeon-origin waystones (boss red). */
    private static final int DUNGEON_COLOR = 0xD0342C;
    /** Give the built-in integration up to 10s to create its waypoint. */
    private static final int MAX_LOOKUP_TICKS = 200;
    /** modId Waystones' bundled JourneyMap plugin registers its waypoints under. */
    private static final String WAYSTONES_JM_ID = "waystones";
    /** Keyed custom-data slot storing the waystone uuid on waypoints we own. */
    private static final String CUSTOM_DATA_KEY = "waystone_uid";

    private final IClientAPI api;
    private volatile boolean journeyMapReady;
    private final Map<UUID, Waystone> pending = new LinkedHashMap<>();
    private final Map<UUID, Integer> lookupTicks = new HashMap<>();
    private final Map<UUID, Waypoint> ownWaypoints = new HashMap<>();

    private WaystoneWaypointBridge(IClientAPI api) {
        this.api = api;
    }

    static void install(IClientAPI api) {
        WaystoneWaypointBridge bridge = new WaystoneWaypointBridge(api);
        Balm.getEvents().onEvent(WaystonesListReceivedEvent.class, bridge::onListReceived);
        Balm.getEvents().onEvent(WaystoneUpdateReceivedEvent.class, bridge::onUpdateReceived);
        Balm.getEvents().onEvent(WaystoneRemoveReceivedEvent.class, bridge::onRemoveReceived);
        ClientEventRegistry.MAPPING_EVENT.subscribe(OreSpawnIntegrations.MODID, bridge::onMappingEvent);
        NeoForge.EVENT_BUS.addListener(bridge::onClientTick);
    }

    private void onMappingEvent(MappingEvent event) {
        if (event.getStage() == MappingEvent.Stage.MAPPING_STARTED) {
            journeyMapReady = true;
        } else if (event.getStage() == MappingEvent.Stage.MAPPING_STOPPED) {
            journeyMapReady = false;
            pending.clear();
            lookupTicks.clear();
            ownWaypoints.clear();
        }
    }

    private void onListReceived(WaystonesListReceivedEvent event) {
        try {
            if (WaystoneTypes.WAYSTONE.equals(event.getWaystoneType())) {
                event.getWaystones().forEach(this::track);
            }
        } catch (Throwable t) {
            OreSpawnIntegrations.LOGGER.warn("JourneyMap compat: waystone list handling failed", t);
        }
    }

    private void onUpdateReceived(WaystoneUpdateReceivedEvent event) {
        try {
            Waystone waystone = event.getWaystone();
            if (WaystoneTypes.WAYSTONE.equals(waystone.getWaystoneType())) {
                track(waystone);
            }
        } catch (Throwable t) {
            OreSpawnIntegrations.LOGGER.warn("JourneyMap compat: waystone update handling failed", t);
        }
    }

    private void onRemoveReceived(WaystoneRemoveReceivedEvent event) {
        try {
            UUID uid = event.getWaystoneId();
            pending.remove(uid);
            lookupTicks.remove(uid);
            Waypoint own = ownWaypoints.remove(uid);
            if (own != null) {
                api.removeWaypoint(OreSpawnIntegrations.MODID, own);
            }
        } catch (Throwable t) {
            OreSpawnIntegrations.LOGGER.warn("JourneyMap compat: waystone removal handling failed", t);
        }
    }

    private void track(Waystone waystone) {
        if (waystone.getOrigin() == WaystoneOrigin.DUNGEON && !waystone.isTransient()) {
            UUID uid = waystone.getWaystoneUid();
            pending.put(uid, waystone);
            lookupTicks.putIfAbsent(uid, 0);
        }
    }

    private void onClientTick(ClientTickEvent.Post event) {
        if (pending.isEmpty() || !journeyMapReady) {
            return;
        }
        Iterator<Map.Entry<UUID, Waystone>> iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Waystone> entry = iterator.next();
            boolean done;
            try {
                done = process(entry.getKey(), entry.getValue());
            } catch (Throwable t) {
                OreSpawnIntegrations.LOGGER.warn(
                        "JourneyMap compat: failed to style waypoint for waystone {}", entry.getKey(), t);
                done = true;
            }
            if (done) {
                lookupTicks.remove(entry.getKey());
                iterator.remove();
            }
        }
    }

    /** Returns true when this waystone needs no further processing. */
    private boolean process(UUID uid, Waystone waystone) {
        Waypoint builtin = findWaystonesWaypoint(uid);
        if (builtin != null) {
            applyDungeonStyle(builtin);
            return true;
        }
        if (!isBuiltinWaystonesBridgeActive()) {
            createOwnWaypoint(uid, waystone);
            return true;
        }
        // Built-in integration is on but hasn't created the waypoint yet;
        // retry next tick, give up quietly after MAX_LOOKUP_TICKS.
        int ticks = lookupTicks.merge(uid, 1, Integer::sum);
        return ticks > MAX_LOOKUP_TICKS;
    }

    /**
     * Waystones' plugin stores a small JSON blob (waystone uuid + type) via
     * the legacy single-field custom data, so a substring match on the uuid
     * finds it without depending on their private serialization class. The
     * deprecated getter is required to read what they wrote.
     */
    @SuppressWarnings("removal")
    private Waypoint findWaystonesWaypoint(UUID uid) {
        List<? extends Waypoint> waypoints = api.getWaypoints(WAYSTONES_JM_ID);
        if (waypoints == null) {
            return null;
        }
        String key = uid.toString();
        for (Waypoint waypoint : waypoints) {
            String data = waypoint.getCustomData();
            if (data != null && data.contains(key)) {
                return waypoint;
            }
        }
        return null;
    }

    private void applyDungeonStyle(Waypoint waypoint) {
        if (waypoint.getColor() != DUNGEON_COLOR) {
            waypoint.setColor(DUNGEON_COLOR);
        }
    }

    /**
     * Mirrors JourneyMapIntegration.shouldManageWaypoints() in the shipped
     * waystones jar; touches Waystones config internals, so any breakage
     * falls back to "assume active" (we then never create duplicates).
     */
    private boolean isBuiltinWaystonesBridgeActive() {
        try {
            var compatibility = WaystonesConfig.getActive().compatibility;
            if (compatibility.preferJourneyMapIntegrationMod && Balm.isModLoaded("jmi")) {
                return false;
            }
            return compatibility.journeyMap;
        } catch (Throwable t) {
            return true;
        }
    }

    private void createOwnWaypoint(UUID uid, Waystone waystone) {
        String key = uid.toString();
        // Dedupe against waypoints we created in an earlier sync or session.
        List<? extends Waypoint> ours = api.getWaypoints(OreSpawnIntegrations.MODID);
        if (ours != null) {
            for (Waypoint existing : ours) {
                if (key.equals(existing.getCustomData(CUSTOM_DATA_KEY))) {
                    applyDungeonStyle(existing);
                    ownWaypoints.put(uid, existing);
                    return;
                }
            }
        }
        String name = waystone.getEffectiveName().getString();
        Waypoint waypoint = WaypointFactory.createClientWaypoint(OreSpawnIntegrations.MODID,
                waystone.getPos().above(2), name, waystone.getDimension(), true);
        waypoint.setColor(DUNGEON_COLOR);
        waypoint.setCustomData(CUSTOM_DATA_KEY, key);
        api.addWaypoint(OreSpawnIntegrations.MODID, waypoint);
        ownWaypoints.put(uid, waypoint);
    }
}
