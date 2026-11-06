package danger.orespawn.integrations.compat.alive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The Hoard Fall's numbers (event-hoard-fall, "Trigger and frequency"). */
class HoardFallRulesTest {

    @Test
    void theNightlyChanceIsTheShippedRate() {
        assertEquals(0.3935, HoardFallRules.NIGHT_CHANCE, 0.001);
        assertEquals(0.787, HoardFallRules.chance(true), 0.002);
        assertTrue(HoardFallRules.chance(true) <= HoardFallRules.CAP);
    }

    /** The draw is seeded: the same world and night always agree, and over many nights it falls about 39 % of them. */
    @Test
    void theSeededRollKeepsTheRate() {
        long seed = 1234567L;
        assertEquals(HoardFallRules.draw(seed, 42), HoardFallRules.draw(seed, 42));
        int falls = 0;
        int nights = 20000;
        for (long n = 0; n < nights; n++) {
            double d = HoardFallRules.draw(seed, n);
            assertTrue(d >= 0 && d < 1);
            if (HoardFallRules.falls(seed, n, false)) {
                falls++;
            }
        }
        assertEquals(0.3935, falls / (double) nights, 0.02);
    }

    /** A night and the morning after share one index, so a night slept through is still that night's Fall. */
    @Test
    void aSleptNightKeepsItsIndex() {
        long dusk = 5 * 24000L + 12000L;
        assertEquals(HoardFallRules.night(dusk), HoardFallRules.night(dusk + 11999));
        assertEquals(HoardFallRules.night(dusk), HoardFallRules.night(6 * 24000L + 100));
        assertEquals(HoardFallRules.night(dusk) + 1, HoardFallRules.night(6 * 24000L + 12000));
    }

    @Test
    void theQuarterIsAroundTheCentre() {
        assertEquals("north", HoardFallRules.quarter(10, -80));
        assertEquals("south", HoardFallRules.quarter(-5, 70));
        assertEquals("east", HoardFallRules.quarter(90, 3));
        assertEquals("west", HoardFallRules.quarter(-90, 3));
    }

    @Test
    void theBandFollowsTheRank() {
        assertEquals(1, HoardFallRules.band(0));
        assertEquals(2, HoardFallRules.band(1));
        assertEquals(4, HoardFallRules.band(3));
        assertEquals(5, HoardFallRules.band(9));
    }
}
