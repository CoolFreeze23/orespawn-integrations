package danger.orespawn.integrations.compat.bipeds;

import javax.annotation.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import danger.orespawn.ModEntities;
import danger.orespawn.entity.Boyfriend;
import danger.orespawn.entity.Girlfriend;
import danger.orespawn.entity.client.BoyfriendRenderer;
import danger.orespawn.entity.client.GirlfriendRenderer;
import danger.orespawn.integrations.OreSpawnIntegrations;
import danger.orespawn.integrations.config.IntegrationsConfig;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Client half of {@link BipedsCompat}. The companion loads after OreSpawn ({@code ordering = "AFTER"}), so its
 * {@code RegisterRenderers} listener runs after the port's and its registrations for the two types replace the port's
 * (the dispatcher's provider map keeps the last one). Referenced only from a client branch.
 */
final class BipedsClient {

    /** The swing applier, set when Better Combat and playerAnimator are loaded and the swings are enabled; else null. */
    @Nullable
    static SwingHook swingHook;

    /** Better Combat's attack on a biped drawn with the player model. */
    interface SwingHook {
        /** In {@code setupAnim}, after vanilla's pose: lay an attack in progress over the parts. */
        void apply(LivingEntity entity, PlayerModel<?> model, float ageInTicks);

        /** At the end of {@code setupRotations}: the attack's whole-body motion; {@code scale} is the renderer's scale. */
        void bodyTransform(LivingEntity entity, PoseStack poseStack, float partialTick, float scale);
    }

    private BipedsClient() {
    }

    static void register(IEventBus modBus, boolean emf, boolean betterCombat) {
        modBus.addListener(BipedsClient::onRegisterRenderers);
        if (betterCombat) {
            BetterCombatSwings.register(modBus, emf);
        }
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        if (IntegrationsConfig.SPEC.isLoaded() && !IntegrationsConfig.playerModelBipeds.getAsBoolean()) {
            OreSpawnIntegrations.LOGGER.info("Bipeds compat: player_model_bipeds is off, the pair keeps OreSpawn's rigs");
            return;
        }
        // her 1.7.10 model draws three-pixel arms (ModelGirlfriend), his four (ModelBoyfriend); the shadows are the port's
        event.registerEntityRenderer(ModEntities.GIRLFRIEND.get(), context -> new PlayerModelBipedRenderer<Girlfriend>(context, true, 0.5F,
                GirlfriendRenderer::textureFor, girlfriend -> girlfriend.isValentineAngry() ? 5.0F : 1.0F));
        event.registerEntityRenderer(ModEntities.BOYFRIEND.get(), context -> new PlayerModelBipedRenderer<Boyfriend>(context, false,
                BoyfriendRenderer.SHADOW, BoyfriendRenderer::textureFor, boyfriend -> BoyfriendRenderer.SCALE));
    }
}
