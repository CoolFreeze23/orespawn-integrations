package danger.orespawn.integrations.compat.walkers.bespoke;

import danger.orespawn.entity.BetterFireball;
import danger.orespawn.entity.Mothra;
import danger.orespawn.integrations.OreSpawnIntegrations;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import tocraft.walkers.ability.ShapeAbility;

/**
 * Mothra's R-key: a wing gust that hurls everything nearby away from the
 * player, plus her signature breath - the port's own {@link BetterFireball}
 * with {@code setNotMe()}, exactly what {@code Mothra.attackWithFireball}
 * spawns (verified against beta.3 bytecode; the small/owner-safe flags are the
 * port's, not ours).
 */
public final class MothraWingGustAbility extends ShapeAbility<Mothra> {

    private static final double GUST_RADIUS = 7.0D;

    @Override
    public ResourceLocation getId() {
        return ResourceLocation.fromNamespaceAndPath(OreSpawnIntegrations.MODID, "mothra_wing_gust");
    }

    @Override
    public void onUse(Player player, Mothra shape, Level world) {
        if (world.isClientSide()) {
            return;
        }
        try {
            // Wing gust: radial knockback, stronger the closer the victim.
            AABB area = player.getBoundingBox().inflate(GUST_RADIUS, GUST_RADIUS * 0.5D, GUST_RADIUS);
            for (LivingEntity victim : world.getEntitiesOfClass(LivingEntity.class, area,
                    e -> e != player && e.isAlive() && !e.isSpectator())) {
                double dist = victim.distanceTo(player);
                double strength = Math.max(0.3D, 1.4D - dist / GUST_RADIUS);
                // knockback(str, x, z) pushes AWAY from the (x, z) source point,
                // so pass the player-minus-victim delta (vanilla hurt() pattern).
                victim.knockback(strength, player.getX() - victim.getX(), player.getZ() - victim.getZ());
                victim.hurtMarked = true;
            }

            // Breath: the port's fireball, aimed along the player's look vector.
            Vec3 dir = player.getLookAngle();
            Vec3 eye = player.getEyePosition();
            BetterFireball breath = new BetterFireball(world, player, dir);
            breath.setSmall();
            breath.setNotMe();
            breath.setPos(eye.x + dir.x * 1.5D, eye.y + dir.y * 1.5D, eye.z + dir.z * 1.5D);
            world.addFreshEntity(breath);

            if (world instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.CLOUD,
                        player.getX(), player.getY() + 1.0D, player.getZ(),
                        40, 2.5D, 0.6D, 2.5D, 0.15D);
            }
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 1.2F, 0.7F);
        } catch (Throwable t) {
            // Port entity internals may shift between betas; one broken boss
            // must not take the ability system down with it.
            OreSpawnIntegrations.LOGGER.error("Mothra wing gust failed - use skipped", t);
        }
    }

    @Override
    public int getDefaultCooldown() {
        return 90;
    }

    @Override
    public Item getIcon() {
        return Items.FEATHER;
    }
}
