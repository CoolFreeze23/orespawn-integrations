package danger.orespawn.integrations.compat.alive;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Server-thread-only delayed task queue for the alive-world features, drained
 * by {@link #onServerTick}. Same drain shape as the house pattern: due tasks
 * are collected first and run after iteration, so an action may safely
 * schedule follow-ups (the meteor sequence and the Prince's per-tick flight
 * driver both rely on this). {@link AliveWorldCompat} clears the queue on
 * ServerStoppedEvent so nothing leaks across server restarts in singleplayer.
 */
final class AliveScheduler {

    private static final List<ScheduledTask> SCHEDULED = new ArrayList<>();

    private record ScheduledTask(long runAtTick, Runnable action) {}

    private AliveScheduler() {
    }

    static void schedule(MinecraftServer server, int delayTicks, Runnable action) {
        SCHEDULED.add(new ScheduledTask(server.getTickCount() + delayTicks, action));
    }

    static void onServerTick(ServerTickEvent.Post event) {
        if (SCHEDULED.isEmpty()) {
            return;
        }
        long now = event.getServer().getTickCount();
        // Collect due tasks first so actions may safely schedule follow-ups.
        List<ScheduledTask> due = null;
        Iterator<ScheduledTask> it = SCHEDULED.iterator();
        while (it.hasNext()) {
            ScheduledTask task = it.next();
            if (now >= task.runAtTick()) {
                it.remove();
                if (due == null) {
                    due = new ArrayList<>();
                }
                due.add(task);
            }
        }
        if (due != null) {
            for (ScheduledTask task : due) {
                try {
                    task.action().run();
                } catch (Throwable t) {
                    AliveWorldCompat.logOnce("scheduled_task", t);
                }
            }
        }
    }

    static void reset() {
        SCHEDULED.clear();
    }
}
