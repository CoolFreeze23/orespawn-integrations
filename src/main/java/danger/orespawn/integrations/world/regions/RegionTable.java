package danger.orespawn.integrations.world.regions;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * What every place is called: the fourteen overworld regions in their priority order, the dimensions with their tier
 * or side-realm line, and Chaos's three depths. Pure tables and rules (no game classes), so the order, the depth
 * cutoffs and the indices are unit tested.
 *
 * <p>The order is region-overworld's table, the one the region cards' border rules agree on: the Old Ground first (its
 * groves are forests too), the Moth Heights before the Frost (snow on a mountain is the Heights'), the Dark Wood
 * before the Pine North (the BOP dead woods are taiga too), the Larder's cave biomes just before the Countryside,
 * which also takes every overworld biome no tag above holds.
 */
public final class RegionTable {

    /** A region: its L number (the lang key and the first-visit bit) and its tag slug. */
    public record Region(int l, String slug) {}

    public static final List<Region> ORDER = List.of(
            new Region(1, "old_ground"),
            new Region(13, "usurpers_lawn"),
            new Region(5, "wetlands"),
            new Region(6, "selva"),
            new Region(8, "executioners_yards"),
            new Region(9, "moth_heights"),
            new Region(10, "frost"),
            new Region(4, "dark_wood"),
            new Region(11, "pine_north"),
            new Region(7, "sertao"),
            new Region(3, "greenwood"),
            new Region(12, "abyssal_coast"),
            new Region(14, "larder"),
            new Region(2, "countryside"));

    /** The Countryside's L number: the fallback for an overworld biome no region tag holds. */
    public static final int COUNTRYSIDE = 2;

    /** Colours of the titles (system-region-names, "How a title looks"). */
    public static final int GREEN = 0xA8D08D;
    public static final int GOLD = 0xE6C15A;
    public static final int GREY = 0x9A9A9A;
    public static final int WHITE = 0xFFFFFF;

    /** Chaos's depths: the Nursery's cutoffs (system-bercario: middle.max_y 76, nursery.max_y 56). */
    public static final int MIDDLE_MAX_Y = 76;
    public static final int NURSERY_MAX_Y = 56;

    /** Indices kept in the attachment's first byte: 1-14 the regions, 20+ dimensions, 40-42 Chaos's depths. */
    public static final int DEPTH_RASO = 40;
    public static final int DEPTH_MEIO = 41;
    public static final int DEPTH_BERCARIO = 42;

    /**
     * A dimension's title: {@code key} is the path under {@code orespawn_integrations.dimension.}; {@code tier} the
     * tier line under it (0 for a side realm, which shows its own subtitle instead).
     */
    public record Dimension(int index, String key, int tier, int color) {}

    public static final String CHAOS = "orespawn:chaos";
    public static final String OVERWORLD = "minecraft:overworld";
    /** A Planarium's dimension type: runtime levels with their own ids, all one "pockets" title. */
    public static final String ARS_JAR_TYPE = "ars_nouveau:jar";

    public static final Map<String, Dimension> DIMENSIONS = Map.ofEntries(
            Map.entry("minecraft:the_nether", new Dimension(20, "minecraft.the_nether", 0, WHITE)),
            Map.entry("minecraft:the_end", new Dimension(21, "minecraft.the_end", 6, GOLD)),
            Map.entry("orespawn:utopia", new Dimension(22, "orespawn.utopia", 2, GOLD)),
            Map.entry("orespawn:mining", new Dimension(23, "orespawn.mining", 3, GOLD)),
            Map.entry("orespawn:village", new Dimension(24, "orespawn.village", 2, GOLD)),
            Map.entry("orespawn:crystal", new Dimension(25, "orespawn.crystal", 4, GOLD)),
            Map.entry("orespawn:islands", new Dimension(26, "orespawn.islands", 5, GOLD)),
            Map.entry(CHAOS, new Dimension(27, "orespawn.chaos", 7, GREY)),
            Map.entry("twilightforest:twilight_forest", new Dimension(28, "twilightforest.twilight_forest", 0, WHITE)),
            Map.entry("aether:the_aether", new Dimension(29, "aether.the_aether", 0, WHITE)),
            Map.entry("the_bumblezone:the_bumblezone", new Dimension(30, "the_bumblezone.the_bumblezone", 0, WHITE)),
            Map.entry("vortexmod:tardisdim", new Dimension(31, "vortexmod.tardisdim", 0, WHITE)),
            Map.entry("vortexmod:vortexdim", new Dimension(32, "vortexmod.vortexdim", 0, WHITE)),
            Map.entry("vortexmod:skarodim", new Dimension(33, "vortexmod.skarodim", 0, WHITE)),
            Map.entry("irons_spellbooks:pocket_dimension", new Dimension(34, "irons_spellbooks.pocket_dimension", 0, WHITE)),
            Map.entry(ARS_JAR_TYPE, new Dimension(35, "ars_nouveau.jar", 0, WHITE)));

    private RegionTable() {
    }

    /** The first region in the order whose tag {@code holds} says yes; the Countryside when none does. */
    public static int regionOf(Predicate<String> holds) {
        for (Region region : ORDER) {
            if (holds.test(region.slug())) {
                return region.l();
            }
        }
        return COUNTRYSIDE;
    }

    /** Chaos's depth at block Y {@code y}: above 76 the Shallows, 56-76 the Middle, below 56 the Nursery. */
    public static int depthOf(int y) {
        if (y > MIDDLE_MAX_Y) {
            return DEPTH_RASO;
        }
        return y >= NURSERY_MAX_Y ? DEPTH_MEIO : DEPTH_BERCARIO;
    }

    public static boolean isRegion(int index) {
        return index >= 1 && index <= 14;
    }

    public static boolean isDepth(int index) {
        return index >= DEPTH_RASO && index <= DEPTH_BERCARIO;
    }

    public static String depthSlug(int index) {
        return switch (index) {
            case DEPTH_RASO -> "raso";
            case DEPTH_MEIO -> "meio";
            default -> "bercario";
        };
    }

    /** The dimension entry with this index, or null. */
    public static Dimension dimensionByIndex(int index) {
        for (Dimension d : DIMENSIONS.values()) {
            if (d.index() == index) {
                return d;
            }
        }
        return null;
    }
}
