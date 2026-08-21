package danger.orespawn.integrations.compat.creatures;

import java.util.List;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import tallestegg.guardvillagers.common.entities.Guard;

import it.unimi.dsi.fastutil.ints.IntList;

/**
 * "Guards Join the Celebration" (alive-world wave): when a {@code #c:bosses}
 * mob falls, every Guard Villagers guard within 48 blocks salutes — crossbow
 * guards fire celebratory firework volleys skyward, shield guards raise their
 * shields for a few seconds, and everyone else does a happy hop with villager
 * particles. Pure reward/ambience: no damage, no aggro, no AI rewrites.
 *
 * <p>The boss guard clauses (client-side check + {@code #c:bosses} tag check)
 * copy the house pattern shared by WalkersCompat and the alive package's
 * CelebrationHandler; like CelebrationHandler — and unlike WalkersCompat's
 * orespawn-namespace-restricted shape absorption — this listener is tag-only,
 * so the guards celebrate exactly when the addon's boss celebration fires.
 * This class deliberately does NOT touch CelebrationHandler; it is its own
 * parallel LivingDeathEvent listener.</p>
 *
 * <p><b>API citations (policy 4)</b>, all javap-verified against
 * {@code guardvillagers-2.4.10-1.21.1.jar}:
 * <ul>
 *   <li>{@code tallestegg.guardvillagers.common.entities.Guard} — public class
 *       extending {@code PathfinderMob}, implements {@code CrossbowAttackMob};
 *       registered as entity id {@code guardvillagers:guard}
 *       ({@code GuardEntityType} static init, bytecode {@code ldc "guardvillagers"}
 *       / {@code ldc "guard"}).</li>
 *   <li>{@code Guard.startUsingItem(InteractionHand)} — public override; bytecode
 *       shows it calls super then applies the mod's own shield-walk slowdown when
 *       the stack {@code canPerformAction(ItemAbilities.SHIELD_BLOCK)}, i.e. this
 *       is the mod's supported "raise shield" entry point.</li>
 *   <li>{@code Guard.stopUsingItem()} — public override (removes the slowdown).</li>
 *   <li>{@code LivingEntity.getOffhandItem()/getMainHandItem()/isUsingItem()/
 *       getUsedItemHand()} — vanilla surface used for the hand checks.</li>
 * </ul>
 * Verified against Guard Villagers 2.4.10; re-verify the Guard class package on
 * a major-version bump.
 *
 * <p>Only ever classloaded when the guardvillagers mod is present — the main mod
 * class invokes {@link #init(IEventBus)} reflectively behind
 * {@code ModList.isLoaded("guardvillagers")}. All handler bodies are
 * try/catch + log-once (house defensive style); everything runs server-side,
 * so no client-dist guard is needed.</p>
 */
public final class GuardVillagersCompat {

    private static final double CELEBRATE_RADIUS = 48.0;
    private static final int SHIELD_RAISE_TICKS = 60;

    private GuardVillagersCompat() {
    }

    public static void init(IEventBus modBus) {
        CreaturesSupport.ensureHooked();
        NeoForge.EVENT_BUS.addListener(GuardVillagersCompat::onBossDeath);
    }

    private static void onBossDeath(LivingDeathEvent event) {
        try {
            LivingEntity died = event.getEntity();
            if (died.level().isClientSide()) {
                return;
            }
            if (!died.getType().is(CreaturesSupport.BOSSES_TAG)) {
                return;
            }
            if (!(died.level() instanceof ServerLevel level)) {
                return;
            }
            List<Guard> guards = level.getEntitiesOfClass(Guard.class,
                    died.getBoundingBox().inflate(CELEBRATE_RADIUS, CELEBRATE_RADIUS / 2, CELEBRATE_RADIUS));
            if (guards.isEmpty()) {
                return;
            }
            MinecraftServer server = level.getServer();
            RandomSource random = level.random;
            for (Guard guard : guards) {
                if (guard.getMainHandItem().getItem() instanceof CrossbowItem) {
                    // Celebratory volley: 2-3 fireworks, staggered so a garrison
                    // reads as rolling salutes rather than one synchronized blast.
                    int shots = 2 + random.nextInt(2);
                    for (int i = 0; i < shots; i++) {
                        CreaturesSupport.schedule(server, 20 + i * 25 + random.nextInt(15),
                                () -> volleyRocket(level, guard));
                    }
                } else if (guard.getOffhandItem().canPerformAction(ItemAbilities.SHIELD_BLOCK)) {
                    // Shield salute: raise for ~3s, then lower. The lower step
                    // re-checks the used hand so a genuine combat block that
                    // started meanwhile is the one we leave alone if hands moved.
                    int raiseAt = random.nextInt(10);
                    CreaturesSupport.schedule(server, raiseAt, () -> {
                        if (guard.isAlive() && !guard.isUsingItem()) {
                            guard.startUsingItem(InteractionHand.OFF_HAND);
                        }
                    });
                    CreaturesSupport.schedule(server, raiseAt + SHIELD_RAISE_TICKS + random.nextInt(20), () -> {
                        if (guard.isAlive() && guard.isUsingItem()
                                && guard.getUsedItemHand() == InteractionHand.OFF_HAND) {
                            guard.stopUsingItem();
                        }
                    });
                } else {
                    // Unarmed-for-celebration fallback: happy hop + particles.
                    CreaturesSupport.schedule(server, 10 + random.nextInt(20), () -> cheer(level, guard));
                }
            }
        } catch (Throwable t) {
            CreaturesSupport.logOnce("guardvillagers_celebration", t);
        }
    }

    /** One celebratory rocket from the guard's position + the crossbow report. */
    private static void volleyRocket(ServerLevel level, Guard guard) {
        if (!guard.isAlive() || guard.level() != level) {
            return;
        }
        level.playSound(null, guard.getX(), guard.getY(), guard.getZ(),
                SoundEvents.CROSSBOW_SHOOT, SoundSource.NEUTRAL,
                1.0F, 1.0F / (level.random.nextFloat() * 0.4F + 0.8F));
        CreaturesSupport.launchRocket(level, guard.getX(), guard.getY(), guard.getZ(),
                IntList.of(CreaturesSupport.GOLD, CreaturesSupport.RED));
    }

    private static void cheer(ServerLevel level, Guard guard) {
        if (!guard.isAlive() || guard.level() != level) {
            return;
        }
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                guard.getX(), guard.getY() + 1.2, guard.getZ(), 6, 0.4, 0.4, 0.4, 0.02);
        if (guard.onGround()) {
            guard.setDeltaMovement(guard.getDeltaMovement().add(0.0, 0.35, 0.0));
            guard.hasImpulse = true;
        }
    }
}
