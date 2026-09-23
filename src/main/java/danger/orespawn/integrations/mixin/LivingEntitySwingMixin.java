package danger.orespawn.integrations.mixin;

import danger.orespawn.integrations.compat.bipeds.BipedSwingStartHook;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reports each swing to the bipeds compat ({@link BipedSwingStartHook}), which starts a Better Combat attack for the
 * Girlfriend or the Boyfriend on the client when the server's animate packet arrives. Up to OreSpawn 2.0.0-beta.9 their
 * swing timer never advanced, so the packet is the one sign of a new swing on every version. Does nothing when the hook
 * is not installed.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntitySwingMixin {

    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Z)V", at = @At("HEAD"), require = 0)
    private void orespawn_integrations$reportSwing(InteractionHand hand, boolean updateSelf, CallbackInfo ci) {
        final BipedSwingStartHook.Listener listener = BipedSwingStartHook.listener;
        if (listener != null) {
            listener.swung((LivingEntity) (Object) this, hand);
        }
    }
}
