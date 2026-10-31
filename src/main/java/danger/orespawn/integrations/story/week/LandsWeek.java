package danger.orespawn.integrations.story.week;

import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The Land's Week (system-lands-week): the Land's day and the one dawn job every weekday card hooks.
 *
 * <p>This is the hand-off signature other groups compile against before the week group's branch is merged. It reads
 * the overworld's own day index and keeps the dawn hooks in a list; the full dawn check, the Land's day counter in
 * SavedData and the weekday lines replace the bodies, never the signatures.</p>
 */
public final class LandsWeek {

    public static final int SUNDAY = 0;
    public static final int MONDAY = 1;
    public static final int TUESDAY = 2;
    public static final int WEDNESDAY = 3;
    public static final int THURSDAY = 4;
    public static final int FRIDAY = 5;
    public static final int SATURDAY = 6;

    private record Hook(String key, Consumer<MinecraftServer> job) {}

    private static final List<Hook> HOOKS = new ArrayList<>();

    private LandsWeek() {
    }

    /** Today's weekday on the Land's Week, 0-6 with Sunday = 0. Day 0 of a world is a Monday. */
    public static int today(MinecraftServer server) {
        long day = server.overworld().getDayTime() / 24000L;
        return (int) ((day % 7 + 1) % 7);
    }

    /**
     * Registers {@code job} to run once at each overworld dawn, after the Land's day has moved on, in registration
     * order. Register once, from a system's {@code init}.
     */
    public static void onDawn(String key, Consumer<MinecraftServer> job) {
        HOOKS.add(new Hook(key, job));
    }
}
