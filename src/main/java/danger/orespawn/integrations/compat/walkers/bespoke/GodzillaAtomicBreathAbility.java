package danger.orespawn.integrations.compat.walkers.bespoke;

import danger.orespawn.entity.BetterFireball;
import danger.orespawn.entity.Godzilla;
import danger.orespawn.integrations.OreSpawnIntegrations;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import tocraft.walkers.ability.ShapeAbility;

/**
 * Godzilla's R-key: atomic breath as a fire-line. Godzilla's real blast is a
 * {@link BetterFireball} sized by {@code setBig}/{@code setReallyBig} (beta.3
 * bytecode); the morph rakes a staggered line of them along the aim - three
 * small stream segments capped by a big terminal blast. {@code setNotMe()}
 * keeps the breath from cooking its owner, same flag the port itself uses.
 */
public final class GodzillaAtomicBreathAbility extends ShapeAbility<Godzilla> {

    private static final int SEGMENTS = 4;
    private static final double SEGMENT_SPACING = 2.5D;

    @Override
    public ResourceLocation getId() {
        return ResourceLocation.fromNamespaceAndPath(OreSpawnIntegrations.MODID, "godzilla_atomic_breath");
    }

    @Override
    public void onUse(Player player, Godzilla shape, Level world) {
        if (world.isClientSide()) {
            return;
        }
        try {
            Vec3 dir = player.getLookAngle();
            Vec3 eye = player.getEyePosition();
            for (int i = 0; i < SEGMENTS; i++) {
                BetterFireball blast = new BetterFireball(world, player, dir);
                if (i == SEGMENTS - 1) {
                    blast.setBig();
                } else {
                    blast.setSmall();
                }
                blast.setNotMe();
                double lead = 2.0D + i * SEGMENT_SPACING;
                blast.setPos(eye.x + dir.x * lead, eye.y + dir.y * lead, eye.z + dir.z * lead);
                world.addFreshEntity(blast);
            }

            if (world instanceof ServerLevel server) {
                for (int i = 1; i <= 6; i++) {
                    Vec3 p = eye.add(dir.scale(i));
                    server.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 6, 0.2D, 0.2D, 0.2D, 0.02D);
                }
            }
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 1.0F, 0.6F);
        } catch (Throwable t) {
            OreSpawnIntegrations.LOGGER.error("Godzilla atomic breath failed - use skipped", t);
        }
    }

    @Override
    public int getDefaultCooldown() {
        return 200;
    }

    @Override
    public Item getIcon() {
        return Items.FIRE_CHARGE;
    }
}
