package danger.orespawn.integrations.compat.waystones;

import danger.orespawn.integrations.OreSpawnIntegrations;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.waystones.api.MutableWaystone;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystoneOrigin;
import net.blay09.mods.waystones.api.event.GenerateWaystoneNameEvent;
import net.blay09.mods.waystones.api.event.WaystoneInitializedEvent;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.Locale;

/**
 * Waystones (Balm) integration: gives structure-generated, DUNGEON-origin
 * waystones a themed name when they sit inside one of our companion
 * structures instead of the random MrPork name.
 *
 * Flow (verified against waystones-neoforge-21.1.40 bytecode): the shrine's
 * waystone block auto-registers on first server chunk load
 * (WaystoneBlockEntityBase.onLoad -> initializeWaystone -> addWaystone),
 * which fires WaystoneInitializedEvent with a still-nameless waystone.
 * Setting a name there makes hasName() true, so the name generator that
 * would otherwise run on first player activation never replaces it.
 * GenerateWaystoneNameEvent is handled too as a second chance in case the
 * waystone somehow reaches activation nameless.
 */
public final class WaystonesCompat {

    private static final String LANG_PREFIX = "waystone." + OreSpawnIntegrations.MODID + ".";

    private WaystonesCompat() {
    }

    public static void init(IEventBus modBus) {
        // Balm may not be bootstrapped while mods are still constructing, so
        // defer handler registration to common setup (runs after every mod
        // exists) and hop to the main thread since setup is parallel-dispatched.
        modBus.addListener(FMLCommonSetupEvent.class,
                event -> event.enqueueWork(WaystonesCompat::registerBalmHandlers));
    }

    private static void registerBalmHandlers() {
        try {
            Balm.getEvents().onEvent(WaystoneInitializedEvent.class, WaystonesCompat::onWaystoneInitialized);
            Balm.getEvents().onEvent(GenerateWaystoneNameEvent.class, WaystonesCompat::onGenerateWaystoneName);
            OreSpawnIntegrations.LOGGER.info("Waystones compat: dungeon waystone naming hooks registered");
        } catch (Throwable t) {
            OreSpawnIntegrations.LOGGER.error("Waystones compat: failed to register Balm event handlers", t);
        }
    }

    private static void onWaystoneInitialized(WaystoneInitializedEvent event) {
        try {
            Waystone waystone = event.getWaystone();
            if (waystone.getOrigin() != WaystoneOrigin.DUNGEON || waystone.hasName()) {
                return;
            }
            Component name = resolveArenaName(waystone);
            if (name != null && waystone instanceof MutableWaystone mutable) {
                // The manager already holds this exact instance and marked its
                // SavedData dirty, so mutating the name here persists with it.
                mutable.setName(name);
                OreSpawnIntegrations.LOGGER.info("Named dungeon waystone at {} '{}'",
                        waystone.getPos(), name.getString());
            }
        } catch (Throwable t) {
            OreSpawnIntegrations.LOGGER.warn("Waystones compat: failed to name initialized waystone", t);
        }
    }

    private static void onGenerateWaystoneName(GenerateWaystoneNameEvent event) {
        try {
            Waystone waystone = event.getWaystone();
            if (waystone.getOrigin() != WaystoneOrigin.DUNGEON) {
                return;
            }
            Component name = resolveArenaName(waystone);
            if (name != null) {
                event.setName(name);
            }
        } catch (Throwable t) {
            OreSpawnIntegrations.LOGGER.warn("Waystones compat: failed to override generated waystone name", t);
        }
    }

    /**
     * Returns the themed name if the waystone's position is inside an
     * OreSpawn-related structure piece, or null to leave vanilla behavior.
     */
    private static Component resolveArenaName(Waystone waystone) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return null;
        }
        ServerLevel level = server.getLevel(waystone.getDimension());
        if (level == null) {
            return null;
        }
        StructureStart start = level.structureManager()
                .getStructureWithPieceAt(waystone.getPos(), WaystonesCompat::isOreSpawnStructure);
        if (!start.isValid()) {
            return null;
        }
        ResourceLocation id = level.registryAccess()
                .registryOrThrow(Registries.STRUCTURE)
                .getKey(start.getStructure());
        return id != null ? nameForStructure(id) : null;
    }

    private static boolean isOreSpawnStructure(Holder<Structure> holder) {
        return holder.unwrapKey().map(key -> {
            String namespace = key.location().getNamespace();
            return OreSpawnIntegrations.MODID.equals(namespace) || "orespawn".equals(namespace);
        }).orElse(false);
    }

    private static Component nameForStructure(ResourceLocation structureId) {
        String path = structureId.getPath();
        return switch (path) {
            case "kraken_lair" -> Component.translatable(LANG_PREFIX + "kraken_lair");
            case "mobzilla_arena" -> Component.translatable(LANG_PREFIX + "mobzilla_arena");
            default -> {
                // Substring heuristics so future companion structures (and the
                // King arena variants in the orespawn namespace) stay covered.
                if (path.contains("kraken")) {
                    yield Component.translatable(LANG_PREFIX + "kraken_lair");
                }
                if (path.contains("mobzilla") || path.contains("godzilla")) {
                    yield Component.translatable(LANG_PREFIX + "mobzilla_arena");
                }
                if (path.contains("king")) {
                    yield Component.translatable(LANG_PREFIX + "kings_arena");
                }
                yield Component.literal(titleCase(path) + " Waystone");
            }
        };
    }

    private static String titleCase(String path) {
        StringBuilder builder = new StringBuilder(path.length());
        for (String word : path.split("_")) {
            if (word.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return builder.toString();
    }
}
