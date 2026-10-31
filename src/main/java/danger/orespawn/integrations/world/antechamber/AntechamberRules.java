package danger.orespawn.integrations.world.antechamber;

/**
 * The Antechamber's numbers (structure-chaos-antechamber), pure and unit tested: the ledge height, the chunk-centre
 * address, the sign corners and the catch's trigger.
 */
public final class AntechamberRules {

    public static final int MIN_Y = 100;
    public static final int MAX_Y = 240;
    public static final int HALF = 3;
    public static final int PORTAL_COOLDOWN = 80;
    /** The catch fires once the player is falling 4 or more blocks below the ledge. */
    public static final int CATCH_DROP = 4;
    /** The catch sets the player this far above the island top they land over. */
    public static final int CATCH_ABOVE = 2;
    /** The perch forms when nothing stands within this many blocks under a ranked arrival. */
    public static final int VOID_DEPTH = 64;
    public static final int SLOW_FALLING_TICKS = 200;

    /** Sign corners as (dx, dz) from the centre: sign 1 and 2 on the facing (north) row, sign 3 at the far edge. */
    public static final int[][] SIGNS = {{-HALF, -HALF}, {HALF, -HALF}, {HALF, HALF}};

    private AntechamberRules() {
    }

    /**
     * The ledge's Y: max(100, the MOTION_BLOCKING height + 1), capped at 240. {@code topBlockY} is the heightmap's
     * top block (a chunk's {@code getHeight}), so a ledge over an island sits right on its surface and one over the
     * void at 100.
     */
    public static int ledgeY(int topBlockY) {
        return Math.min(MAX_Y, Math.max(MIN_Y, topBlockY + 1));
    }

    /** The centre block coordinate of the chunk holding block coordinate {@code blockCoord}. */
    public static int chunkCentre(int blockCoord) {
        return (blockCoord >> 4 << 4) + 8;
    }

    /** True when an armed player has fallen far enough below the ledge for the moths to catch them. */
    public static boolean shouldCatch(double playerY, int ledgeY, boolean falling) {
        return falling && playerY <= ledgeY - CATCH_DROP;
    }

    /** The sign rotation (0-15) that turns a standing sign at (dx, dz) from the centre to face the centre. */
    public static int signRotation(int dx, int dz) {
        // A player at the centre looking at the sign has this yaw; a sign placed by them faces back at them.
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        return Math.floorMod(Math.round((float) ((yaw + 180.0) / 22.5)), 16);
    }
}
