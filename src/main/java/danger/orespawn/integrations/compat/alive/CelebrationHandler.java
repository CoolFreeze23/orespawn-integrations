package danger.orespawn.integrations.compat.alive;

import java.time.LocalDate;
import java.time.Month;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import danger.orespawn.util.SeasonalDates;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Boss-kill celebrations plus the New Year calendar volleys (both share the
 * firework helper).
 *
 * <p>The boss guard matches the {@code c:bosses} entity-type tag this addon
 * ships; WalkersCompat listens to the same LivingDeathEvent with an identical
 * tag check and the two listeners deliberately coexist.</p>
 *
 * <p>Seasonal color choices call {@code SeasonalDates.isHalloween()} on the
 * shipped port jar at launch time - never cached at init, so a server that
 * runs across midnight flips palettes correctly.</p>
 */
final class CelebrationHandler {

    /** Entity-type tag marking OreSpawn boss mobs (shipped by this addon's data layer). */
    static final TagKey<EntityType<?>> BOSSES_TAG =
            TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("c", "bosses"));

    private static final int GRACE_TICKS = 1200;           // 60s hostile grace after a boss falls
    private static final int VILLAGE_SEARCH_RADIUS = 128;  // POI search around the corpse
    private static final int NEW_YEAR_VOLLEY_PERIOD = 2400;

    // Firework star colors (RGB).
    private static final int GOLD = 0xF0C334;
    private static final int RED = 0xDE3F2C;
    private static final int HALLOWEEN_ORANGE = 0xF07813;
    private static final int HALLOWEEN_PURPLE = 0x7C1BB8;
    private static final int[] FESTIVE = {GOLD, RED, 0xF5F5F5, 0x46A8E0, 0x4BD44B, HALLOWEEN_PURPLE};

    /** Per-dimension "hostiles stand down" deadline (game time), wiped on server stop. */
    private static final Map<ResourceKey<Level>, Long> GRACE_UNTIL = new HashMap<>();

    private CelebrationHandler() {
    }

    // =====================================================================
    // Boss-kill celebration
    // =====================================================================

    static void onBossDeath(LivingDeathEvent event) {
        try {
            LivingEntity died = event.getEntity();
            if (died.level().isClientSide()) {
                return;
            }
            if (!died.getType().is(BOSSES_TAG)) {
                return;
            }
            if (!(died.level() instanceof ServerLevel serverLevel)) {
                return;
            }
            MinecraftServer server = serverLevel.getServer();

            // (a) world-wide announcement
            server.getPlayerList().broadcastSystemMessage(Component.translatable(
                    "event.orespawn_integrations.boss_felled", died.getDisplayName()), false);

            // (b) nearest village celebrates: bell + fireworks over the meeting point
            serverLevel.getPoiManager()
                    .findClosest(h -> h.is(PoiTypes.MEETING), died.blockPosition(),
                            VILLAGE_SEARCH_RADIUS, PoiManager.Occupancy.ANY)
                    .ifPresent(bellPos -> celebrateAt(serverLevel, bellPos));

            // (c) hostile grace: one-time stand-down sweep + a timed truce window
            for (Monster monster : died.level().getEntitiesOfClass(
                    Monster.class, died.getBoundingBox().inflate(64, 32, 64))) {
                monster.setTarget(null);
            }
            GRACE_UNTIL.put(serverLevel.dimension(), serverLevel.getGameTime() + GRACE_TICKS);
        } catch (Throwable t) {
            AliveWorldCompat.logOnce("boss_celebration", t);
        }
    }

    private static void celebrateAt(ServerLevel level, BlockPos bellPos) {
        BlockState state = level.getBlockState(bellPos);
        if (state.getBlock() instanceof BellBlock bell) {
            bell.attemptToRing(level, bellPos, null);
        }
        MinecraftServer server = level.getServer();
        RandomSource random = level.random;
        int rockets = 3 + random.nextInt(3); // 3-5 across ~6s
        for (int i = 0; i < rockets; i++) {
            int delay = i * 25 + random.nextInt(15);
            AliveScheduler.schedule(server, delay, () -> {
                RandomSource r = level.random;
                double x = bellPos.getX() + 0.5 + (r.nextDouble() - 0.5) * 4.0;
                double z = bellPos.getZ() + 0.5 + (r.nextDouble() - 0.5) * 4.0;
                launchRocket(level, x, bellPos.getY(), z, celebrationColors());
            });
        }
    }

    /** Halloween queried live per launch, matching the port's own clock semantics. */
    private static IntList celebrationColors() {
        return SeasonalDates.isHalloween()
                ? IntList.of(HALLOWEEN_ORANGE, HALLOWEEN_PURPLE)
                : IntList.of(GOLD, RED);
    }

    /**
     * Vanilla-shaped rocket (CelebrateVillagersSurvivedRaid construction):
     * one LARGE_BALL explosion, twinkle, no trail, flight 1.
     */
    static void launchRocket(ServerLevel level, double x, double y, double z, IntList colors) {
        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        rocket.set(DataComponents.FIREWORKS, new Fireworks(1, List.of(new FireworkExplosion(
                FireworkExplosion.Shape.LARGE_BALL, colors, IntList.of(), false, true))));
        level.addFreshEntity(new FireworkRocketEntity(level, rocket, x, y + 1.0, z, false));
    }

    /**
     * Grace enforcement: while the post-boss truce runs in a dimension, no
     * monster there may acquire a player target. Only cancels when the NEW
     * target is a (non-null) player - the stand-down sweep's own
     * setTarget(null) refires this event and must pass through.
     */
    static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        try {
            if (!(event.getEntity() instanceof Monster monster)) {
                return;
            }
            LivingEntity newTarget = event.getNewAboutToBeSetTarget();
            if (newTarget == null) {
                return; // clearing a target is always allowed
            }
            if (!(newTarget instanceof Player)) {
                return;
            }
            Long until = GRACE_UNTIL.get(monster.level().dimension());
            if (until != null && monster.level().getGameTime() < until) {
                event.setCanceled(true);
            }
        } catch (Throwable t) {
            AliveWorldCompat.logOnce("boss_grace", t);
        }
    }

    // =====================================================================
    // New Year calendar volleys (Dec 31 / Jan 1, night)
    // =====================================================================

    static void onNewYearTick(LevelTickEvent.Post event) {
        try {
            if (!(event.getLevel() instanceof ServerLevel level)) {
                return;
            }
            if (level.dimension() != Level.OVERWORLD) {
                return;
            }
            if (level.getGameTime() % NEW_YEAR_VOLLEY_PERIOD != 0) {
                return;
            }
            if (!level.isNight() || !isNewYearWindow()) {
                return;
            }
            List<ServerPlayer> players = level.players();
            if (players.isEmpty()) {
                return;
            }
            ServerPlayer player = players.get(level.random.nextInt(players.size()));
            level.getPoiManager()
                    .findClosest(h -> h.is(PoiTypes.MEETING), player.blockPosition(),
                            VILLAGE_SEARCH_RADIUS, PoiManager.Occupancy.ANY)
                    .ifPresent(bellPos -> {
                        MinecraftServer server = level.getServer();
                        for (int i = 0; i < 3; i++) {
                            AliveScheduler.schedule(server, i * 8, () -> {
                                RandomSource r = level.random;
                                double x = bellPos.getX() + 0.5 + (r.nextDouble() - 0.5) * 4.0;
                                double z = bellPos.getZ() + 0.5 + (r.nextDouble() - 0.5) * 4.0;
                                launchRocket(level, x, bellPos.getY(), z, festiveColors(r));
                            });
                        }
                    });
        } catch (Throwable t) {
            AliveWorldCompat.logOnce("new_year", t);
        }
    }

    /**
     * Mirrors SeasonalDates' pattern locally: month/day checked on
     * LocalDate.now() per query, never cached, so a session spanning midnight
     * starts or stops celebrating on its own.
     */
    private static boolean isNewYearWindow() {
        LocalDate now = LocalDate.now();
        return (now.getMonth() == Month.DECEMBER && now.getDayOfMonth() == 31)
                || (now.getMonth() == Month.JANUARY && now.getDayOfMonth() == 1);
    }

    /** Two distinct festive colors per rocket. */
    private static IntList festiveColors(RandomSource random) {
        int first = random.nextInt(FESTIVE.length);
        int second = (first + 1 + random.nextInt(FESTIVE.length - 1)) % FESTIVE.length;
        return IntList.of(FESTIVE[first], FESTIVE[second]);
    }

    static void reset() {
        GRACE_UNTIL.clear();
    }
}
