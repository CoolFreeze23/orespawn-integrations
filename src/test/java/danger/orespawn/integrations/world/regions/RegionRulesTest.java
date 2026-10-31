package danger.orespawn.integrations.world.regions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * The region lookup order, the Chaos depth cutoffs, the 4-byte record and the 10 s hold (system-region-names).
 * Pure logic; no game classes.
 */
class RegionRulesTest {

    @Test
    void orderIsTheRegionCardsOrder() {
        List<String> expected = List.of("old_ground", "usurpers_lawn", "wetlands", "selva", "executioners_yards",
                "moth_heights", "frost", "dark_wood", "pine_north", "sertao", "greenwood", "abyssal_coast", "larder",
                "countryside");
        assertEquals(expected, RegionTable.ORDER.stream().map(RegionTable.Region::slug).toList());
        Set<Integer> numbers = new HashSet<>();
        RegionTable.ORDER.forEach(r -> numbers.add(r.l()));
        assertEquals(14, numbers.size(), "each L number once");
    }

    @Test
    void firstTagInTheOrderWins() {
        // A Distant Lands grove is a forest after the retag: the Old Ground still wins over the Greenwood.
        assertEquals(1, RegionTable.regionOf(s -> s.equals("old_ground") || s.equals("greenwood")));
        // A BOP bog is swamp and taiga: the Wetlands.
        assertEquals(5, RegionTable.regionOf(s -> s.equals("wetlands") || s.equals("pine_north")));
        // Snowy peaks: the Moth Heights before the Frost.
        assertEquals(9, RegionTable.regionOf(s -> s.equals("frost") || s.equals("moth_heights")));
        // BOP dead forest: the Dark Wood before the Pine North.
        assertEquals(4, RegionTable.regionOf(s -> s.equals("pine_north") || s.equals("dark_wood")));
        // Selva-Old Ground border: the Old Ground.
        assertEquals(1, RegionTable.regionOf(s -> s.equals("selva") || s.equals("old_ground")));
        // Nothing holds it: the Countryside.
        assertEquals(RegionTable.COUNTRYSIDE, RegionTable.regionOf(s -> false));
    }

    @Test
    void chaosDepths() {
        assertEquals(RegionTable.DEPTH_RASO, RegionTable.depthOf(100));
        assertEquals(RegionTable.DEPTH_RASO, RegionTable.depthOf(77));
        assertEquals(RegionTable.DEPTH_MEIO, RegionTable.depthOf(76));
        assertEquals(RegionTable.DEPTH_MEIO, RegionTable.depthOf(56));
        assertEquals(RegionTable.DEPTH_BERCARIO, RegionTable.depthOf(55));
    }

    @Test
    void dimensionsHaveTheirTiers() {
        assertEquals(2, RegionTable.DIMENSIONS.get("orespawn:utopia").tier());
        assertEquals(3, RegionTable.DIMENSIONS.get("orespawn:mining").tier());
        assertEquals(2, RegionTable.DIMENSIONS.get("orespawn:village").tier());
        assertEquals(4, RegionTable.DIMENSIONS.get("orespawn:crystal").tier());
        assertEquals(5, RegionTable.DIMENSIONS.get("orespawn:islands").tier());
        assertEquals(6, RegionTable.DIMENSIONS.get("minecraft:the_end").tier());
        assertEquals(7, RegionTable.DIMENSIONS.get("orespawn:chaos").tier());
        assertEquals(0, RegionTable.DIMENSIONS.get("minecraft:the_nether").tier());
        assertFalse(RegionTable.DIMENSIONS.containsKey("minecraft:overworld"), "the Land is its regions");
        Set<Integer> indices = new HashSet<>();
        RegionTable.DIMENSIONS.values().forEach(d -> assertTrue(indices.add(d.index()), "index used twice"));
        indices.forEach(i -> assertFalse(RegionTable.isRegion(i) || RegionTable.isDepth(i)));
    }

    @Test
    void recordPacksIntoFourBytes() {
        int state = RegionState.pack(14, 9, 0x3FFF);
        assertEquals(14, RegionState.last(state));
        assertEquals(9, RegionState.hold(state));
        assertEquals(0x3FFF, RegionState.mask(state));
        assertEquals(0, state >>> 30, "two spare bits stay clear");
    }

    @Test
    void maskRecordsEachRegionOnce() {
        int state = 0;
        for (int l = 1; l <= 14; l++) {
            assertFalse(RegionState.visited(state, l));
            state = RegionState.markVisited(state, l);
            assertTrue(RegionState.visited(state, l));
        }
        assertEquals(0x3FFF, RegionState.mask(state));
        assertEquals(state, RegionState.markVisited(state, 15), "no bit beyond the fourteenth");
    }

    @Test
    void tenStepsInARowCross() {
        int state = RegionState.pack(2, 0, 0);
        int candidate = 0;
        for (int i = 1; i < RegionState.HOLD_STEPS; i++) {
            RegionState.Step step = RegionState.step(state, candidate, 5);
            assertFalse(step.crossed(), "step " + i);
            state = step.state();
            candidate = step.candidate();
        }
        RegionState.Step last = RegionState.step(state, candidate, 5);
        assertTrue(last.crossed());
        assertEquals(5, RegionState.last(last.state()));
    }

    @Test
    void aBorderNeverFlickers() {
        int state = RegionState.pack(2, 0, 0);
        int candidate = 0;
        for (int i = 0; i < 40; i++) {
            RegionState.Step step = RegionState.step(state, candidate, i % 6 < 5 ? 5 : 2);
            assertFalse(step.crossed());
            state = step.state();
            candidate = step.candidate();
        }
        assertEquals(2, RegionState.last(state));
    }

    @Test
    void everyRegionTagFileExists() throws IOException {
        for (RegionTable.Region region : RegionTable.ORDER) {
            String path = "/data/orespawn_integrations/tags/worldgen/biome/region/" + region.slug() + ".json";
            try (InputStream in = RegionRulesTest.class.getResourceAsStream(path)) {
                assertTrue(in != null, path);
                assertTrue(new String(in.readAllBytes(), StandardCharsets.UTF_8).contains("\"values\""), path);
            }
        }
    }
}
