package danger.orespawn.integrations.world.regions;

/**
 * How large an on-screen title may draw (pure logic, unit tested). Vanilla draws titles at 4x and subtitles at 2x whatever
 * their length, so a long line runs off both edges of a small GUI: at the auto GUI scale a 1920x1200 handheld is 384
 * scaled pixels wide. A line that fits keeps its size; a longer one shrinks just enough to fit, with a margin each side.
 */
public final class TitleFit {

    /** Scaled pixels kept clear at each screen edge. */
    public static final int MARGIN = 8;

    private TitleFit() {
    }

    public static float scale(float base, int textWidth, int screenWidth) {
        int room = screenWidth - 2 * MARGIN;
        if (textWidth <= 0 || room <= 0 || textWidth * base <= room) {
            return base;
        }
        return (float) room / textWidth;
    }
}
