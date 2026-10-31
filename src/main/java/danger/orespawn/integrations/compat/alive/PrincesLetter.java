package danger.orespawn.integrations.compat.alive;

import danger.orespawn.integrations.config.IntegrationsConfig;
import danger.orespawn.integrations.story.firsthour.FirstHourData;
import danger.orespawn.integrations.story.firsthour.FirstHourScenes;
import danger.orespawn.integrations.story.firsthour.RoyalSummons;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Prince's Letter (B04): the first flyover over a new player, forced low (25 blocks instead of the shipped 45)
 * through {@link PrinceFlyover#triggerNear}, anchored on that player. As he passes overhead the Royal Summons drops
 * on a slow-falling item whose pickup target is its addressee, so a friend standing beside it cannot take it. The
 * ticket calls it at P04 (and once for a player from before 0.10.0, with no number); it is a first-hour scene, so it
 * starts through the scene cap and frees its slot when the puppet retires. No waypoint is made.
 */
final class PrincesLetter {

    static final double FIRST_PASS_ALTITUDE = 25.0;
    private static final int FALL_TICKS = 400;
    private static final double FALL_SPEED = 0.15;

    private PrincesLetter() {
    }

    static void install() {
        FirstHourScenes.letter = PrincesLetter::start;
    }

    private static FirstHourScenes.Result start(ServerPlayer player, boolean numbered) {
        if (!IntegrationsConfig.on(IntegrationsConfig.storyPrincesLetter)) {
            return FirstHourScenes.Result.UNAVAILABLE;
        }
        if (!(player.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return FirstHourScenes.Result.BUSY; // waits until the player is back in the overworld
        }
        long now = level.getServer().getTickCount();
        long scene = FirstHourScenes.CAP.start(now);
        if (scene == 0) {
            return FirstHourScenes.Result.BUSY;
        }
        boolean started = PrinceFlyover.triggerNear(level, player.blockPosition(), FIRST_PASS_ALTITUDE, player,
                () -> FirstHourScenes.CAP.end(scene));
        if (!started) {
            FirstHourScenes.CAP.end(scene);
            return FirstHourScenes.Result.BUSY;
        }
        return FirstHourScenes.Result.STARTED;
    }

    /** Drops the addressee's Summons from the Prince's height, straight over where they stand. */
    static void drop(ServerLevel level, ServerPlayer to, double fromY) {
        if (to.isRemoved() || to.level() != level) {
            return; // gone mid-flight: the ticket hands the book over at their next login
        }
        Vec3 at = new Vec3(to.getX(), Math.max(fromY - 2.0, to.getY() + 4.0), to.getZ());
        ItemEntity item = new ItemEntity(level, at.x, at.y, at.z, RoyalSummons.build(to));
        item.setTarget(to.getUUID());
        item.setUnlimitedLifetime();
        item.setPickUpDelay(10);
        item.setNoGravity(true);
        item.setDeltaMovement(0.0, -FALL_SPEED, 0.0);
        if (!level.addFreshEntity(item)) {
            return;
        }
        FirstHourData.markLetterSent(to, FirstHourData.LETTER_SUMMONS);
        AliveScheduler.schedule(level.getServer(), 1, new SlowFall(item, 0));
    }

    /** Keeps the letter drifting down like a slow-falling feather, then hands it back to gravity. */
    private record SlowFall(ItemEntity item, int age) implements Runnable {
        @Override
        public void run() {
            if (item.isRemoved()) {
                return;
            }
            BlockPos below = item.blockPosition().below();
            boolean landed = item.onGround() || !item.level().getBlockState(below).isAir();
            if (landed || age >= FALL_TICKS) {
                item.setNoGravity(false);
                return;
            }
            item.setDeltaMovement(0.0, -FALL_SPEED, 0.0);
            AliveScheduler.schedule(item.level().getServer(), 1, new SlowFall(item, age + 1));
        }
    }
}
