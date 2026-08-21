package danger.orespawn.integrations.compat.creatures;

import java.util.List;

import doggytalents.DoggyTalents;
import doggytalents.common.entity.Dog;
import doggytalents.common.entity.ai.triggerable.DogBackFlipAction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * "Victory Backflips" + "Big Game Hound" (alive-world wave): when a
 * {@code #c:bosses} mob falls, every Doggy Talents Next dog within 48 blocks
 * does a backflip (staggered a second or two so a pack of dogs reads as a
 * wave of flips). If a dog with the Guard Dog talent was within 32 blocks of
 * the kill and its owner stood within 32 blocks too, the owner is granted the
 * hidden "Big Game Hound" advancement
 * ({@code orespawn_integrations:big_game/big_game_hound}, criterion
 * {@code witnessed}).
 *
 * <p>The boss guard clauses (client-side check + {@code #c:bosses} tag check)
 * copy the house pattern shared by WalkersCompat and the alive package's
 * CelebrationHandler; like CelebrationHandler this listener is tag-only, so
 * the dogs flip exactly when the addon's boss celebration fires. This class
 * does NOT touch CelebrationHandler; it is its own parallel LivingDeathEvent
 * listener.</p>
 *
 * <p><b>API citations (policy 4)</b>, all javap-verified against
 * {@code DoggyTalentsNext-1.21.1-1.19.0.jar}:
 * <ul>
 *   <li>{@code doggytalents.common.entity.Dog} — public class (registered as
 *       entity id {@code doggytalents:dog}; {@code DoggyEntityTypes} static
 *       init, bytecode {@code ldc "doggytalents"} / {@code ldc "dog"});
 *       {@code public boolean triggerAction(TriggerableAction)}.</li>
 *   <li>{@code doggytalents.common.entity.ai.triggerable.DogBackFlipAction}
 *       — public class extending {@code AnimationAction}, public ctor
 *       {@code DogBackFlipAction(Dog)}. This is DTN's OWN backflip trigger:
 *       {@code doggytalents.common.event.EventHandler} bytecode does exactly
 *       {@code dog.triggerAction(new DogBackFlipAction(dog))} server-side, so
 *       we ride the supported path (the action plays the shipped
 *       {@code assets/doggytalents/doggytalents/dog_animations/backflip.json}
 *       animation and syncs it to clients itself; no fallback spin needed).</li>
 *   <li>{@code doggytalents.api.feature.IDog.getDogLevel(Supplier<? extends Talent>)}
 *       — public default (implemented by {@code Dog} via {@code AbstractDog});
 *       returns 0 when the talent is untrained.</li>
 *   <li>{@code doggytalents.DoggyTalents.GUARD_DOG} — public static
 *       {@code Supplier<Talent>}.</li>
 * </ul>
 * Verified against Doggy Talents Next 1.19.0; the triggerable-action surface
 * is DTN-internal-but-stable — re-verify on a DTN major bump.
 *
 * <p>Only ever classloaded when the doggytalents mod is present — the main mod
 * class invokes {@link #init(IEventBus)} reflectively behind
 * {@code ModList.isLoaded("doggytalents")}. All handler bodies are try/catch +
 * log-once (house defensive style); everything runs server-side, so no
 * client-dist guard is needed. The advancement grant fails soft (log-once
 * no-op) when the advancement JSON was stripped by its conditions.</p>
 */
public final class DoggyTalentsCompat {

    private static final double BACKFLIP_RADIUS = 48.0;
    private static final double HOUND_RADIUS = 32.0;
    private static final String HOUND_ADVANCEMENT = "big_game/big_game_hound";

    private DoggyTalentsCompat() {
    }

    public static void init(IEventBus modBus) {
        CreaturesSupport.ensureHooked();
        NeoForge.EVENT_BUS.addListener(DoggyTalentsCompat::onBossDeath);
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
            List<Dog> dogs = level.getEntitiesOfClass(Dog.class,
                    died.getBoundingBox().inflate(BACKFLIP_RADIUS, BACKFLIP_RADIUS / 2, BACKFLIP_RADIUS));
            if (dogs.isEmpty()) {
                return;
            }
            MinecraftServer server = level.getServer();
            RandomSource random = level.random;
            double houndRadiusSq = HOUND_RADIUS * HOUND_RADIUS;
            for (Dog dog : dogs) {
                // (a) Victory backflip — staggered; triggerAction quietly
                // returns false if the dog is busy/incapacitated, which is
                // exactly the no-punish behavior we want.
                CreaturesSupport.schedule(server, random.nextInt(30), () -> {
                    if (dog.isAlive() && dog.level() == level) {
                        dog.triggerAction(new DogBackFlipAction(dog));
                    }
                });

                // (b) Big Game Hound — Guard Dog talent, dog AND owner at the
                // kill site. The owner need not be the killer (reward, never
                // punish: a hunting party all standing there counts).
                if (dog.distanceToSqr(died) <= houndRadiusSq
                        && dog.getDogLevel(DoggyTalents.GUARD_DOG) > 0
                        && dog.getOwner() instanceof ServerPlayer owner
                        && owner.distanceToSqr(died) <= houndRadiusSq) {
                    CreaturesSupport.grantWitnessed(owner, HOUND_ADVANCEMENT);
                }
            }
        } catch (Throwable t) {
            CreaturesSupport.logOnce("doggytalents_celebration", t);
        }
    }
}
