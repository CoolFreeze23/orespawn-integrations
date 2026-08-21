package danger.orespawn.integrations.compat.arsnouveau;

import com.mojang.blaze3d.vertex.PoseStack;

import danger.orespawn.entity.client.GirlfriendRenderer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Client-only half of {@link ArsFamiliarCompat}: renders the Girlfriend
 * familiar by reusing the port's own girlfriend assets instead of shipping a
 * duplicate model. The port's Girlfriend is a vanilla humanoid, not geckolib
 * (mirror entity/client/GirlfriendRenderer.java: {@code HumanoidMobRenderer}
 * over {@code ModelGirlfriend extends HumanoidModel<Girlfriend>}, layer
 * {@code orespawn:girlfriend/main} registered in
 * OreSpawnClient.registerLayerDefinitions:351) — so this renderer bakes the
 * same {@link GirlfriendRenderer#MODEL_LAYER} into a plain
 * {@code HumanoidModel<FamiliarGirlfriend>} (ModelGirlfriend adds no members
 * beyond its {@code createBodyLayer}, so the baked geometry is identical;
 * bakeLayer resolves by ModelLayerLocation equality regardless of which mod
 * registered it) and pulls the skin texture from the entity. AN familiars are
 * shoulder-height critters (its own are registered 0.5x0.5, ModEntities
 * bytecode, ars_nouveau-1.21.1-5.13.0), so she renders at 0.4 scale — matching
 * the 0.25x0.7 hitbox in {@link ArsFamiliarCompat}.
 *
 * <p>Referenced only from a {@code FMLEnvironment.dist.isClient()} branch so
 * this class is never classloaded on a dedicated server (house idiom,
 * {@code compat/travelersbackpack/OreSpawnPacksClient}).</p>
 */
final class ArsFamiliarClient {

    private ArsFamiliarClient() {
    }

    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ArsFamiliarCompat.FAMILIAR_GIRLFRIEND.get(),
                FamiliarGirlfriendRenderer::new);
    }

    static final class FamiliarGirlfriendRenderer
            extends HumanoidMobRenderer<FamiliarGirlfriend, HumanoidModel<FamiliarGirlfriend>> {

        private static final float SCALE = 0.4f;

        FamiliarGirlfriendRenderer(EntityRendererProvider.Context context) {
            // Shadow 0.5f like the port renderer, scaled with the body.
            super(context, new HumanoidModel<>(context.bakeLayer(GirlfriendRenderer.MODEL_LAYER)),
                    0.5f * SCALE);
        }

        @Override
        protected void scale(FamiliarGirlfriend entity, PoseStack poseStack, float partialTick) {
            poseStack.scale(SCALE, SCALE, SCALE);
            super.scale(entity, poseStack, partialTick);
        }

        @Override
        public ResourceLocation getTextureLocation(FamiliarGirlfriend entity) {
            return entity.getTexture();
        }
    }
}
