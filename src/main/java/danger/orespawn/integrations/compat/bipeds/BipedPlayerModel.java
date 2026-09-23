package danger.orespawn.integrations.compat.bipeds;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;

/**
 * The vanilla player model with a Better Combat attack in progress laid over vanilla's pose, through
 * {@link BipedsClient#swingHook} (null unless Better Combat is present and the swings are enabled).
 */
final class BipedPlayerModel<T extends LivingEntity> extends PlayerModel<T> {

    BipedPlayerModel(ModelPart root, boolean slim) {
        super(root, slim);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        final BipedsClient.SwingHook hook = BipedsClient.swingHook;
        if (hook != null) {
            hook.apply(entity, this, ageInTicks);
        }
    }
}
