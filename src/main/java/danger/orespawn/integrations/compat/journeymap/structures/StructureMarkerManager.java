package danger.orespawn.integrations.compat.journeymap.structures;

import danger.orespawn.integrations.OreSpawnIntegrations;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.display.MarkerOverlay;
import journeymap.api.v2.client.event.MappingEvent;
import journeymap.api.v2.client.model.MapImage;
import journeymap.api.v2.client.model.TextProperties;
import journeymap.api.v2.common.event.ClientEventRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Detects tracked structures as their chunks come in and drops a
 * {@link MarkerOverlay} with a bespoke 24x24 icon on the JourneyMap map.
 *
 * Event surface (verified with javap against the bundled
 * journeymap-api-neoforge-2.0.0 jar): the JM v2 API exposes NO chunk event -
 * ClientEventRegistry only offers mapping/display/waypoint/radar events. So
 * the chunk signal comes from NeoForge's {@link ChunkEvent.Load} instead,
 * filtered to {@link ServerLevel} chunks: on a client VM a ServerLevel only
 * exists when the INTEGRATED server is running, which is exactly the guard
 * the feature needs (structure starts are a server-side concept; on remote
 * servers the event never fires and the feature is silently inert).
 * {@link ClientEventRegistry#MAPPING_EVENT} supplies the lifecycle: markers
 * are (re)shown after MAPPING_STARTED and wiped on MAPPING_STOPPED.
 *
 * Threading: chunk-load handlers may run on the server thread, so detection
 * only reads chunk-local data (getAllStarts + a structureManager
 * confirmation against the same chunk - never a lookup that could
 * sync-load neighbors) and records results into concurrent collections.
 * Every JourneyMap call happens on the client tick.
 *
 * Failure policy: any throwable from a JourneyMap call logs ONCE and
 * disables the feature for the rest of the session (a broken map add-on
 * must never spam the log or take the pack down).
 */
final class StructureMarkerManager {

    /**
     * Ownership key for everything this feature registers with JourneyMap
     * (plugin id, event subscriptions, overlay ownership). MUST stay
     * different from {@code OreSpawnIntegrations.MODID}: JourneyMap 6.0.4's
     * PluginHelper stores client plugins in a map keyed by getModId(), so a
     * second plugin under the mod's real id would clobber the waystone
     * bridge plugin (or vice versa). ClientAPI.playerAccepts() ignores the
     * value, it is purely a namespacing string.
     */
    static final String OWNER_ID = OreSpawnIntegrations.MODID + "_structures";

    /** Marker creation budget per client tick; keeps login hitch-free. */
    private static final int MAX_SHOWS_PER_TICK = 10;
    /** Label only appears once zoomed in a bit, to keep the map readable. */
    private static final int LABEL_MIN_ZOOM = 2;

    /** One detected structure start, immutable so it can cross threads. */
    private record DetectedStructure(ResourceKey<Level> dimension, ResourceLocation structureId,
                                     BlockPos center, String key) {
    }

    private final IClientAPI api;

    /** Everything detected this session for the current world, keyed by dim|structure|chunk. */
    private final Map<String, DetectedStructure> discovered = new ConcurrentHashMap<>();
    /** Detections waiting for the client tick to turn them into overlays. */
    private final ConcurrentLinkedQueue<DetectedStructure> pending = new ConcurrentLinkedQueue<>();
    /** Keys already shown since the last MAPPING_STARTED. Client thread only. */
    private final Set<String> shownKeys = new HashSet<>();

    private volatile boolean mappingActive;
    private volatile String worldId;
    private final AtomicBoolean broken = new AtomicBoolean();

    private StructureMarkerManager(IClientAPI api) {
        this.api = api;
    }

    static void install(IClientAPI api) {
        StructureMarkerManager manager = new StructureMarkerManager(api);
        ClientEventRegistry.MAPPING_EVENT.subscribe(OWNER_ID, manager::onMappingEvent);
        NeoForge.EVENT_BUS.addListener(manager::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(manager::onClientTick);
    }

    // ------------------------------------------------------------------
    // JourneyMap lifecycle
    // ------------------------------------------------------------------

    private void onMappingEvent(MappingEvent event) {
        try {
            if (event.getStage() == MappingEvent.Stage.MAPPING_STARTED) {
                // New world (or same world re-entered): drop stale state from
                // a different save, then re-queue everything already known so
                // markers survive dimension hops and re-logins within a session.
                // A null worldId cannot distinguish saves, so treat it as
                // always-new and simply re-detect from chunk loads.
                String newWorldId = event.getWorldId();
                if (newWorldId == null || !Objects.equals(newWorldId, worldId)) {
                    discovered.clear();
                }
                worldId = newWorldId;
                pending.clear();
                shownKeys.clear();
                pending.addAll(discovered.values());
                mappingActive = true;
            } else if (event.getStage() == MappingEvent.Stage.MAPPING_STOPPED) {
                mappingActive = false;
                pending.clear();
                shownKeys.clear();
                api.removeAll(OWNER_ID);
            }
        } catch (Throwable t) {
            failOnce("mapping lifecycle handling", t);
        }
    }

    // ------------------------------------------------------------------
    // Detection (integrated server side, possibly off-thread)
    // ------------------------------------------------------------------

    private void onChunkLoad(ChunkEvent.Load event) {
        if (broken.get() || !StructureMarkersConfig.isEnabled()) {
            return;
        }
        // ServerLevel in this VM == integrated server. Remote servers never
        // trip this; client-side LevelChunk loads are filtered out too.
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        try {
            ChunkAccess chunk = event.getChunk();
            // Structure STARTS only live in the origin chunk of a structure,
            // so this fires exactly once per structure instance and reads
            // nothing outside the chunk that just loaded.
            Map<Structure, StructureStart> starts = chunk.getAllStarts();
            if (starts.isEmpty()) {
                return;
            }
            Registry<Structure> registry = serverLevel.registryAccess().registryOrThrow(Registries.STRUCTURE);
            for (Map.Entry<Structure, StructureStart> entry : starts.entrySet()) {
                ResourceLocation id = registry.getKey(entry.getKey());
                if (id == null || !TrackedStructures.isTracked(id)) {
                    continue;
                }
                // Confirm through the level's StructureManager, restricted to
                // the already-loaded chunk (StructureAccess overload - cannot
                // trigger neighbor loads).
                StructureStart start = serverLevel.structureManager()
                        .getStartForStructure(SectionPos.bottomOf(chunk), entry.getKey(), chunk);
                if (start == null || !start.isValid()) {
                    continue;
                }
                BlockPos center = start.getBoundingBox().getCenter();
                String key = serverLevel.dimension().location() + "|" + id + "|" + chunk.getPos().toLong();
                DetectedStructure detected =
                        new DetectedStructure(serverLevel.dimension(), id, center, key);
                if (discovered.putIfAbsent(key, detected) == null && mappingActive) {
                    // Chunks that load before MAPPING_STARTED are only
                    // recorded; the mapping handler re-queues them.
                    pending.add(detected);
                }
            }
        } catch (Throwable t) {
            failOnce("structure detection", t);
        }
    }

    // ------------------------------------------------------------------
    // Display (client thread)
    // ------------------------------------------------------------------

    private void onClientTick(ClientTickEvent.Post event) {
        if (broken.get() || !mappingActive || pending.isEmpty()) {
            return;
        }
        if (!StructureMarkersConfig.isEnabled()) {
            // Toggled off mid-session: drop the backlog so it cannot grow.
            pending.clear();
            return;
        }
        int budget = MAX_SHOWS_PER_TICK;
        DetectedStructure detected;
        while (budget-- > 0 && (detected = pending.poll()) != null) {
            try {
                show(detected);
            } catch (Throwable t) {
                failOnce("marker display", t);
                return;
            }
        }
    }

    private void show(DetectedStructure detected) throws Exception {
        StructureMarkerStyle style = TrackedStructures.styleFor(detected.structureId());
        if (style == null || !shownKeys.add(detected.key())) {
            return; // untracked (cannot happen) or already on the map
        }
        String name = style.localizedName();
        MapImage icon = new MapImage(style.texture(), StructureMarkerStyle.ICON_SIZE, StructureMarkerStyle.ICON_SIZE)
                .setColor(style.iconColor())
                .centerAnchors();
        MarkerOverlay marker = new MarkerOverlay(OWNER_ID, detected.center(), icon);
        marker.setDimension(detected.dimension());
        marker.setTitle(name);
        marker.setLabel(name);
        marker.setDisplayOrder(100);
        // Active UIs / map types are left at the Overlay defaults, which are
        // already "all" (verified in the Overlay ctor bytecode); the Context
        // enum used to narrow them is deprecated in api 2.0.0. The default
        // minZoom is 2, though - lower it so markers show fully zoomed out.
        marker.setMinZoom(0);
        marker.setTextProperties(new TextProperties()
                .setScale(1.0f)
                .setColor(0xFFFFFF)
                .setFontShadow(true)
                .setMinZoom(LABEL_MIN_ZOOM)
                .setOffsetY(StructureMarkerStyle.ICON_SIZE / 2 + 2));
        api.show(marker);
    }

    // ------------------------------------------------------------------
    // Failure handling
    // ------------------------------------------------------------------

    /**
     * First failure wins: log once with the stack, then go inert for the
     * session. No JourneyMap cleanup here - this can run on the server
     * thread, and markers already shown are harmless until MAPPING_STOPPED
     * (where removal itself is also guarded).
     */
    private void failOnce(String stage, Throwable t) {
        if (broken.compareAndSet(false, true)) {
            OreSpawnIntegrations.LOGGER.error(
                    "JourneyMap structure markers: {} failed - disabling structure markers for this session",
                    stage, t);
            pending.clear();
            discovered.clear();
        }
    }
}
