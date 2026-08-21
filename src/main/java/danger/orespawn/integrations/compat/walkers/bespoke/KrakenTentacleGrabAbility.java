package danger.orespawn.integrations.compat.walkers.bespoke;

import danger.orespawn.MobStats;
import danger.orespawn.entity.Kraken;
import danger.orespawn.integrations.OreSpawnIntegrations;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import tocraft.walkers.ability.ShapeAbility;

/**
 * Kraken's R-key: a tentacle grab. The real Kraken seizes its victim and holds
 * it at a fixed point below itself (PARITY_NOTES PN-004); the morph inverts
 * that - the aimed target is yanked toward the player and takes a bite of the
 * Kraken's own attack stat ({@link MobStats#KRAKEN}, the port's canonical
 * damage table).
 */
public final class KrakenTentacleGrabAbility extends ShapeAbility<Kraken> {

    private static final double GRAB_RANGE = 24.0D;
    /** Used only if the MobStats lookup ever breaks in a future beta. */
    private static final float FALLBACK_DAMAGE = 10.0F;

    @Override
    public ResourceLocation getId() {
        return ResourceLocation.fromNamespaceAndPath(OreSpawnIntegrations.MODID, "kraken_tentacle_grab");
    }

    @Override
    public void onUse(Player player, Kraken shape, Level world) {
        if (world.isClientSide()) {
            return;
        }
        try {
            LivingEntity target = AimUtil.aimedLiving(player, world, GRAB_RANGE);
            if (target == null) {
                player.displayClientMessage(
                        Component.translatable("message.orespawn_integrations.tentacle_miss"), true);
                return;
            }

            float damage = FALLBACK_DAMAGE;
            try {
                // Kraken hits for 80 in the port; a quarter-strength grab is
                // still a haymaker without one-shotting mid-game mobs.
                damage = (float) (MobStats.KRAKEN.attackDamage() / 4.0D);
            } catch (Throwable statError) {
                OreSpawnIntegrations.LOGGER.debug("MobStats.KRAKEN unavailable; using fallback grab damage", statError);
            }

            // Reel the victim in: velocity scaled by distance, capped so a
            // 24-block grab does not fling them past the player.
            Vec3 toPlayer = player.position().subtract(target.position());
            double dist = Math.max(toPlayer.length(), 0.001D);
            Vec3 pull = new Vec3(
                    toPlayer.x / dist * Math.min(dist * 0.22D, 1.8D),
                    Math.min(dist * 0.06D + 0.25D, 0.8D),
                    toPlayer.z / dist * Math.min(dist * 0.22D, 1.8D));
            target.setDeltaMovement(pull);
            target.hurtMarked = true; // sync the yank to clients (players included)
            target.hurt(world.damageSources().playerAttack(player), damage);

            if (world instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.SQUID_INK,
                        target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ(),
                        20, 0.4D, 0.4D, 0.4D, 0.05D);
            }
            world.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.SQUID_SQUIRT, SoundSource.PLAYERS, 1.0F, 0.6F);
        } catch (Throwable t) {
            OreSpawnIntegrations.LOGGER.error("Kraken tentacle grab failed - use skipped", t);
        }
    }

    @Override
    public int getDefaultCooldown() {
        return 120;
    }

    @Override
    public Item getIcon() {
        return Items.INK_SAC;
    }
}
