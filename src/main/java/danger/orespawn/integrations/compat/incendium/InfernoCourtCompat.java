package danger.orespawn.integrations.compat.incendium;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import danger.orespawn.integrations.OreSpawnIntegrations;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Honorary Court: the addon's boss-celebration ritual extended to Incendium's
 * Nether nobility. When a player downs the Hovering Inferno, the Royal Court
 * salutes with a server-wide announcement and a fire-palette firework volley
 * over the corpse; downing one of Incendium's structure minibosses earns a
 * smaller two-rocket salute.
 *
 * <p><b>Detection (Incendium is a datapack mod — there is nothing to
 * classload, so the soft-dep is pure string matching):</b> Incendium 5.4.4
 * summons its bosses as vanilla mobs decorated with NBT. Verified against
 * {@code Incendium_1.21.x_v5.4.4.jar} (base data, not the 1.21.2+/1.21.4+
 * overlays):
 * <ul>
 *   <li>The Hovering Inferno is a blaze summoned with command tag
 *       {@code in.hovering_inferno} and
 *       {@code DeathLootTable:"incendium:hovering_inferno"}
 *       ({@code data/incendium/function/hovering_inferno/summon.mcfunction}).
 *       We match the command tag — it survives the "disgraceful kill" path
 *       ({@code kill/disgracefully.mcfunction}) swapping the loot table to
 *       {@code minecraft:empty}, and that path uses {@code /kill} which our
 *       player-credit guard filters out anyway.</li>
 *   <li>Minibosses get {@code DeathLootTable} set to a distinct id at init
 *       ({@code function/entity/&lt;name&gt;/*init*.mcfunction}):
 *       {@code incendium:entity/sentry} (Pipeline Sentry / Prime Pipeline
 *       Sentry), {@code incendium:entity/spirit}, {@code
 *       incendium:entity/toxic_slime}, {@code incendium:entity/withered_skeleton}
 *       (all four verified). We match {@link Mob#getLootTable()}.
 *       Deliberately excluded: {@code incendium:entity/dune_blaze}
 *       (mass-summoned "Infernal Minion" adds during the Inferno fight),
 *       the {@code incendium:cvill/mob/*} piglin villagers (civilians), and
 *       the castle elites ({@code incendium:castle/entity/*} — lesser named
 *       mobs; the knight table is not even wired in 5.4.4 base data).</li>
 * </ul>
 *
 * <p>Without Incendium installed no entity ever carries these tags/tables, so
 * every handler is inert; init may be called unconditionally, though the
 * orchestrator is expected to gate it on ModList.isLoaded("incendium") for
 * tidiness.</p>
 *
 * <p>POLICY 4 — API surfaces verified via javap: MC 1.21.1 (NeoForge
 * 21.1.223 dev jar): {@code Entity.getTags() -> Set<String>},
 * {@code Mob.getLootTable() -> ResourceKey<LootTable>} (public final,
 * non-null: falls back to the type default). neoforge-21.1.223-universal.jar:
 * {@code LivingDeathEvent.getSource() -> DamageSource},
 * {@code ServerTickEvent.Post}, {@code ServerStoppedEvent}. Pack runtime is
 * NeoForge 21.1.248; all surfaces stable across 21.1.x.</p>
 *
 * <p>House defensive style (copied from compat/alive): every game-event
 * handler body is try/catch with a log-once mute; delayed rockets run through
 * this class's own tiny server-tick queue (compat/alive's AliveScheduler is
 * package-private by design), wiped on server stop.</p>
 */
public final class InfernoCourtCompat {

    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();

    /** Command tag Incendium puts on the Hovering Inferno blaze (verified, see class doc). */
    private static final String INFERNO_TAG = "in.hovering_inferno";

    /** Miniboss DeathLootTable ids Incendium assigns at entity init (verified, see class doc). */
    private static final Set<ResourceLocation> MINIBOSS_LOOT_TABLES = Set.of(
            ResourceLocation.fromNamespaceAndPath("incendium", "entity/sentry"),
            ResourceLocation.fromNamespaceAndPath("incendium", "entity/spirit"),
            ResourceLocation.fromNamespaceAndPath("incendium", "entity/toxic_slime"),
            ResourceLocation.fromNamespaceAndPath("incendium", "entity/withered_skeleton"));

    // Fire-palette star colors (RGB). INFERNO_ORANGE is Incendium's own
    // #ff6600 boss-bar/name color; GOLD and RED are the house celebration
    // palette from compat/alive/CelebrationHandler.
    private static final int INFERNO_ORANGE = 0xFF6600;
    private static final int GOLD = 0xF0C334;
    private static final int RED = 0xDE3F2C;

    /** Toxic Slime splits/re-inits could machine-gun the mini salute; one per dimension per 15s. */
    private static final int MINI_COOLDOWN_TICKS = 300;

    private static final Map<ResourceKey<Level>, Long> LAST_MINI_SALUTE = new HashMap<>();

    // --- self-contained delayed-task queue (AliveScheduler shape, local copy) ---
    private record ScheduledTask(long runAtTick, Runnable action) {}
    private static final List<ScheduledTask> SCHEDULED = new ArrayList<>();

    private InfernoCourtCompat() {
    }

    public static void init(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(InfernoCourtCompat::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(InfernoCourtCompat::onServerTick);
        NeoForge.EVENT_BUS.addListener(InfernoCourtCompat::onServerStopped);
    }

    static void onLivingDeath(LivingDeathEvent event) {
        try {
            LivingEntity died = event.getEntity();
            if (died.level().isClientSide()) {
                return;
            }
            if (!(died.level() instanceof ServerLevel level)) {
                return;
            }
            // Player-credited kills only: the Court salutes a hunt, not
            // Incendium's own /kill cleanup (disgraceful-kill path) or
            // environment deaths. DamageSource.getEntity() resolves
            // projectile owners, so bow/trident kills count.
            if (!(event.getSource().getEntity() instanceof Player)) {
                return;
            }
            if (died.getTags().contains(INFERNO_TAG)) {
                grandSalute(level, died);
                return;
            }
            if (died instanceof Mob mob
                    && MINIBOSS_LOOT_TABLES.contains(mob.getLootTable().location())) {
                miniSalute(level, died);
            }
        } catch (Throwable t) {
            logOnce("inferno_court_death", t);
        }
    }

    /** Hovering Inferno: server-wide announcement + 5-7 rockets across ~6s. */
    private static void grandSalute(ServerLevel level, LivingEntity died) {
        MinecraftServer server = level.getServer();
        server.getPlayerList().broadcastSystemMessage(Component.translatable(
                "event.orespawn_integrations.honorary_court", died.getDisplayName()), false);
        double x = died.getX();
        double y = died.getY();
        double z = died.getZ();
        RandomSource random = level.random;
        int rockets = 5 + random.nextInt(3);
        for (int i = 0; i < rockets; i++) {
            int delay = i * 25 + random.nextInt(15);
            schedule(server, delay, () -> {
                RandomSource r = level.random;
                launchRocket(level,
                        x + (r.nextDouble() - 0.5) * 6.0, y,
                        z + (r.nextDouble() - 0.5) * 6.0,
                        firePalette(r));
            });
        }
    }

    /** Miniboss: quiet two-rocket salute at the corpse, per-dimension cooldown. */
    private static void miniSalute(ServerLevel level, LivingEntity died) {
        long now = level.getGameTime();
        Long last = LAST_MINI_SALUTE.get(level.dimension());
        if (last != null && now - last < MINI_COOLDOWN_TICKS) {
            return;
        }
        LAST_MINI_SALUTE.put(level.dimension(), now);
        MinecraftServer server = level.getServer();
        double x = died.getX();
        double y = died.getY();
        double z = died.getZ();
        for (int i = 0; i < 2; i++) {
            int delay = i * 20 + level.random.nextInt(10);
            schedule(server, delay, () -> {
                RandomSource r = level.random;
                launchRocket(level,
                        x + (r.nextDouble() - 0.5) * 3.0, y,
                        z + (r.nextDouble() - 0.5) * 3.0,
                        firePalette(r));
            });
        }
    }

    /** Two distinct fire-palette colors per rocket. */
    private static IntList firePalette(RandomSource random) {
        return random.nextBoolean()
                ? IntList.of(INFERNO_ORANGE, GOLD)
                : IntList.of(INFERNO_ORANGE, RED);
    }

    /**
     * Vanilla-shaped rocket, construction copied from
     * compat/alive/CelebrationHandler#launchRocket (that helper is
     * package-private; the recipe is CelebrateVillagersSurvivedRaid's): one
     * LARGE_BALL explosion, twinkle, no trail, flight 1.
     */
    private static void launchRocket(ServerLevel level, double x, double y, double z, IntList colors) {
        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        rocket.set(DataComponents.FIREWORKS, new Fireworks(1, List.of(new FireworkExplosion(
                FireworkExplosion.Shape.LARGE_BALL, colors, IntList.of(), false, true))));
        level.addFreshEntity(new FireworkRocketEntity(level, rocket, x, y + 1.0, z, false));
    }

    // --- delayed-task plumbing ---

    private static void schedule(MinecraftServer server, int delayTicks, Runnable action) {
        SCHEDULED.add(new ScheduledTask(server.getTickCount() + delayTicks, action));
    }

    static void onServerTick(ServerTickEvent.Post event) {
        if (SCHEDULED.isEmpty()) {
            return;
        }
        long now = event.getServer().getTickCount();
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
                    logOnce("inferno_court_task", t);
                }
            }
        }
    }

    static void onServerStopped(ServerStoppedEvent event) {
        SCHEDULED.clear();
        LAST_MINI_SALUTE.clear();
    }

    private static void logOnce(String key, Throwable t) {
        if (LOGGED.add(key)) {
            OreSpawnIntegrations.LOGGER.error(
                    "[incendium] handler '{}' failed; muting further reports of this failure", key, t);
        }
    }
}
