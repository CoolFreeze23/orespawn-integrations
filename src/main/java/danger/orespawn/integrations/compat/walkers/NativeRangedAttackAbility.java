package danger.orespawn.integrations.compat.walkers;

import danger.orespawn.integrations.OreSpawnIntegrations;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import tocraft.walkers.ability.ShapeAbility;

/**
 * Generic R-key ability: fire the morphed mob's own ranged attack. Applies to
 * every orespawn entity whose class implements {@link RangedAttackMob}
 * (currently Girlfriend and Boyfriend in beta.3; automatically covers any mob
 * the port upgrades later). The stored shape entity is parked on the player and
 * its {@code performRangedAttack} is aimed along the player's line of sight.
 */
final class NativeRangedAttackAbility extends ShapeAbility<LivingEntity> {

    private static final double AIM_RANGE = 48.0D;

    @Override
    public ResourceLocation getId() {
        return ResourceLocation.fromNamespaceAndPath(OreSpawnIntegrations.MODID, "native_ranged_attack");
    }

    @Override
    public void onUse(Player player, LivingEntity shape, Level world) {
        if (world.isClientSide() || !(shape instanceof RangedAttackMob ranged)) {
            return;
        }
        if (shape.level() != world) {
            // Stale stored shape (e.g. mid dimension change): performRangedAttack
            // spawns its projectile into shape.level(), which would be the wrong
            // dimension here - skip this use instead.
            OreSpawnIntegrations.LOGGER.debug("Walkers shape level mismatch for {}; ranged ability skipped",
                    EntityType.getKey(shape.getType()));
            return;
        }
        try {
            // The projectile originates from the shooter entity, so put the shape
            // exactly where the player stands and copy the aim rotations.
            shape.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
            shape.setYHeadRot(player.getYHeadRot());

            Vec3 eye = player.getEyePosition();
            // Ray end: first block hit along the aim, or full range on a miss.
            Vec3 end = player.pick(AIM_RANGE, 1.0F, false).getLocation();
            LivingEntity target = findAimedEntity(player, shape, world, eye, end);
            if (target == null) {
                // performRangedAttack needs a LivingEntity to aim at; a positioned,
                // never-spawned stand-in works because the orespawn attack impls
                // only read the target's coordinates and eye height.
                target = new ArmorStand(world, end.x, end.y, end.z);
            }
            ranged.performRangedAttack(target, 1.0F);
        } catch (Throwable t) {
            // Stored shape entities have no ticking AI: a mob's attack code may
            // assume goal or target state that does not exist here. One broken
            // mob must not take down the ability system or the server thread.
            OreSpawnIntegrations.LOGGER.error("Native ranged attack failed for shape {} - use skipped",
                    EntityType.getKey(shape.getType()), t);
        }
    }

    /** Closest living entity whose (slightly inflated) hitbox intersects the aim ray, or null. */
    private static LivingEntity findAimedEntity(Player player, LivingEntity shape, Level world, Vec3 eye, Vec3 end) {
        AABB sweep = player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0D);
        LivingEntity closest = null;
        double closestDistSq = Double.MAX_VALUE;
        for (LivingEntity candidate : world.getEntitiesOfClass(LivingEntity.class, sweep,
                e -> e != player && e != shape && e.isAlive() && !e.isSpectator())) {
            Optional<Vec3> hit = candidate.getBoundingBox().inflate(0.3D).clip(eye, end);
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

    @Override
    public int getDefaultCooldown() {
        return 40;
    }

    @Override
    public Item getIcon() {
        return Items.BOW;
    }
}
