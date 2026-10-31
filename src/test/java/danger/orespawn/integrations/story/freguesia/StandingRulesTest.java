package danger.orespawn.integrations.story.freguesia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import danger.orespawn.integrations.story.freguesia.StandingRules.Candidate;
import danger.orespawn.integrations.story.freguesia.StandingRules.Missing;
import danger.orespawn.integrations.story.freguesia.StandingRules.Numbers;

import java.util.List;

import org.junit.jupiter.api.Test;

/** The Freguesia's arithmetic, against the card's test lines (system-freguesia, wonder-wednesday-market). */
class StandingRulesTest {

    private static final Numbers N = Numbers.DEFAULT;

    @Test
    void thresholdsAndBuilderKeysAreTheCards() {
        assertEquals(60, StandingRules.threshold(1, N));
        assertEquals(150, StandingRules.threshold(2, N));
        assertEquals(300, StandingRules.threshold(3, N));
        assertEquals(600, StandingRules.threshold(4, N));
        assertEquals(450, StandingRules.builderKey(3, N));
        assertEquals(900, StandingRules.builderKey(4, N));
        assertEquals(7, StandingRules.daysNeeded(2));
        assertEquals(14, StandingRules.daysNeeded(3));
        assertEquals(28, StandingRules.daysNeeded(4));
    }

    @Test
    void configuredNumbersReplaceTheDefaults() {
        Numbers n = Numbers.of(List.of(10, 20, 30, 40), List.of(45, 90));
        assertEquals(30, StandingRules.threshold(3, n));
        assertEquals(90, StandingRules.builderKey(4, n));
    }

    @Test
    void homesteadNeedsOnlyStanding() {
        assertEquals(Missing.STANDING, StandingRules.missing(0, 59, 0, false, false, false, N));
        assertEquals(Missing.NONE, StandingRules.missing(0, 60, 0, false, false, false, N));
    }

    @Test
    void hamletNeedsDaysAndADeed() {
        assertEquals(Missing.DAYS, StandingRules.missing(1, 150, 6, true, false, false, N));
        assertEquals(Missing.DEED, StandingRules.missing(1, 150, 7, false, false, false, N));
        assertEquals(Missing.NONE, StandingRules.missing(1, 150, 7, true, false, false, N));
    }

    /** At 300 standing and 14 stone days with no boss kill stage 3 waits on its key; at 450 it arrives with no kill. */
    @Test
    void parishWaitsForAKeyAndTheBuilderKeyOpensIt() {
        assertEquals(Missing.KEY, StandingRules.missing(2, 300, 14, true, false, false, N));
        assertEquals(Missing.NONE, StandingRules.missing(2, 300, 14, true, true, false, N));
        assertEquals(Missing.NONE, StandingRules.missing(2, 450, 14, true, false, false, N));
        assertEquals(Missing.DAYS, StandingRules.missing(2, 450, 13, true, false, false, N));
    }

    /** At 600 and 28 days with no Knight the Town waits; 900 brings it. */
    @Test
    void townWaitsForAKnightOrNineHundred() {
        assertEquals(Missing.KEY, StandingRules.missing(3, 600, 28, true, true, false, N));
        assertEquals(Missing.NONE, StandingRules.missing(3, 600, 28, true, false, true, N));
        assertEquals(Missing.NONE, StandingRules.missing(3, 900, 28, true, false, false, N));
        assertEquals(Missing.TOP, StandingRules.missing(4, 2000, 99, true, true, true, N));
    }

    /** A solo builder stops at 200 a day; two signers count 1.5x inside the same 200. */
    @Test
    void theDailyCapAndTheMutirao() {
        int today = 0;
        int placed = 0;
        for (int i = 0; i < 300; i++) {
            int h = StandingRules.capped(today, StandingRules.placementHalves(1));
            today += h;
            if (h > 0) {
                placed++;
            }
        }
        assertEquals(400, today);
        assertEquals(200, placed);
        assertEquals(3, StandingRules.placementHalves(2));
        assertEquals(2, StandingRules.placementHalves(1));
        assertEquals(1, StandingRules.capped(399, 3));
        assertEquals(0, StandingRules.capped(400, 2));
    }

    @Test
    void theStoneGrace() {
        assertTrue(StandingRules.withinGrace(10, 40, 30));
        assertFalse(StandingRules.withinGrace(10, 41, 30));
        assertFalse(StandingRules.withinGrace(-1, 5, 30));
    }

    /** With two stones the one that gained more since last Wednesday wins; a tie goes to the lower total. */
    @Test
    void theWeeksBestKept() {
        assertEquals(2, StandingRules.pick(List.of(new Candidate(1, 30, 500, 0), new Candidate(2, 40, 100, 0))));
        assertEquals(1, StandingRules.pick(List.of(new Candidate(1, 40, 90, 0), new Candidate(2, 40, 100, 0))));
        assertEquals(-1, StandingRules.pick(List.of(new Candidate(1, 0, 900, 0))));
    }

    /** A stone picked two Wednesdays running yields to the other; alone, it keeps the visit. */
    @Test
    void twoVisitsRunningYield() {
        assertEquals(2, StandingRules.pick(List.of(new Candidate(1, 90, 500, 3), new Candidate(2, 10, 100, 1))));
        assertEquals(1, StandingRules.pick(List.of(new Candidate(1, 90, 500, 3))));
        assertEquals(1, StandingRules.pick(List.of(new Candidate(1, 90, 500, 1), new Candidate(2, 10, 100, 0))));
        int v = StandingRules.shiftVisits(0, true);
        v = StandingRules.shiftVisits(v, true);
        assertEquals(3, v);
        assertEquals(2, StandingRules.shiftVisits(v, false));
    }

    @Test
    void aSevenBySevenPlotRefusesTheTentAndEightByEightTakesIt() {
        assertFalse(Plots.takesTent(7, 7));
        assertFalse(Plots.takesTent(8, 7));
        assertTrue(Plots.takesTent(8, 8));
    }
}
