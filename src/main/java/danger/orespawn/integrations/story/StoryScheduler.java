package danger.orespawn.integrations.story;

import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Server-thread delayed tasks for the road's systems, the same drain shape as the alive world's scheduler: due tasks
 * are collected first and run after the pass, so a task may schedule its own follow-ups. Cleared when the server stops,
 * so nothing carries over between singleplayer sessions; anything that must survive a restart lives in SavedData.
 */
public final class StoryScheduler {

    private record Task(long runAtTick, String key, Runnable action) {}

    private static final List<Task> TASKS = new ArrayList<>();

    private StoryScheduler() {
    }

    public static void schedule(MinecraftServer server, int delayTicks, String key, Runnable action) {
        TASKS.add(new Task(server.getTickCount() + Math.max(0, delayTicks), key, action));
    }

    static void onServerTick(ServerTickEvent.Post event) {
        if (TASKS.isEmpty()) {
            return;
        }
        long now = event.getServer().getTickCount();
        List<Task> due = null;
        Iterator<Task> it = TASKS.iterator();
        while (it.hasNext()) {
            Task task = it.next();
            if (now >= task.runAtTick()) {
                it.remove();
                if (due == null) {
                    due = new ArrayList<>();
                }
                due.add(task);
            }
        }
        if (due != null) {
            for (Task task : due) {
                try {
                    task.action().run();
                } catch (Throwable t) {
                    StoryCore.logOnce(task.key(), t);
                }
            }
        }
    }

    static void reset() {
        TASKS.clear();
    }
}
