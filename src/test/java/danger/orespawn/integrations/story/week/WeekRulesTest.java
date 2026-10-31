package danger.orespawn.integrations.story.week;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

/** The Land's Week, the Misfile roll and the Almanac's die as pure logic (system-lands-week's test line). */
class WeekRulesTest {

    /** Steps the dawn check the way the 20-tick clock does: the Land's day for a sequence of day times. */
    private static long run(long start, long... dayTimes) {
        long landDay = WeekRules.dayIndex(start);
        long last = landDay;
        for (long t : dayTimes) {
            long index = WeekRules.dayIndex(t);
            landDay = WeekRules.advance(landDay, last, index);
            last = index;
        }
        return landDay;
    }

    @Test
    void dayZeroIsMonday() {
        assertEquals(0, WeekRules.weekday(0));
        assertEquals(LandsWeek.MONDAY, WeekRules.sundayFirst(0));
        assertEquals(6, WeekRules.weekday(6));
        assertEquals(LandsWeek.SUNDAY, WeekRules.sundayFirst(6));
        assertEquals(LandsWeek.SATURDAY, WeekRules.sundayFirst(5));
    }

    @Test
    void fourteenDawnsRiseByOneEach() {
        long landDay = 0;
        long last = 0;
        for (int day = 1; day <= 14; day++) {
            long index = WeekRules.dayIndex(day * WeekRules.DAY + 10);
            long next = WeekRules.advance(landDay, last, index);
            assertEquals(landDay + 1, next, "dawn " + day);
            assertEquals((int) (next % 7), WeekRules.weekday(next));
            landDay = next;
            last = index;
        }
        assertEquals(14, landDay);
        assertEquals(0, WeekRules.weekday(landDay), "day 14 is a Monday again");
    }

    @Test
    void timeSetDayOnDayTwentyMakesDayTwentyOneNotMonday() {
        long noonOfDay20 = 20 * WeekRules.DAY + 6000;
        long afterTimeSetDay = 1000; // /time set day: the day time is 1000, time query day reads 0
        long landDay = run(noonOfDay20, afterTimeSetDay);
        // The card's line reads "day 21, not Monday": the point is that the week is not reset to day 0. Day 21 itself
        // falls on a Monday (21 % 7 == 0), so the check is on the day count, not on the weekday.
        assertEquals(21, landDay);
        assertNotEquals(0, landDay, "not reset to day 0");
        // The next natural dawn after it is day 22, never a rewind.
        assertEquals(22, run(noonOfDay20, afterTimeSetDay, WeekRules.DAY + 5));
    }

    @Test
    void startsAtTheHubsDayCount() {
        assertEquals(123, run(123 * WeekRules.DAY + 4000));
        assertEquals(124, run(123 * WeekRules.DAY + 4000, 124 * WeekRules.DAY));
    }

    @Test
    void timeAddCountsOneDay() {
        assertEquals(11, run(10 * WeekRules.DAY + 100, 11 * WeekRules.DAY + 100));
        assertEquals(10, run(10 * WeekRules.DAY + 100, 10 * WeekRules.DAY + 23000), "same day, no dawn");
    }

    @Test
    void duskWindowAndHandoverSlide() {
        assertTrue(WeekRules.inDusk(12000));
        assertFalse(WeekRules.inDusk(6000));
        assertFalse(WeekRules.inDusk(18000));
        assertFalse(WeekRules.clearOfHandover(true, 1000, 1000 - 599));
        assertTrue(WeekRules.clearOfHandover(true, 1000, 1000 - 600));
        assertTrue(WeekRules.clearOfHandover(false, 0, 0), "a closed ticket never holds the clock");
        assertTrue(WeekRules.canopyHeld(true, 500, 400));
        assertFalse(WeekRules.canopyHeld(true, 700, 400));
    }

    @Test
    void firstWeekEndsAtTheEighthDawn() {
        assertTrue(WeekRules.inFirstWeek(10, 10));
        assertTrue(WeekRules.inFirstWeek(10, 17));
        assertFalse(WeekRules.inFirstWeek(10, 18));
        assertFalse(WeekRules.inFirstWeek(-1, 3));
    }

    @Test
    void misfileTuesdayEightSundayNine() {
        Random r = new Random(1);
        for (long day = 0; day < 70; day++) {
            int row = MisfileRules.row(day, 0, r::nextInt);
            if (WeekRules.weekday(day) == 1) {
                assertEquals(8, row);
            } else if (WeekRules.weekday(day) == 6) {
                assertEquals(9, row);
            } else {
                assertTrue(row == 1 || row == 3 || (row == 10 && day >= 7), "day " + day + " row " + row);
            }
        }
        assertEquals(1, MisfileRules.row(9, 10, b -> 0), "the day after Everything in Its Place");
        assertEquals(3, MisfileRules.row(2, 0, b -> b == 40 ? 0 : 1), "no row 10 before day 7");
    }

    @Test
    void almanacStreamsAreStablePerSaltAndDay() {
        long seed = 42L;
        assertEquals(AlmanacRules.mix(seed, 5, 1), AlmanacRules.mix(seed, 5, 1));
        Map<Long, Integer> seen = new HashMap<>();
        for (int salt = 1; salt <= 7; salt++) {
            long m = AlmanacRules.mix(seed, 5, salt);
            assertFalse(seen.containsKey(m), "salt " + salt + " shares a stream");
            seen.put(m, salt);
        }
        assertNotEquals(AlmanacRules.mix(seed, 5, 1), AlmanacRules.mix(seed, 6, 1));
    }

    @Test
    void almanacFixturesAndPlaces() {
        assertEquals("event-mula-trail", AlmanacRules.fixture(3));
        assertEquals("event-fado-do-lobisomem", AlmanacRules.fixture(4));
        assertEquals(null, AlmanacRules.fixture(6));
        assertTrue(AlmanacRules.outranks(AlmanacRules.place("npc-mapinguari"), AlmanacRules.place("event-hoard-fall")));
        assertFalse(AlmanacRules.outranks(AlmanacRules.place("event-hoard-fall"), AlmanacRules.place("event-mula-trail")));
        assertTrue(AlmanacRules.serverRoom(0, 0));
        assertFalse(AlmanacRules.serverRoom(1, AlmanacRules.SPACING_TICKS - 1));
        assertTrue(AlmanacRules.serverRoom(1, AlmanacRules.SPACING_TICKS));
        assertFalse(AlmanacRules.serverRoom(2, Long.MAX_VALUE));
    }

    @Test
    void weekStateRoundTripsAndReadsShortRecords() {
        WeekState s = new WeekState();
        s.lineDay = 3;
        s.weekStart = 1;
        s.setFlag(WeekState.FLAG_FIRST_CRY);
        WeekState back = WeekState.fromInts(s.toInts());
        assertEquals(3, back.lineDay);
        assertEquals(1, back.weekStart);
        assertTrue(back.flag(WeekState.FLAG_FIRST_CRY));
        WeekState shortRecord = WeekState.fromInts(new int[]{5});
        assertEquals(5, shortRecord.lineDay);
        assertEquals(WeekState.UNSET, shortRecord.duskDay);
        assertEquals(0, shortRecord.flags);
    }
}
