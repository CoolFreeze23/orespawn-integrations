package danger.orespawn.integrations.story.week;

import net.minecraft.server.MinecraftServer;

import java.util.HashMap;
import java.util.Map;

/**
 * The Almanac (system-almanac): the world's one daily die and the night's world-event slot.
 *
 * <p>This is the hand-off signature other groups compile against before the week group's branch is merged: a night
 * holds one overworld world event, and a second asking the same night is refused. The full roll, the SavedData record
 * and the priority list replace the body, never the signature.</p>
 */
public final class Almanac {

    private static final Map<Long, String> NIGHTS = new HashMap<>();

    private Almanac() {
    }

    /**
     * Asks for tonight's world-event slot for {@code eventKey}. True when the slot was free (it is now the caller's) or
     * already held by the same key; false when tonight already has another world event, so the caller doesn't start.
     */
    public static boolean claimNight(MinecraftServer server, String eventKey) {
        long night = server.overworld().getDayTime() / 24000L;
        String holder = NIGHTS.putIfAbsent(night, eventKey);
        return holder == null || holder.equals(eventKey);
    }
}
