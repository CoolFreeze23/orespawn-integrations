package danger.orespawn.integrations.mixin;

import com.mojang.blaze3d.vertex.PoseStack;

import danger.orespawn.integrations.compat.bipeds.BipedItemHook;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Better Combat's attacks turn the weapon in the hand as well as the arm (the animations' {@code rightItem} /
 * {@code leftItem} parts); playerAnimator applies that turn only to players, at this same point. For the Girlfriend and
 * the Boyfriend the bipeds compat supplies it ({@link BipedItemHook}), so a two-handed weapon stays gripped the right way
 * round through the swing. Does nothing for any other entity, or when the hook is not installed.
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerSwingMixin {

    @Inject(method = "renderArmWithItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"),
            require = 0)
    private void orespawn_integrations$swingItem(LivingEntity entity, ItemStack stack, ItemDisplayContext context, HumanoidArm arm,
                                                 PoseStack poseStack, MultiBufferSource buffer, int light, CallbackInfo ci) {
        final BipedItemHook.Transform transform = BipedItemHook.transform;
        if (transform != null) {
            transform.apply(entity, arm, poseStack);
        }
    }
}
