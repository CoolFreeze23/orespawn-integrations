package danger.orespawn.integrations.world.regions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Titles keep vanilla's size when they fit and shrink just enough when they don't. Pure logic; no game classes. */
class TitleFitTest {

    /** A 1920x1200 handheld at the auto GUI scale, the case that overflowed. */
    private static final int LEGION = 384;

    @Test
    void aLineThatFitsKeepsItsSize() {
        assertEquals(4.0F, TitleFit.scale(4.0F, 72, LEGION));   // "The Greenwood"
        assertEquals(2.0F, TitleFit.scale(2.0F, 184, LEGION));  // exactly the room: 368
    }

    @Test
    void theGreenwoodSubtitleShrinksToFit() {
        float s = TitleFit.scale(2.0F, 250, LEGION);
        assertTrue(s < 2.0F, "shrinks");
        assertTrue(250 * s <= LEGION - 2 * TitleFit.MARGIN + 0.01F, "fits between the margins");
        assertTrue(s > 1.4F, "only as much as it needs");
    }

    @Test
    void aWideTitleShrinksToo() {
        float s = TitleFit.scale(4.0F, 144, LEGION);  // "A Masmorra do Esquecimento"
        assertTrue(144 * s <= LEGION - 2 * TitleFit.MARGIN + 0.01F);
    }

    @Test
    void degenerateInputsKeepTheBase() {
        assertEquals(2.0F, TitleFit.scale(2.0F, 0, LEGION));
        assertEquals(4.0F, TitleFit.scale(4.0F, 100, 10));
    }
}
