package danger.orespawn.integrations.compat.bipeds;

import java.util.function.Function;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;

/**
 * A biped drawn with the vanilla player model: the {@code player} or {@code player_slim} layer (the layer Entity Model
 * Features keys a player animation pack's model on), the player's armour layers, the port's own texture switch with
 * its 64x32 sheet converted to the player layout ({@link LegacySkins}), and the port's scale. {@code HumanoidMobRenderer}
 * adds the held-item, custom-head and elytra layers as the port's classic renderer has them.
 */
final class PlayerModelBipedRenderer<T extends Mob> extends HumanoidMobRenderer<T, PlayerModel<T>> {

    /** The port's scale for an entity (the valentine Girlfriend's 5x). */
    interface Scale<T> {
        float of(T entity);
    }

    private final boolean slim;
    private final Function<T, ResourceLocation> legacyTexture;
    private final Scale<T> scale;

    PlayerModelBipedRenderer(EntityRendererProvider.Context context, boolean slim, float shadow,
                             Function<T, ResourceLocation> legacyTexture, Scale<T> scale) {
        super(context, new BipedPlayerModel<>(context.bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim), shadow);
        this.slim = slim;
        this.legacyTexture = legacyTexture;
        this.scale = scale;
        this.addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidArmorModel<>(context.bakeLayer(slim ? ModelLayers.PLAYER_SLIM_INNER_ARMOR : ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidArmorModel<>(context.bakeLayer(slim ? ModelLayers.PLAYER_SLIM_OUTER_ARMOR : ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return LegacySkins.upgraded(this.legacyTexture.apply(entity), this.slim);
    }

    @Override
    protected void setupRotations(T entity, PoseStack poseStack, float bob, float yBodyRot, float partialTick, float entityScale) {
        super.setupRotations(entity, poseStack, bob, yBodyRot, partialTick, entityScale);
        final BipedsClient.SwingHook hook = BipedsClient.swingHook;
        if (hook != null) {
            hook.bodyTransform(entity, poseStack, partialTick, this.scale.of(entity));
        }
    }

    @Override
    protected void scale(T entity, PoseStack poseStack, float partialTick) {
        final float s = this.scale.of(entity);
        if (s != 1.0F) {
            poseStack.scale(s, s, s);
        }
        super.scale(entity, poseStack, partialTick);
    }
}
