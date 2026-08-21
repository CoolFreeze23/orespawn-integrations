package danger.orespawn.integrations.compat.walkers.bespoke;

import danger.orespawn.entity.TheKing;
import danger.orespawn.entity.ThunderBolt;
import danger.orespawn.integrations.OreSpawnIntegrations;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import tocraft.walkers.ability.ShapeAbility;

/**
 * The King's R-key: a volley of the port's own {@link ThunderBolt}
 * projectiles - the same entity TheKing's AI fires (beta.3 bytecode:
 * construct, own, {@code shoot}). One dead-accurate center bolt flanked by two
 * scattered ones, mirroring his barrage pattern.
 */
public final class KingThunderVolleyAbility extends ShapeAbility<TheKing> {

    private static final int BOLTS = 3;

    @Override
    public ResourceLocation getId() {
        return ResourceLocation.fromNamespaceAndPath(OreSpawnIntegrations.MODID, "king_thunder_volley");
    }

    @Override
    public void onUse(Player player, TheKing shape, Level world) {
        if (world.isClientSide()) {
            return;
        }
        try {
            Vec3 dir = player.getLookAngle();
            for (int i = 0; i < BOLTS; i++) {
                // The shooter ctor spawns at the player's eye and sets ownership,
                // so damage credits the player.
                ThunderBolt bolt = new ThunderBolt(world, player);
                bolt.shoot(dir.x, dir.y, dir.z, 1.6F, i == 0 ? 0.0F : 8.0F);
                world.addFreshEntity(bolt);
            }
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.7F, 1.2F);
        } catch (Throwable t) {
            OreSpawnIntegrations.LOGGER.error("The King's thunder volley failed - use skipped", t);
        }
    }

    @Override
    public int getDefaultCooldown() {
        return 160;
    }

    @Override
    public Item getIcon() {
        return Items.LIGHTNING_ROD;
    }
}
