package danger.orespawn.integrations.compat.hbm;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.hbm.extprop.HbmLivingAttachments;

import danger.orespawn.integrations.OreSpawnIntegrations;
import danger.orespawn.integrations.config.IntegrationsConfig;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Thread 1 "It Was Always Uranium" — Geiger Prospecting: HBM's geiger counter
 * doubles as an ore-dowsing rod. Hold it in either hand and it ticks faster
 * near OreSpawn ruby / titanium / uranium veins — with ZERO radiation dose,
 * ever. North star: the geiger sings but never bites; right-clicking it shows
 * the player's dose still at 0, which IS the fantasy.
 *
 * <p>POLICY 4 — light-code integration, verified against
 * {@code hbmsntm-198A.jar} (pack mods folder, javap on the shipped classes;
 * compileOnly via {@code libs/hbmsntm-198A.jar}):
 * <ul>
 *   <li>{@code com.hbm.extprop.HbmLivingAttachments} — javap-confirmed
 *       {@code public static float getRadEnv(LivingEntity)} /
 *       {@code public static void setRadEnv(LivingEntity, float)}. radEnv is
 *       telemetry only: actual dose is applied exclusively inside
 *       {@code ContaminationUtil.contaminate(...)}, which this class never
 *       calls. Writing radEnv moves the needle, not the health bar.</li>
 *   <li>{@code com.hbm.handler.EntityEffectHandler.tick(LivingEntity)} —
 *       bytecode-confirmed: every 20 ticks ({@code tickCount % 20}) it runs
 *       {@code setRadBuf(getRadEnv(e))} then {@code setRadEnv(e, 0)}. HBM
 *       therefore SELF-CLEARS the channel: if we stop writing, the reading
 *       dies within at most two transfer windows (~2 s). Per the design
 *       directive we deliberately do NOT write 0 ourselves — a max-only write
 *       can never mask genuine HBM ambient telemetry (chunk radiation
 *       accumulates into the same field via contaminate).</li>
 *   <li>{@code com.hbm.items.tools.GeigerCounterItem.inventoryTick} —
 *       bytecode-confirmed: every 5 ticks it reads {@code getRadBuf} and picks
 *       a sound from overlapping bands: v&lt;1 occasional single click,
 *       1&le;v&lt;5 GEIGER1, 5&lt;v&lt;15 GEIGER2/3, 15&lt;v&lt;25 GEIGER4/5,
 *       v&gt;25 GEIGER6 ({@code NtmSoundEvents}). The signal strengths below
 *       (ruby 2, titanium 8, uranium 22) land in escalating audible bands:
 *       slow tick / medium chatter / frantic GEIGER4-5.</li>
 *   <li>{@code com.hbm.items.NtmItems} — registers the item as
 *       {@code hbmsntm:geiger_counter} (registration string confirmed in
 *       bytecode). Looked up by id here, so no compile dep on the item class;
 *       HBM's {@code hbmsntm:dosimeter} reads the same radBuf and reacts near
 *       veins for free, but only the geiger triggers our scan.</li>
 * </ul>
 * Version-range note: radEnv/radBuf are an internal telemetry contract of
 * hbmsntm, verified against 198A only. If a future HBM build moves or renames
 * them this class dies into its log-once catch and the pack keeps running —
 * re-verify on any hbmsntm bump.
 *
 * <p>Scanned blocks (ids verified against ORESPAWN-IDS.json "blocks"):
 * {@code orespawn:ore_ruby}, {@code orespawn:ore_titanium},
 * {@code orespawn:ore_uranium} plus their storage blocks {@code block_ruby},
 * {@code block_titanium}, {@code block_uranium} (a vault wall sings too — the
 * verification report's suggested list). NOTE: OreSpawn has no
 * {@code *_mining} / {@code *_nether} ore BLOCKS — those suffixes in
 * ORESPAWN-IDS are placed features that re-place the same three ore blocks in
 * the Mining dimension / Nether, so the three ids above cover every dimension
 * automatically.
 *
 * <p>Mechanics: every 20 ticks (staggered per player) a held-geiger player
 * scans a 6-block cube radius for the strongest signal block; the cached
 * strength is then re-asserted into radEnv every tick with
 * {@code max(current, signal)} semantics, so the write is phase-proof against
 * HBM's own 20-tick transfer and never lowers a real reading. The first scan
 * that pings an OreSpawn vein grants criterion {@code "witnessed"} of
 * advancement {@code orespawn_integrations:uranium/first_ping} (JSON ships
 * with the Thread 1 advancement module; missing = log-once no-op).
 *
 * <p>POLICY 1+2: only ever classloaded when hbmsntm is present — the main mod
 * class invokes {@link #init(IEventBus)} reflectively after a ModList check —
 * and every scan re-checks the Thread 1 "uranium" config toggle, so flipping
 * the toggle silences prospecting live (no /reload needed for this Java-side
 * feature). House defensive style: every handler body is try/catch with a
 * log-once mute (copied from
 * {@link danger.orespawn.integrations.compat.alive.AliveWorldCompat}).
 */
public final class GeigerProspectingCompat {

    /** Thread toggle id in {@link IntegrationsConfig} (POLICY 2). */
    private static final String THREAD_ID = "uranium";

    /** Rescan cadence; matches HBM's own radEnv-&gt;radBuf transfer window. */
    private static final int SCAN_INTERVAL_TICKS = 20;
    /** Cube "radius" in blocks around the player's feet (13^3 = 2197 reads/scan). */
    private static final int SCAN_RADIUS = 6;

    // Signal strengths, empirically mapped to GeigerCounterItem's sound bands
    // (see class header). Emission and harm are fully independent here — harm
    // is exactly zero.
    private static final float SIGNAL_RUBY = 2.0F;      // slow, curious ticking
    private static final float SIGNAL_TITANIUM = 8.0F;  // medium chatter
    private static final float SIGNAL_URANIUM = 22.0F;  // frantic GEIGER4/5

    private static final ResourceLocation GEIGER_ID =
            ResourceLocation.fromNamespaceAndPath("hbmsntm", "geiger_counter");
    private static final String FIRST_PING_ADVANCEMENT = "uranium/first_ping";
    private static final String CRITERION = "witnessed";

    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();

    /**
     * Per-player cached scan result (server thread only; concurrent map as
     * house belt-and-braces). Absent or 0 = no vein in range / not holding.
     */
    private static final Map<UUID, Float> SIGNALS = new ConcurrentHashMap<>();

    /** Lazily resolved handles (registries are populated long before ticks). */
    private static Item geigerCounter;
    private static Map<Block, Float> signalBlocks;
    private static boolean resolveFailed;

    private GeigerProspectingCompat() {
    }

    /** Invoked reflectively by the main mod class when hbmsntm is loaded. */
    public static void init(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(GeigerProspectingCompat::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(GeigerProspectingCompat::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(GeigerProspectingCompat::onServerStopped);
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer player)) {
                return; // server side only; the geiger item handles its own audio
            }
            boolean holding = isHoldingGeiger(player);
            // Rescan every 20 ticks, staggered across players by entity id so a
            // full server never scans everyone on the same tick.
            if ((player.tickCount + (player.getId() & 0xF)) % SCAN_INTERVAL_TICKS == 0) {
                rescan(player, holding);
            }
            if (!holding) {
                return; // no phantom writes; HBM zeroes radEnv within 20 ticks
            }
            Float signal = SIGNALS.get(player.getUUID());
            if (signal == null || signal <= 0.0F) {
                return;
            }
            // Phase-proof, max-only re-assert: whatever tick HBM's transfer
            // lands on, it sees at least our signal — and a genuine (stronger)
            // HBM ambient reading is never lowered.
            if (signal > HbmLivingAttachments.getRadEnv(player)) {
                HbmLivingAttachments.setRadEnv(player, signal);
            }
        } catch (Throwable t) {
            logOnce("player_tick", t);
        }
    }

    /** Refreshes the cached signal; also the advancement trigger point. */
    private static void rescan(ServerPlayer player, boolean holding) {
        if (!holding || !IntegrationsConfig.isThreadEnabled(THREAD_ID)) {
            SIGNALS.remove(player.getUUID());
            return;
        }
        Map<Block, Float> blocks = signalBlocks();
        if (blocks == null) {
            return; // ids failed to resolve; already logged, feature inert
        }
        ServerLevel level = player.serverLevel();
        BlockPos center = player.blockPosition();
        float strongest = 0.0F;
        // Positions within 6 blocks of a live player are always loaded chunks.
        scan:
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-SCAN_RADIUS, -SCAN_RADIUS, -SCAN_RADIUS),
                center.offset(SCAN_RADIUS, SCAN_RADIUS, SCAN_RADIUS))) {
            Float signal = blocks.get(level.getBlockState(pos).getBlock());
            if (signal != null && signal > strongest) {
                strongest = signal;
                if (strongest >= SIGNAL_URANIUM) {
                    break scan; // already at max — save the remaining reads
                }
            }
        }
        if (strongest > 0.0F) {
            SIGNALS.put(player.getUUID(), strongest);
            grantFirstPing(player);
        } else {
            SIGNALS.remove(player.getUUID());
        }
    }

    private static boolean isHoldingGeiger(ServerPlayer player) {
        Item geiger = geigerItem();
        return geiger != null
                && (player.getMainHandItem().is(geiger) || player.getOffhandItem().is(geiger));
    }

    /**
     * Resolves {@code hbmsntm:geiger_counter} once. getOptional (not a bare
     * get) because ITEM is a defaulted registry that would hand back AIR.
     */
    private static Item geigerItem() {
        if (geigerCounter == null && !resolveFailed) {
            geigerCounter = BuiltInRegistries.ITEM.getOptional(GEIGER_ID).orElse(null);
            if (geigerCounter == null) {
                resolveFailed = true;
                logMissingOnce("item " + GEIGER_ID);
            }
        }
        return geigerCounter;
    }

    /**
     * Resolves the OreSpawn signal blocks once. A missing id degrades to
     * "that block just doesn't ping" with a log-once — never a crash; if NONE
     * resolve (orespawn absent/renamed everything) the feature goes inert.
     */
    private static Map<Block, Float> signalBlocks() {
        if (signalBlocks == null) {
            Map<Block, Float> map = new HashMap<>();
            putSignal(map, "ore_ruby", SIGNAL_RUBY);
            putSignal(map, "block_ruby", SIGNAL_RUBY);
            putSignal(map, "ore_titanium", SIGNAL_TITANIUM);
            putSignal(map, "block_titanium", SIGNAL_TITANIUM);
            putSignal(map, "ore_uranium", SIGNAL_URANIUM);
            putSignal(map, "block_uranium", SIGNAL_URANIUM);
            signalBlocks = map.isEmpty() ? Map.of() : map;
        }
        return signalBlocks.isEmpty() ? null : signalBlocks;
    }

    private static void putSignal(Map<Block, Float> map, String path, float strength) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("orespawn", path);
        BuiltInRegistries.BLOCK.getOptional(id).ifPresentOrElse(
                block -> map.put(block, strength),
                () -> logMissingOnce("block " + id));
    }

    /**
     * Grants criterion {@value #CRITERION} of the Thread 1 advancement
     * {@code orespawn_integrations:uranium/first_ping}. award() is idempotent
     * (returns false once granted), and this only runs on the 20-tick scan
     * cadence while a vein is actually pinging.
     */
    private static void grantFirstPing(ServerPlayer player) {
        AdvancementHolder holder = player.serverLevel().getServer().getAdvancements()
                .get(ResourceLocation.fromNamespaceAndPath(
                        OreSpawnIntegrations.MODID, FIRST_PING_ADVANCEMENT));
        if (holder == null) {
            logMissingOnce("advancement " + FIRST_PING_ADVANCEMENT);
            return;
        }
        player.getAdvancements().award(holder, CRITERION);
    }

    private static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SIGNALS.remove(event.getEntity().getUUID());
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        SIGNALS.clear();
    }

    // --- defensive plumbing (house style, see AliveWorldCompat) ---

    private static void logOnce(String key, Throwable t) {
        if (LOGGED.add(key)) {
            OreSpawnIntegrations.LOGGER.error(
                    "[geiger_prospecting] handler '{}' failed; muting further reports of this failure",
                    key, t);
        }
    }

    private static void logMissingOnce(String what) {
        if (LOGGED.add("missing:" + what)) {
            OreSpawnIntegrations.LOGGER.warn(
                    "[geiger_prospecting] '{}' is not registered/loaded; the feature that needs it stays inert",
                    what);
        }
    }
}
