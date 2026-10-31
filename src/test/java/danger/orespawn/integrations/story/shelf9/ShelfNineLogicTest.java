package danger.orespawn.integrations.story.shelf9;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import danger.orespawn.integrations.command.CcRules;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Shelf 9's pure rules: the Register's layout, the site rule, the Four Ways, the mage list and the /cc table. */
class ShelfNineLogicTest {

    @Test
    void registerFillsEightSignsOfFourThenOverflows() {
        assertEquals(0, RegisterLayout.sign(1));
        assertEquals(0, RegisterLayout.row(1));
        assertEquals(0, RegisterLayout.sign(4));
        assertEquals(3, RegisterLayout.row(4));
        assertEquals(1, RegisterLayout.sign(5));
        assertEquals(7, RegisterLayout.sign(32));
        assertEquals(3, RegisterLayout.row(32));
        assertEquals(-1, RegisterLayout.sign(33));
        assertEquals(0, RegisterLayout.overflow(32));
        assertEquals(3, RegisterLayout.overflow(35));
    }

    @Test
    void siteFitsWithinFiveBlocksAndNoFluid() {
        int[] flat = new int[25];
        Arrays.fill(flat, 70);
        boolean[] dry = new boolean[25];
        assertTrue(SiteSearch.fits(flat, dry));
        flat[7] = 75;
        assertTrue(SiteSearch.fits(flat, dry), "a spread of 5 fits");
        flat[7] = 76;
        assertFalse(SiteSearch.fits(flat, dry), "a spread of 6 does not");
        flat[7] = 70;
        dry[12] = true;
        assertFalse(SiteSearch.fits(flat, dry), "one fluid read refuses the patch");
    }

    @Test
    void siteGroundIsTheMedianRead() {
        int[] heights = new int[25];
        Arrays.fill(heights, 64);
        heights[0] = 69;
        heights[1] = 60;
        assertEquals(64, SiteSearch.groundOf(heights));
    }

    @Test
    void spiralStaysInsideFortyEightBlocksNearestFirst() {
        List<int[]> spiral = SiteSearch.spiral(SiteSearch.RADIUS, 4);
        assertEquals(0, spiral.get(0)[0]);
        assertEquals(0, spiral.get(0)[1]);
        int last = 0;
        for (int[] o : spiral) {
            int d = o[0] * o[0] + o[1] * o[1];
            assertTrue(d <= 48 * 48);
            assertTrue(d >= last);
            last = d;
        }
    }

    @Test
    void waysNameTheNearestThingOnEachSide() {
        WaysPlan.Found farVillage = new WaysPlan.Found("village", 0, -400);
        WaysPlan.Found nearTower = new WaysPlan.Found("tower", 30, -200);
        WaysPlan.Found bar = new WaysPlan.Found("bar", 300, 10);
        WaysPlan.Found tooFar = new WaysPlan.Found("henge", -2100, 0);
        WaysPlan.Found[] sides = WaysPlan.assign(List.of(farVillage, nearTower, bar, tooFar));
        assertEquals("tower", sides[0].kind(), "north keeps the nearer of two");
        assertEquals("bar", sides[1].kind());
        assertNull(sides[2], "nothing south");
        assertNull(sides[3], "the henge is past 2,048 blocks");
        assertEquals(300, WaysPlan.roundedDistance(bar));
        WaysPlan.Found farBar = new WaysPlan.Found("bar", 0, 1834);
        assertEquals("bar", WaysPlan.assign(List.of(farBar))[2].kind(), "the farthest bar measured still shows");
        assertEquals(1830, WaysPlan.roundedDistance(farBar));
        assertEquals(0, WaysPlan.side(0, -1));
        assertEquals(1, WaysPlan.side(5, 5));
        assertEquals(2, WaysPlan.side(1, 5));
        assertEquals(3, WaysPlan.side(-5, 2));
    }

    @Test
    void mageListParsesTagFilesAndMatchesPicksOrLabels() {
        Set<String> list = MageList.parse(
                "{\"values\":[\"neoorigins:fire_mage\",{\"id\":\"neoorigins:necromancer\",\"required\":false}]}");
        assertEquals(Set.of("neoorigins:fire_mage", "neoorigins:necromancer"), list);
        assertTrue(MageList.isMage(list, List.of("neoorigins:human", "neoorigins:fire_mage"), Set.of()));
        assertTrue(MageList.isMage(list, List.of(), Set.of("cc_origin.necromancer")), "the Label's tag counts");
        assertFalse(MageList.isMage(list, List.of("neoorigins:human", "neoorigins:class_cleric"), Set.of("cc_origin.human")));
        assertEquals(8, MageList.DEFAULT.size());
    }

    @Test
    void ccRulesFromTheCards() {
        assertEquals(21, CcRules.footprint("hatching_ground"));
        assertEquals(CcRules.DEFAULT_FOOTPRINT, CcRules.footprint("end_bar"));
        assertEquals(64, CcRules.keepClear("boteco"));
        assertEquals(0, CcRules.keepClear("hatching_ground"));
        assertTrue(CcRules.full("boteco", 1), "a second boteco is refused");
        assertFalse(CcRules.full("cacareco", 1), "two Knick-Knacks may stand");
        assertTrue(CcRules.full("cacareco", 2));
        assertTrue(CcRules.validName("hunt_lodge"));
        assertFalse(CcRules.validName("../x"));
    }
}
