package danger.orespawn.integrations.compat.journeymap.structures;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * The structures that get a JourneyMap marker, mapped to their marker style.
 *
 * Two groups:
 * - our own Tier-1 datapack structures ({@code orespawn_integrations:*},
 *   defined in data/orespawn_integrations/worldgen/structure/), and
 * - the iconic OreSpawn landmarks players actually hunt for, ids verified
 *   against data/orespawn/worldgen/structure/ in
 *   orespawn-1.21.1-1.0.0-beta.3.jar. Note the port ships the challenge
 *   tower as king/queen variants and the ender castle as end/islands
 *   variants - there is no plain "challenge_tower"/"ender_castle" id.
 *
 * The two boss arenas share the skull icon per the marker design (skull =
 * "a boss lives here") but are tinted apart: Mobzilla red, Kraken deep-sea
 * blue. Everything else has a bespoke icon.
 */
final class TrackedStructures {

    private static final int UNTINTED = 0xFFFFFF;
    private static final int MOBZILLA_RED = 0xFF6B5E;
    private static final int KRAKEN_BLUE = 0x6EC6FF;

    private static final Map<ResourceLocation, StructureMarkerStyle> TRACKED = Map.ofEntries(
            // --- our structures (orespawn_integrations namespace) ---
            Map.entry(ours("mobzilla_arena"),
                    StructureMarkerStyle.of("skull", MOBZILLA_RED, "mobzilla_arena", "Mobzilla Arena")),
            Map.entry(ours("kraken_lair"),
                    StructureMarkerStyle.of("skull", KRAKEN_BLUE, "kraken_lair", "Kraken Lair")),
            // --- iconic OreSpawn landmarks (orespawn namespace) ---
            Map.entry(orespawn("challenge_tower_king"),
                    StructureMarkerStyle.of("tower", UNTINTED, "challenge_tower_king", "Challenge Tower (King)")),
            Map.entry(orespawn("challenge_tower_queen"),
                    StructureMarkerStyle.of("tower", UNTINTED, "challenge_tower_queen", "Challenge Tower (Queen)")),
            Map.entry(orespawn("kyuubi_dungeon"),
                    StructureMarkerStyle.of("torii", UNTINTED, "kyuubi_dungeon", "Kyuubi Dungeon")),
            Map.entry(orespawn("ender_castle_end"),
                    StructureMarkerStyle.of("castle", UNTINTED, "ender_castle_end", "Ender Castle")),
            Map.entry(orespawn("ender_castle_islands"),
                    StructureMarkerStyle.of("castle", UNTINTED, "ender_castle_islands", "Ender Castle")),
            Map.entry(orespawn("mantis_nest"),
                    StructureMarkerStyle.of("mantis", UNTINTED, "mantis_nest", "Mantis Nest")),
            Map.entry(orespawn("inca_pyramid"),
                    StructureMarkerStyle.of("pyramid", UNTINTED, "inca_pyramid", "Inca Pyramid")));

    static boolean isTracked(ResourceLocation structureId) {
        return TRACKED.containsKey(structureId);
    }

    static StructureMarkerStyle styleFor(ResourceLocation structureId) {
        return TRACKED.get(structureId);
    }

    static int count() {
        return TRACKED.size();
    }

    private static ResourceLocation ours(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                danger.orespawn.integrations.OreSpawnIntegrations.MODID, path);
    }

    private static ResourceLocation orespawn(String path) {
        return ResourceLocation.fromNamespaceAndPath("orespawn", path);
    }

    private TrackedStructures() {
    }
}
