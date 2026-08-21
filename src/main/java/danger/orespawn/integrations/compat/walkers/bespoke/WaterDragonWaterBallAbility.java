package danger.orespawn.integrations.compat.walkers.bespoke;

import danger.orespawn.entity.WaterBall;
import danger.orespawn.entity.WaterDragon;
import danger.orespawn.integrations.OreSpawnIntegrations;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import tocraft.walkers.ability.ShapeAbility;

/**
 * Water Dragon's R-key: a twin {@link WaterBall} volley. The audit notes the
 * ported WaterDragon lost its ranged WaterBall volleys (AUDIT_INVENTORY) - the
 * morph gives that attack back using the port's own projectile, whose shooter
 * ctor spawns at the eye with ownership set.
 */
public final class WaterDragonWaterBallAbility extends ShapeAbility<WaterDragon> {

    private static final int BALLS = 2;

    @Override
    public ResourceLocation getId() {
        return ResourceLocation.fromNamespaceAndPath(OreSpawnIntegrations.MODID, "water_dragon_water_ball");
    }

    @Override
    public void onUse(Player player, WaterDragon shape, Level world) {
        if (world.isClientSide()) {
            return;
        }
        try {
            for (int i = 0; i < BALLS; i++) {
                WaterBall ball = new WaterBall(world, player);
                ball.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.5F, i * 4.0F);
                world.addFreshEntity(ball);
            }
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.DOLPHIN_SPLASH, SoundSource.PLAYERS, 1.0F, 1.0F);
        } catch (Throwable t) {
            OreSpawnIntegrations.LOGGER.error("Water Dragon water ball volley failed - use skipped", t);
        }
    }

    @Override
    public int getDefaultCooldown() {
        return 60;
    }

    @Override
    public Item getIcon() {
        return Items.HEART_OF_THE_SEA;
    }
}
