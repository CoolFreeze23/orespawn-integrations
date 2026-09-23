package danger.orespawn.integrations.compat.bipeds;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

/**
 * Where {@code ItemInHandLayerSwingMixin} asks for the held item's turn in a Better Combat attack, just before the
 * held-item layer draws the item in the hand (the point playerAnimator uses for a player's). Null unless the bipeds
 * compat installed it; the mixin references nothing else of this mod.
 */
public final class BipedItemHook {

    /** Turns the item drawn in {@code arm}'s hand; called only while the layer renders a held item. */
    public interface Transform {
        void apply(LivingEntity entity, HumanoidArm arm, PoseStack poseStack);
    }

    public static volatile Transform transform;

    private BipedItemHook() {
    }
}
