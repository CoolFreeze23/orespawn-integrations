package danger.orespawn.integrations.world.antechamber;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The ledge's height rule, its chunk-centre address, the signs' facing and the catch's trigger. Pure logic. */
class AntechamberRulesTest {

    @Test
    void ledgeYRule() {
        assertEquals(100, AntechamberRules.ledgeY(-1), "over the void: Y 100");
        assertEquals(100, AntechamberRules.ledgeY(60), "under an island lower than 99: still 100");
        assertEquals(100, AntechamberRules.ledgeY(99));
        assertEquals(131, AntechamberRules.ledgeY(130), "on top of an island's surface, never inside it");
        assertEquals(240, AntechamberRules.ledgeY(250), "capped at 240");
        assertEquals(240, AntechamberRules.ledgeY(239));
    }

    @Test
    void chunkCentre() {
        assertEquals(8, AntechamberRules.chunkCentre(0));
        assertEquals(8, AntechamberRules.chunkCentre(15));
        assertEquals(24, AntechamberRules.chunkCentre(16));
        assertEquals(-8, AntechamberRules.chunkCentre(-1));
        assertEquals(-8, AntechamberRules.chunkCentre(-16));
        // The whole 7x7 stays inside the centre's chunk.
        int c = AntechamberRules.chunkCentre(-37);
        assertEquals((c - AntechamberRules.HALF) >> 4, (c + AntechamberRules.HALF) >> 4);
    }

    @Test
    void signsFaceTheCentre() {
        // A sign due north of the centre (dz < 0) faces south: rotation 0.
        assertEquals(0, AntechamberRules.signRotation(0, -3));
        assertEquals(8, AntechamberRules.signRotation(0, 3));
        // The north-west corner faces south-east (rotation 14), the north-east south-west (2).
        assertEquals(14, AntechamberRules.signRotation(-3, -3));
        assertEquals(2, AntechamberRules.signRotation(3, -3));
        assertEquals(6, AntechamberRules.signRotation(3, 3));
    }

    @Test
    void catchOnlyWhenFallingFourBelow() {
        assertFalse(AntechamberRules.shouldCatch(98.0, 100, true));
        assertTrue(AntechamberRules.shouldCatch(96.0, 100, true));
        assertFalse(AntechamberRules.shouldCatch(50.0, 100, false), "standing on an island below is no fall");
    }
}
