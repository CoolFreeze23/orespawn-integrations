package danger.orespawn.integrations.mixin;

import java.util.function.Consumer;

import danger.orespawn.integrations.compat.bipeds.EmfAnimateHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Entity Model Features animates a model when the model starts drawing ({@code EMFModelPartWithState.render} calls
 * {@code EMFModelPartRoot.animate()} before the part's cubes): after {@code setupAnim}, so a player animation pack
 * overwrites whatever {@code setupAnim} posed, Better Combat's attacks included. Its API offers a per-part pause, but in
 * 3.2.4 the compiled animation never reads the paused parts. This reports the end of {@code animate()} so the bipeds
 * compat can lay a Better Combat attack or weapon pose back over the torso and arms before they are drawn
 * ({@link EmfAnimateHook}).
 *
 * <p>Applied only when Entity Model Features is present (pseudo target, {@code require = 0}); does nothing unless the
 * bipeds compat installed its listener.</p>
 */
@Pseudo
@Mixin(targets = "traben.entity_model_features.models.parts.EMFModelPartRoot", remap = false)
public abstract class EmfModelPartRootMixin {

    @Inject(method = "animate()V", at = @At("TAIL"), require = 0)
    private void orespawn_integrations$afterAnimate(CallbackInfo ci) {
        final Consumer<Object> listener = EmfAnimateHook.afterAnimate;
        if (listener != null) {
            try {
                listener.accept(this);
            } catch (LinkageError e) {
                EmfAnimateHook.disable(e);
            }
        }
    }
}
