package danger.orespawn.integrations.command;

import java.util.Map;

/**
 * The hub placements' rules from the cards (system-cronograma-de-obras, the hub's checklist), as pure logic so they
 * can be unit tested: how wide each thing's footprint is (every chunk of it must be loaded), how far it must keep from
 * a bed, a chest or a Parish Stone where its card names a distance, and how many may stand.
 */
public final class CcRules {

    /** The widest template a card allows (17-assets.md): the fallback footprint for a thing not listed. */
    public static final int DEFAULT_FOOTPRINT = 48;

    private static final Map<String, Integer> FOOTPRINT = Map.of(
            "hatching_ground", 21,
            "boteco", 24,
            "hunt_lodge", 32,
            "lost_cathedral", 48,
            "offering_stone", 8,
            "cacareco", 48);

    /** Distances from the cards: the boteco "never within 64 blocks of a bed, chest or Parish Stone"; the Lodge
     *  "128 or more from any base"; the Lost Cathedral "256 or more from any base". */
    private static final Map<String, Integer> KEEP_CLEAR = Map.of(
            "boteco", 64,
            "hunt_lodge", 128,
            "lost_cathedral", 256);

    /** Two Knick-Knacks at most on the hub (checklist step 7); every other thing once. */
    private static final Map<String, Integer> MAX_PLACES = Map.of("cacareco", 2);

    private CcRules() {
    }

    public static int footprint(String thing) {
        return FOOTPRINT.getOrDefault(thing, DEFAULT_FOOTPRINT);
    }

    /** Blocks to keep from beds, chests and Parish Stones, or 0 when the card names none. */
    public static int keepClear(String thing) {
        return KEEP_CLEAR.getOrDefault(thing, 0);
    }

    public static int maxPlaces(String thing) {
        return MAX_PLACES.getOrDefault(thing, 1);
    }

    /** True when a thing already placed {@code placed} times may not be placed again. */
    public static boolean full(String thing, int placed) {
        return placed >= maxPlaces(thing);
    }

    /** A thing name usable in a function path: lower-case letters, digits and underscores. */
    public static boolean validName(String thing) {
        return thing != null && !thing.isEmpty() && thing.matches("[a-z0-9_]+");
    }
}
