package danger.orespawn.integrations.compat.walkers.bespoke;

import java.util.Optional;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Shared aim helpers for the bespoke boss abilities. */
final class AimUtil {

    private AimUtil() {
    }

    /** First block hit along the player's aim, or the full-range point on a miss. */
    static Vec3 aimPoint(Player player, double range) {
        return player.pick(range, 1.0F, false).getLocation();
    }

    /**
     * Closest living entity whose (slightly inflated) hitbox intersects the aim
     * ray, or {@code null}. Same sweep the generic ranged ability uses, minus
     * the stored-shape exclusion (bespoke abilities never park the shape).
     */
    static LivingEntity aimedLiving(Player player, Level world, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = aimPoint(player, range);
        AABB sweep = player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0D);
        LivingEntity closest = null;
        double closestDistSq = Double.MAX_VALUE;
        for (LivingEntity candidate : world.getEntitiesOfClass(LivingEntity.class, sweep,
                e -> e != player && e.isAlive() && !e.isSpectator())) {
            Optional<Vec3> hit = candidate.getBoundingBox().inflate(0.35D).clip(eye, end);
            if (hit.isPresent()) {
                double distSq = eye.distanceToSqr(hit.get());
                if (distSq < closestDistSq) {
                    closestDistSq = distSq;
                    closest = candidate;
                }
            }
        }
        return closest;
    }
}
