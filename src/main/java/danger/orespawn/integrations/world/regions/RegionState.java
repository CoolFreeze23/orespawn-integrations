package danger.orespawn.integrations.world.regions;

/**
 * The 4-byte {@code region_state} record: byte 0 the region, depth or dimension the player was last shown, byte 1 the
 * hold counter in 20-tick steps, bytes 2-3 the 14-bit first-visit mask (bit L-1 for region L). Packed into one int so
 * the attachment is a single Codec.INT. The candidate region of a hold in progress is transient (kept by the
 * handler, never saved): after a restart the hold simply starts again. Pure logic, unit tested.
 */
public final class RegionState {

    /** 10 s on the 20-tick timer. */
    public static final int HOLD_STEPS = 10;
    public static final int MASK_BITS = 0x3FFF;

    private RegionState() {
    }

    public static int last(int state) {
        return state & 0xFF;
    }

    public static int hold(int state) {
        return (state >>> 8) & 0xFF;
    }

    public static int mask(int state) {
        return (state >>> 16) & MASK_BITS;
    }

    public static int pack(int last, int hold, int mask) {
        return (last & 0xFF) | ((Math.min(hold, 0xFF) & 0xFF) << 8) | ((mask & MASK_BITS) << 16);
    }

    /** True once region {@code l} (1-14) paid its first-visit scrap. */
    public static boolean visited(int state, int l) {
        return l >= 1 && l <= 14 && (mask(state) & (1 << (l - 1))) != 0;
    }

    public static int markVisited(int state, int l) {
        if (l < 1 || l > 14) {
            return state;
        }
        return pack(last(state), hold(state), mask(state) | (1 << (l - 1)));
    }

    /** One step's outcome: the new state, the new transient candidate, and whether a crossing completed. */
    public record Step(int state, int candidate, boolean crossed) {}

    /**
     * One 20-tick step with the player standing in {@code target}. Standing where they were last shown resets the
     * hold; standing somewhere new counts, and the tenth step in a row on the same new place is a crossing. A border
     * walked back and forth never completes a hold, so it never flickers.
     */
    public static Step step(int state, int candidate, int target) {
        int last = last(state);
        int mask = mask(state);
        if (target == last) {
            return new Step(pack(last, 0, mask), 0, false);
        }
        int hold = target == candidate ? hold(state) + 1 : 1;
        if (hold >= HOLD_STEPS) {
            return new Step(pack(target, 0, mask), 0, true);
        }
        return new Step(pack(last, hold, mask), target, false);
    }

    /** Sets the shown place at once (a dimension change), with no hold. */
    public static int jump(int state, int target) {
        return pack(target, 0, mask(state));
    }
}
