package danger.orespawn.integrations.compat.alive;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FallSafetyWindowTest {

    @Test
    void aHomeCountsFromSixteenUnderToThirtyTwoOverTheSurface() {
        int surface = 100;
        assertTrue(FallSafety.inSurfaceWindow(84, surface), "16 under the surface counts");
        assertFalse(FallSafety.inSurfaceWindow(83, surface), "17 under does not");
        assertFalse(FallSafety.inSurfaceWindow(60, surface), "a dungeon 40 down does not");
        assertTrue(FallSafety.inSurfaceWindow(132, surface), "32 over counts");
        assertFalse(FallSafety.inSurfaceWindow(133, surface), "33 over does not");
        assertFalse(FallSafety.inSurfaceWindow(175, surface), "a sky ship does not");
    }
}
