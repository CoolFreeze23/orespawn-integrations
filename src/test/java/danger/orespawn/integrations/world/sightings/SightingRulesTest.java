package danger.orespawn.integrations.world.sightings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** The sighting rules and the family table (system-sightings). Pure logic and file reads; no game classes. */
class SightingRulesTest {

    @Test
    void nothingUnderThirtyTwoOrPastTheView() {
        assertFalse(SightingRules.inWindow(31.9, 32));
        assertTrue(SightingRules.inWindow(32, 32));
        assertTrue(SightingRules.inWindow(110, 32));
        assertFalse(SightingRules.inWindow(130, 32));
        assertEquals(24, SightingRules.minimum(true, 32, 24), "a Scout from 24");
        assertEquals(32, SightingRules.minimum(false, 32, 24));
    }

    @Test
    void oneSecondIsTwoChecksOnTheSameTarget() {
        assertFalse(SightingRules.secondOnTarget(null, "s:a@1"));
        assertFalse(SightingRules.secondOnTarget("s:a@1", "s:a@2"));
        assertTrue(SightingRules.secondOnTarget("s:a@1", "s:a@1"));
        assertFalse(SightingRules.secondOnTarget("s:a@1", null));
    }

    @Test
    void duskWindow() {
        assertTrue(SightingRules.isDusk(12000));
        assertTrue(SightingRules.isDusk(24000L * 5 + 13999));
        assertFalse(SightingRules.isDusk(14000));
        assertFalse(SightingRules.isDusk(6000));
        assertEquals(5, SightingRules.day(24000L * 5 + 13000));
    }

    @Test
    void startsRingKeepsTheNewest512() {
        SightingRules.Ring ring = new SightingRules.Ring(SightingRules.MAX_STARTS);
        for (long i = 0; i < 600; i++) {
            assertTrue(ring.add(i, 1));
        }
        assertFalse(ring.add(599, 1), "a start already sighted is not new");
        assertEquals(512, ring.size());
        assertEquals(88, ring.valueAt(0), "the oldest dropped first");
        assertEquals(599, ring.valueAt(511));
        assertFalse(ring.contains(10, 1));
        assertTrue(ring.add(599, 2), "the same chunk with another structure is another start");
    }

    @Test
    void familyBitsFitTheLong() {
        Set<Long> bits = new HashSet<>();
        for (int n = 1; n <= 51; n++) {
            assertTrue(bits.add(SightingRules.bit(n)));
        }
        assertEquals(0L, SightingRules.bit(0));
    }

    @Test
    void familyTableHasTheFortyAndTheirPages() throws Exception {
        SightingFamilies families = SightingFamilies.get();
        long core = families.all().stream().filter(f -> f.number() <= 40).count();
        assertEquals(40, core);
        Set<String> slugs = new HashSet<>();
        for (SightingFamilies.Family f : families.all()) {
            assertTrue(slugs.add(f.slug()), "slug twice: " + f.slug());
            String path = "/data/orespawn_integrations/advancement/avistamentos/" + f.slug() + ".json";
            try (InputStream in = SightingRulesTest.class.getResourceAsStream(path)) {
                assertNotNull(in, path);
            }
            if (!f.kind().equals("none")) {
                assertFalse(f.targets().isEmpty(), f.slug() + " has no targets");
            }
        }
        assertEquals("none", families.number(39).kind(), "the urutau page stays locked in 0.10.0");
        assertEquals(1, families.structure("minecraft:village_plains").number());
        assertEquals(12, families.structure("minecraft:pillager_outpost").number());
        assertEquals(35, families.entity("orespawn:godzilla").number());
        assertEquals(43, families.block("hbmsntm:crashed_bomb").number());
        assertEquals(26, families.hubThing("hatching_ground").number());
        try (InputStream in = SightingRulesTest.class.getResourceAsStream(
                "/data/orespawn_integrations/advancement/avistamentos/terra_a_vista.json")) {
            assertNotNull(in);
        }
    }
}
