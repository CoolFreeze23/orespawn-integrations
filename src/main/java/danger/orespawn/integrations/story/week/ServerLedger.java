package danger.orespawn.integrations.story.week;

import danger.orespawn.integrations.story.StoryCore;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The Server's Ledger's dawn queue (system-livro-caixa-do-servidor): every once-a-day job is queued at dawn and run
 * one job per server tick, so no tick carries two and the sunrise never hitches. A job that would run past 1 ms
 * splits itself and queues its remainder with {@link #enqueue}. The hourly office-hours job shares the queue.
 *
 * <p>The queue holds nothing between restarts: a stop mid-dawn just runs the next dawn whole. The last dawn's queue
 * length and its slowest job are kept for {@code /cc livrocaixa dawn}, the line the copy's spark runs read.</p>
 */
public final class ServerLedger {

    /** The budget one job keeps to in any tick. */
    public static final long JOB_BUDGET_NANOS = 1_000_000L;

    private record Job(String key, Runnable action) {}

    private static final Deque<Job> QUEUE = new ArrayDeque<>();

    private static int lastDawnLength;
    private static int lastDawnRun;
    private static String slowestKey = "";
    private static long slowestNanos;
    private static int overBudget;
    private static long lastDawnDay = -1;

    private ServerLedger() {
    }

    /** Queues a job for the next free tick. */
    public static void enqueue(String key, Runnable action) {
        QUEUE.addLast(new Job(key, action));
    }

    /** Starts a dawn's count: the jobs queued from here on are this dawn's. */
    static void beginDawn(long landDay) {
        lastDawnDay = landDay;
        lastDawnLength = QUEUE.size();
        lastDawnRun = 0;
        slowestKey = "";
        slowestNanos = 0;
        overBudget = 0;
    }

    /** Called once the dawn's jobs are all queued, so the length counts them. */
    static void dawnQueued() {
        lastDawnLength = QUEUE.size();
    }

    static void onServerTick(ServerTickEvent.Post event) {
        Job job = QUEUE.pollFirst();
        if (job == null) {
            return;
        }
        long start = System.nanoTime();
        try {
            job.action().run();
        } catch (Throwable t) {
            StoryCore.logOnce("ledger." + job.key(), t);
        }
        long took = System.nanoTime() - start;
        lastDawnRun++;
        if (took > slowestNanos) {
            slowestNanos = took;
            slowestKey = job.key();
        }
        if (took > JOB_BUDGET_NANOS) {
            overBudget++;
        }
    }

    static void onServerStopped(ServerStoppedEvent event) {
        QUEUE.clear();
        lastDawnDay = -1;
        lastDawnLength = 0;
        lastDawnRun = 0;
        slowestKey = "";
        slowestNanos = 0;
        overBudget = 0;
    }

    public static int pending() {
        return QUEUE.size();
    }

    /** The last dawn's report: {Land's day, jobs queued, jobs run, slowest key, slowest microseconds, over 1 ms}. */
    public static Object[] lastDawn(MinecraftServer server) {
        return new Object[]{lastDawnDay, lastDawnLength, lastDawnRun, slowestKey.isEmpty() ? "-" : slowestKey,
                slowestNanos / 1000L, overBudget};
    }
}
