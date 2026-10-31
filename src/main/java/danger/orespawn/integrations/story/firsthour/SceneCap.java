package danger.orespawn.integrations.story.firsthour;

import java.util.ArrayList;
import java.util.List;

/**
 * The first hour's scene cap, server-wide: at most four scenes live at once (a forced flyover, a First Fall, a Field
 * Odete), and never two starting within 60 s, so four friends joining together each get their own first ten minutes
 * and a fifth waits for the first to end. Scenes are not world events: they never take an Almanac slot. A scene that
 * is never ended (a crate nobody opens) stops counting after {@link #MAX_LIFE} ticks. Plain Java, tested under JUnit;
 * the clock is the server's tick count, and the cap lives only in memory (scenes never survive a restart).
 */
public final class SceneCap {

    public static final int MAX_LIVE = 4;
    public static final int SPACING = 60 * 20;
    public static final int MAX_LIFE = 5 * 60 * 20;

    private record Scene(long id, long start) {}

    private final List<Scene> live = new ArrayList<>();
    private long lastStart = Long.MIN_VALUE / 2;
    private long nextId = 1;

    /** True when a scene could start at {@code now}. */
    public boolean canStart(long now) {
        prune(now);
        return live.size() < MAX_LIVE && now - lastStart >= SPACING;
    }

    /** Starts a scene and returns its id, or 0 when the cap refuses it. */
    public long start(long now) {
        if (!canStart(now)) {
            return 0;
        }
        long id = nextId++;
        live.add(new Scene(id, now));
        lastStart = now;
        return id;
    }

    public void end(long id) {
        live.removeIf(s -> s.id() == id);
    }

    public int liveCount(long now) {
        prune(now);
        return live.size();
    }

    public void reset() {
        live.clear();
        lastStart = Long.MIN_VALUE / 2;
    }

    private void prune(long now) {
        live.removeIf(s -> now - s.start() >= MAX_LIFE);
    }
}
