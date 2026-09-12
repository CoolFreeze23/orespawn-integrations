package danger.orespawn.integrations.compat.alive;

import java.util.List;

import danger.orespawn.entity.ThePrinceAdult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Rare daytime spectacle: an adult Prince glides in a straight line across the
 * sky near a random player. The entity is a cosmetic puppet - a plain
 * noAI'd TamableAnimal would fall, so instead it is frozen airborne with the
 * verified recipe (setActivity(1) flight pose + setOrderedToSit(true) +
 * noGravity + invulnerable + silent) and driven by a per-tick scheduler task
 * that setPos's it along the line with constant yaw. The glide pose (sit flag
 * zeroes wing flap) is the deterministic choice.
 *
 * <p>Only one flyover runs at a time; the flag is cleared when the puppet is
 * discarded and on server stop.</p>
 *
 * <p>The puppet carries the {@link #PUPPET_TAG} entity tag and must never
 * reach disk: its flight task lives only in the running server, so a saved
 * copy would hang in the sky forever as a frozen, invulnerable Prince. It is
 * discarded before the shutdown save, and any tagged Prince that still loads
 * from disk (autosave during the glide followed by a crash, a chunk unloading
 * mid-flight) is refused at join time.</p>
 */
public final class PrinceFlyover {

    private static final int ROLL = 36000;             // ~one chance per 30 min of day-ticks
    private static final double SPEED = 0.6;           // blocks per tick
    private static final double LEG = 90.0;            // start/end distance from the player
    private static final double ALTITUDE = 45.0;       // above surface at the chosen player
    private static final int MAX_TICKS = 600;
    private static final int MIDPOINT_TICK = 150;      // 90 blocks at 0.6 b/t: passing the player
    private static final double DESPAWN_DIST_SQ = 200.0 * 200.0;
    private static final double WITNESS_RADIUS = 96.0;

    /** Entity tag on the puppet, usable in selectors: {@code @e[tag=orespawn_integrations.flyover_puppet]}. */
    public static final String PUPPET_TAG = "orespawn_integrations.flyover_puppet";

    private static boolean active;
    private static ThePrinceAdult current;

    private PrinceFlyover() {
    }

    static void onLevelTick(LevelTickEvent.Post event) {
        try {
            if (active) {
                return;
            }
            if (!(event.getLevel() instanceof ServerLevel level)) {
                return;
            }
            if (level.dimension() != Level.OVERWORLD) {
                return;
            }
            if (!level.isDay()) {
                return;
            }
            if (level.random.nextInt(ROLL) != 0) {
                return;
            }
            List<ServerPlayer> players = level.players();
            if (players.isEmpty()) {
                return;
            }
            begin(level, players.get(level.random.nextInt(players.size())));
        } catch (Throwable t) {
            AliveWorldCompat.logOnce("prince_roll", t);
        }
    }

    /**
     * Cross-module trigger (SeasonsBridge seasonal flyover): starts one flyover
     * anchored on the player nearest {@code pos}, honoring the same
     * single-active-flyover flag as the random roll. Safe no-op when a flyover
     * is already airborne or the level is empty.
     */
    public static void triggerNear(ServerLevel level, net.minecraft.core.BlockPos pos) {
        try {
            if (active) {
                return;
            }
            List<ServerPlayer> players = level.players();
            if (players.isEmpty()) {
                return;
            }
            ServerPlayer anchor = players.get(0);
            double best = Double.MAX_VALUE;
            for (ServerPlayer p : players) {
                double d = p.distanceToSqr(pos.getX(), pos.getY(), pos.getZ());
                if (d < best) {
                    best = d;
                    anchor = p;
                }
            }
            begin(level, anchor);
        } catch (Throwable t) {
            AliveWorldCompat.logOnce("prince_trigger", t);
        }
    }

    private static void begin(ServerLevel level, ServerPlayer player) {
        EntityType<?> type = AliveWorldCompat.entityType("orespawn", "the_prince_adult");
        if (type == null) {
            return;
        }
        Entity created = type.create(level);
        if (!(created instanceof ThePrinceAdult prince)) {
            if (created != null) {
                created.discard();
            }
            AliveWorldCompat.logMissingOnce("the_prince_adult class (unexpected entity type)");
            return;
        }
        RandomSource random = level.random;
        double angle = random.nextDouble() * Math.PI * 2.0;
        double dirX = Math.cos(angle);
        double dirZ = Math.sin(angle);
        double y = level.getHeight(Heightmap.Types.MOTION_BLOCKING,
                player.getBlockX(), player.getBlockZ()) + ALTITUDE;
        double startX = player.getX() - dirX * LEG;
        double startZ = player.getZ() - dirZ * LEG;
        float yaw = (float) Math.toDegrees(Math.atan2(dirZ, dirX)) - 90.0F;

        prince.moveTo(startX, y, startZ, yaw, 0.0F);
        prince.setActivity(1);          // flight pose
        prince.setOrderedToSit(true);   // freezes wing flap -> steady glide
        prince.setInvulnerable(true);
        prince.setNoGravity(true);
        prince.setSilent(true);
        prince.setPersistenceRequired(); // our discard logic decides its end, not despawn rules
        prince.addTag(PUPPET_TAG);
        current = prince;
        if (!level.addFreshEntity(prince)) {
            current = null;
            return;
        }
        active = true;
        AliveScheduler.schedule(level.getServer(), 1,
                new Flight(level, prince, startX, startZ, y, dirX, dirZ, yaw));
    }

    /** Per-tick flight driver; reschedules itself until the puppet is retired. */
    private static final class Flight implements Runnable {
        private final ServerLevel level;
        private final ThePrinceAdult prince;
        private final double startX;
        private final double startZ;
        private final double y;
        private final double dirX;
        private final double dirZ;
        private final float yaw;
        private int tick;
        private boolean granted;

        Flight(ServerLevel level, ThePrinceAdult prince, double startX, double startZ, double y,
               double dirX, double dirZ, float yaw) {
            this.level = level;
            this.prince = prince;
            this.startX = startX;
            this.startZ = startZ;
            this.y = y;
            this.dirX = dirX;
            this.dirZ = dirZ;
            this.yaw = yaw;
        }

        @Override
        public void run() {
            try {
                if (prince.isRemoved()) {
                    retire(prince);
                    return;
                }
                tick++;
                if (tick >= MAX_TICKS || farFromAllPlayers()) {
                    retire(prince);
                    return;
                }
                double x = startX + dirX * SPEED * tick;
                double z = startZ + dirZ * SPEED * tick;
                prince.setDeltaMovement(Vec3.ZERO);
                prince.setPos(x, y, z);
                prince.setYRot(yaw);
                prince.setYBodyRot(yaw);
                prince.setYHeadRot(yaw);
                if (!granted && tick >= MIDPOINT_TICK) {
                    granted = true;
                    AliveWorldCompat.grantWitnessed(level, "modern_wonders/royal_escort",
                            x, y, z, WITNESS_RADIUS);
                }
                AliveScheduler.schedule(level.getServer(), 1, this);
            } catch (Throwable t) {
                try {
                    retire(prince);
                } catch (Throwable ignored) {
                    active = false;
                    current = null;
                }
                AliveWorldCompat.logOnce("prince_flight", t);
            }
        }

        private boolean farFromAllPlayers() {
            double best = Double.MAX_VALUE;
            for (ServerPlayer p : level.players()) {
                best = Math.min(best, p.distanceToSqr(prince));
            }
            return best > DESPAWN_DIST_SQ;
        }
    }

    /** Ends a flyover: removes the puppet if it is still in the world and frees the single-flyover slot. */
    private static void retire(ThePrinceAdult prince) {
        if (prince != null && !prince.isRemoved()) {
            prince.discard();
        }
        if (prince == null || prince == current) {
            current = null;
        }
        active = false;
    }

    /** Runs before the shutdown save, so an airborne puppet is never written to disk. */
    static void onServerStopping(ServerStoppingEvent event) {
        try {
            retire(current);
        } catch (Throwable t) {
            AliveWorldCompat.logOnce("prince_stop", t);
        }
    }

    /**
     * Refuses a puppet that reached disk anyway. Fresh puppets are not loaded
     * from disk and pass through untouched.
     */
    static void onEntityJoin(EntityJoinLevelEvent event) {
        try {
            if (event.getLevel().isClientSide() || !event.loadedFromDisk()) {
                return;
            }
            if (event.getEntity() instanceof ThePrinceAdult adult && adult.getTags().contains(PUPPET_TAG)) {
                event.setCanceled(true);
                adult.discard();
            }
        } catch (Throwable t) {
            AliveWorldCompat.logOnce("prince_join", t);
        }
    }

    static void reset() {
        active = false;
        current = null;
    }
}
