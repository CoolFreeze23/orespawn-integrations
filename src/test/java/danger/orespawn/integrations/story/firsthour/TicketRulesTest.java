package danger.orespawn.integrations.story.firsthour;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import org.junit.jupiter.api.Test;

/**
 * The Priority Ticket's timing rules as pure logic: a simulated player is driven second by second through
 * {@link TicketRules}, the way the 20-tick clock drives it on a server.
 */
class TicketRulesTest {

    private static final int SEC = 20;

    /** One handover: the step and the online second it happened. */
    private record Handover(int step, int second) {}

    /** A simulated join: a scrap is held from the region's first visit, the Fall lands at once. */
    private static final class Sim {
        final TicketState s = TicketState.fresh();
        final List<Handover> log = new ArrayList<>();
        boolean scrap = true;
        boolean falling;
        boolean letterOn = true;
        boolean fallOn = true;
        boolean squire;
        boolean ticketOn = true;
        int originCloseAt = 0;
        int fallFailsUntil = -1;
        int wallSecond;

        TicketRules.Conditions conditions() {
            return new TicketRules.Conditions(ticketOn, wallSecond >= originCloseAt, falling, scrap,
                    letterOn, fallOn, squire, true, false);
        }

        /** Runs {@code seconds} seconds; {@code each} may react (use papers, take damage) after every second. */
        void run(int seconds, BiConsumer<Sim, Integer> each) {
            for (int i = 0; i < seconds; i++) {
                wallSecond++;
                TicketRules.tick(s, SEC);
                int next = TicketRules.decide(s, conditions());
                if (next == TicketRules.CLOSE) {
                    TicketRules.close(s);
                    log.add(new Handover(TicketRules.CLOSE, s.onlineTicks / SEC));
                } else if (next != TicketRules.NONE) {
                    if (next == 5 && s.onlineTicks / SEC < fallFailsUntil) {
                        continue; // the Fall found no spot: held
                    }
                    TicketRules.recordHandover(s, next);
                    if (next == 5) {
                        s.setFlag(TicketState.FLAG_FALL_LANDED);
                    }
                    log.add(new Handover(next, s.onlineTicks / SEC));
                }
                if (each != null) {
                    each.accept(this, next);
                }
            }
        }

        int at(int step) {
            for (Handover h : log) {
                if (h.step() == step) {
                    return h.second();
                }
            }
            return -1;
        }
    }

    private static void assertGaps(Sim sim) {
        Handover prev = null;
        for (Handover h : sim.log) {
            if (h.step() < 2 || h.step() > 10) {
                continue;
            }
            if (prev != null) {
                assertTrue(h.second() - prev.second() >= 90,
                        "P" + h.step() + " came " + (h.second() - prev.second()) + " s after P" + prev.step());
            }
            prev = h;
        }
    }

    @Test
    void originScreenStartsTheClockAndHandsP01() {
        Sim sim = new Sim();
        sim.originCloseAt = 40; // lingers 40 s on the origin screen
        sim.run(41, null);
        assertEquals(0, sim.at(1), "P01 comes as the origin screen closes, at ticket second 0");
        assertTrue(sim.s.used(TicketState.P00));
        assertEquals(20, sim.s.onlineTicks, "the clock starts at the close");
    }

    @Test
    void ignorerGetsP01ToP10ByMinute45() {
        Sim sim = new Sim();
        sim.run(46 * 60, null);
        for (int p = 1; p <= 10; p++) {
            int t = sim.at(p);
            assertTrue(t >= 0 && t <= 45 * 60, "ignorer P" + p + " at " + t);
        }
        assertGaps(sim);
        int letter = sim.at(4);
        assertTrue(letter >= 120 && letter <= 300 + 90, "letter at " + letter);
    }

    @Test
    void rusherMeetsThePrinceAtMinuteTwo() {
        Sim sim = new Sim();
        sim.run(130, (x, step) -> {
            // Uses everything the moment it can: the Eggshell, the casket and a field Correction early.
            x.s.markUsed(1);
            TicketRules.markUsed(x.s, 2);
            TicketRules.markUsed(x.s, 3);
        });
        assertEquals(-1, sim.at(2), "the casket opened early is passed with no number");
        assertEquals(-1, sim.at(3), "the Correction done early is passed with no number");
        assertEquals(120, sim.at(4), "the Letter at 120 s online");
    }

    @Test
    void readerUsingEachPaperGetsTheGapOnly() {
        Sim sim = new Sim();
        sim.run(20 * 60, (x, step) -> {
            if (step >= 1 && step <= 10) {
                TicketRules.markUsed(x.s, step);
                x.s.markUsed(step);
            }
        });
        assertEquals(90, sim.at(2));
        assertEquals(180, sim.at(3));
        assertEquals(270, sim.at(4));
        assertEquals(360, sim.at(5));
        assertGaps(sim);
    }

    @Test
    void letterIsForcedAt300sButNeverInsideTheGap() {
        Sim sim = new Sim();
        sim.scrap = false; // no scrap: P03 waits, and the Letter is not held behind it
        sim.run(400, null);
        int letter = sim.at(4);
        assertTrue(letter >= 300 && letter <= 390, "forced letter at " + letter);
        assertTrue(letter - sim.at(2) >= 90);
        sim.scrap = true;
        sim.run(400, null);
        assertTrue(sim.at(3) > letter, "P03 comes once a scrap is held");
        assertGaps(sim);
    }

    /**
     * "a new account hit every 5 s by a zombie at night gets its Fall 60-120 s after P05 comes due": the damage hold
     * keeps a newcomer's own Fall back for 60 s at most; the other steps keep the plain 10 s hold.
     */
    @Test
    void aNewcomerUnderAttackGetsTheFallWithinAMinute() {
        Sim sim = new Sim();
        sim.run(60 * 15, (x, next) -> {
            if (x.s.handed(4) && !x.s.handed(5) && (x.s.onlineTicks / SEC) % 5 == 0) {
                x.s.lastDamage = x.s.onlineTicks; // a zombie's hit every 5 s once the Letter is in
            }
        });
        int letter = sim.at(4);
        int fall = sim.at(5);
        assertTrue(fall > 0, "the Fall came under attack (P04 at " + letter + ")");
        int due = letter + 180; // the Summons is not opened here, so P05 comes due at P04's 180 s fallback
        assertTrue(fall - due >= 60 && fall - due <= 70,
                "held a minute past due, then handed: P04 " + letter + ", P05 " + fall);
    }

    @Test
    void damageAndFallsHoldTheNextHandover() {
        Sim sim = new Sim();
        sim.run(176, null);
        int hit = sim.s.onlineTicks / SEC; // hit at 175 s, right before P02's 180 s fallback
        sim.s.lastDamage = sim.s.onlineTicks;
        sim.run(15, null);
        assertTrue(sim.at(2) >= hit + 10, "no handover within 10 s of damage: " + sim.at(2));
        Sim faller = new Sim();
        faller.run(170, null);
        faller.falling = true;
        faller.run(30, null);
        assertEquals(-1, faller.at(2), "no handover during a fall");
        faller.falling = false;
        faller.run(1, null);
        assertTrue(faller.at(2) > 0);
    }

    @Test
    void heldFallIssuesNothingLater() {
        Sim sim = new Sim();
        sim.fallFailsUntil = 20 * 60; // a boat on open ocean: no spot for 20 minutes
        sim.run(20 * 60 - 1, null);
        assertEquals(-1, sim.at(5));
        assertEquals(-1, sim.at(6));
        assertEquals(-1, sim.at(7));
        sim.run(5, null);
        assertTrue(sim.at(5) >= 20 * 60);
    }

    @Test
    void deskReachedEarlyIsServedOutOfTurn() {
        Sim sim = new Sim();
        sim.run(5 * 60, null);
        sim.s.setFlag(TicketState.FLAG_REGISTRY_REACHED);
        sim.run(91, null);
        int p09 = sim.at(9);
        assertTrue(p09 > 0 && p09 < 7 * 60, "P09 at " + p09);
        assertTrue(sim.s.flag(TicketState.FLAG_REGISTRY_OUT_OF_TURN));
        sim.run(40 * 60, null);
        assertTrue(sim.at(5) > p09, "the Fall still comes after an out-of-turn visit");
    }

    @Test
    void queueClosesAtSixtyMinutesOrSquire() {
        Sim sim = new Sim();
        sim.run(61 * 60, null);
        assertEquals(TicketState.CLOSED, sim.s.step);
        assertEquals(60 * 60, sim.at(TicketRules.CLOSE));
        Sim squire = new Sim();
        squire.run(100, null);
        squire.squire = true;
        squire.run(1, null);
        assertEquals(TicketState.CLOSED, squire.s.step);
    }

    @Test
    void ticketOffKeepsTheOldTimers() {
        Sim sim = new Sim();
        sim.ticketOn = false;
        sim.run(10 * 60, null);
        assertEquals(-1, sim.at(2));
        assertEquals(120, sim.at(4));
        assertEquals(300, sim.at(5));
    }

    @Test
    void veteranGetsLetterThenDaylightFallThenOdete() {
        TicketState s = TicketState.closed();
        s.step = TicketState.VET_LETTER;
        s.lastHandover = -TicketRules.GAP + 30 * SEC;
        TicketRules.Conditions night = new TicketRules.Conditions(true, true, false, true, true, true,
                false, false, false);
        TicketRules.Conditions day = new TicketRules.Conditions(true, true, false, true, true, true,
                false, true, false);
        int drop = -1;
        for (int i = 0; i < 60 && drop < 0; i++) {
            TicketRules.tick(s, SEC);
            if (TicketRules.decide(s, night) == TicketState.VET_LETTER) {
                TicketRules.recordHandover(s, TicketState.VET_LETTER);
                drop = s.onlineTicks;
            }
        }
        assertEquals(30 * SEC, drop, "one Summons drop 30 s after login");
        s.setFlag(TicketState.FLAG_SUMMONS_OPENED);
        for (int i = 0; i < 300; i++) {
            TicketRules.tick(s, SEC);
            assertEquals(TicketRules.NONE, TicketRules.decide(s, night), "a veteran's Fall waits for daylight");
        }
        TicketRules.tick(s, SEC);
        assertEquals(TicketState.VET_FALL, TicketRules.decide(s, day));
        TicketRules.recordHandover(s, TicketState.VET_FALL);
        assertEquals(TicketState.VET_ODETE, s.step);
        TicketRules.markUsed(s, 5);
        s.onlineTicks += TicketRules.GAP;
        assertEquals(TicketState.VET_ODETE, TicketRules.decide(s, day));
        TicketRules.recordHandover(s, TicketState.VET_ODETE);
        assertEquals(TicketState.CLOSED, s.step);
    }

    @Test
    void stateRoundTripsInTwentyFourBytes() {
        TicketState s = TicketState.fresh();
        s.markUsed(0);
        s.step = 7;
        s.pin = TicketState.PIN_REGISTRY;
        s.lastHandover = 12345;
        s.onlineTicks = 23456;
        s.pinX = -100;
        s.pinZ = 2000;
        s.setFlag(TicketState.FLAG_DUSK_DONE);
        int[] ints = s.toInts();
        assertEquals(6, ints.length);
        TicketState back = TicketState.fromInts(ints);
        assertEquals(s.masks, back.masks);
        assertEquals(7, back.step);
        assertEquals(TicketState.PIN_REGISTRY, back.pin);
        assertEquals(-100, back.pinX);
        assertEquals(2000, back.pinZ);
        assertFalse(back.flag(TicketState.FLAG_VETERAN));
    }

    @Test
    void sceneCapAllowsFourLiveSixtySecondsApart() {
        SceneCap cap = new SceneCap();
        long t = 0;
        long[] ids = new long[5];
        for (int i = 0; i < 4; i++) {
            ids[i] = cap.start(t);
            assertTrue(ids[i] != 0, "scene " + i + " starts");
            assertEquals(0, cap.start(t + 1199), "never two inside 60 s");
            t += 1200;
        }
        assertEquals(0, cap.start(t), "a fifth waits while four are live");
        cap.end(ids[0]);
        assertTrue(cap.start(t) != 0, "the fifth starts when the first ends");
        assertEquals(4, cap.liveCount(t));
        assertEquals(0, cap.liveCount(t + SceneCap.MAX_LIFE + 1));
    }
}
