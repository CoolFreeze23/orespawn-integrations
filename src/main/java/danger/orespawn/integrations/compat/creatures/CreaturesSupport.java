package danger.orespawn.integrations.compat.creatures;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import danger.orespawn.integrations.OreSpawnIntegrations;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Shared plumbing for the creature-mod celebration compats
 * ({@link GuardVillagersCompat}, {@link DoggyTalentsCompat}): a
 * server-thread delayed-task queue (same drain shape as the alive package's
 * AliveScheduler house pattern), log-once defensive helpers, the shared
 * {@code #c:bosses} tag key, the celebratory firework launcher, and the
 * "witnessed"-criterion advancement grant.
 *
 * <p>This class deliberately references NO partner-mod classes, so it is safe
 * to classload whenever at least one of the partner compats is active. The
 * event-bus hooks (tick drain + server-stop wipe) are registered exactly once
 * via {@link #ensureHooked()} no matter how many partner compats init.</p>
 */
final class CreaturesSupport {

    /** Entity-type tag marking boss mobs — the same tag the addon's alive-package
     *  CelebrationHandler fires its boss celebration on, so these compats trigger
     *  exactly when the celebration does. */
    static final TagKey<EntityType<?>> BOSSES_TAG =
            TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("c", "bosses"));

    /** Celebration firework palette — same gold/red the alive package uses. */
    static final int GOLD = 0xF0C334;
    static final int RED = 0xDE3F2C;

    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();
    private static final AtomicBoolean HOOKED = new AtomicBoolean();
    private static final List<ScheduledTask> SCHEDULED = new ArrayList<>();

    private record ScheduledTask(long runAtTick, Runnable action) {}

    private CreaturesSupport() {
    }

    /** Registers the tick-drain + server-stop listeners exactly once. */
    static void ensureHooked() {
        if (HOOKED.compareAndSet(false, true)) {
            NeoForge.EVENT_BUS.addListener(CreaturesSupport::onServerTick);
            NeoForge.EVENT_BUS.addListener(CreaturesSupport::onServerStopped);
        }
    }

    static void schedule(MinecraftServer server, int delayTicks, Runnable action) {
        SCHEDULED.add(new ScheduledTask(server.getTickCount() + delayTicks, action));
    }

    private static void onServerTick(ServerTickEvent.Post event) {
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
                    logOnce("creatures_scheduled_task", t);
                }
            }
        }
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        SCHEDULED.clear();
    }

    // --- defensive plumbing (house style: log once, never crash a tick) ---

    static void logOnce(String key, Throwable t) {
        if (LOGGED.add(key)) {
            OreSpawnIntegrations.LOGGER.error(
                    "[creatures] handler '{}' failed; muting further reports of this failure", key, t);
        }
    }

    static void logMissingOnce(String what) {
        if (LOGGED.add("missing:" + what)) {
            OreSpawnIntegrations.LOGGER.warn(
                    "[creatures] '{}' is not registered/loaded; the feature that needs it stays inert", what);
        }
    }

    /**
     * Vanilla-shaped celebration rocket (CelebrateVillagersSurvivedRaid
     * construction, mirroring the alive package's launcher): one LARGE_BALL
     * explosion, twinkle, no trail, flight 1.
     */
    static void launchRocket(ServerLevel level, double x, double y, double z, IntList colors) {
        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        rocket.set(DataComponents.FIREWORKS, new Fireworks(1, List.of(new FireworkExplosion(
                FireworkExplosion.Shape.LARGE_BALL, colors, IntList.of(), false, true))));
        level.addFreshEntity(new FireworkRocketEntity(level, rocket, x, y + 1.0, z, false));
    }

    /**
     * Grants criterion "witnessed" of one of this addon's advancements to the
     * given player. Missing advancement (thread toggled off / conditions
     * stripped it) logs once and no-ops — never punishes.
     */
    static void grantWitnessed(ServerPlayer player, String advancementPath) {
        AdvancementHolder holder = player.server.getAdvancements()
                .get(ResourceLocation.fromNamespaceAndPath(OreSpawnIntegrations.MODID, advancementPath));
        if (holder == null) {
            logMissingOnce("advancement " + advancementPath);
            return;
        }
        player.getAdvancements().award(holder, "witnessed");
    }
}
