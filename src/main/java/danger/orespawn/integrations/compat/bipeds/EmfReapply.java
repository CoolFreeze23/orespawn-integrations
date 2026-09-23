package danger.orespawn.integrations.compat.bipeds;

import dev.kosmx.playerAnim.impl.animation.AnimationApplier;
import danger.orespawn.integrations.OreSpawnIntegrations;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import traben.entity_model_features.EMFAnimationApi;
import traben.entity_model_features.models.parts.EMFModelPart;
import traben.entity_model_features.models.parts.EMFModelPartRoot;

/**
 * Lays a Better Combat pose over a model that Entity Model Features animates, right after EMF's animation and before
 * the parts are drawn ({@link EmfAnimateHook}, reported from the end of {@code EMFModelPartRoot.animate()}). The pose is
 * computed against the parts as the animation pack left them, so an attack eases in from, and back out to, the pack's
 * own pose, and every axis Better Combat leaves off (the head's pitch, the legs while walking) keeps the pack's motion.
 *
 * <p>One model at a time: the renderer defers it while posing the entity (the bipeds' {@code setupAnim}, a player's
 * {@code RenderLivingEvent.Pre}); the first {@code animate()} of that model's root while EMF is animating that entity
 * applies it; every entity render ends by clearing it, so it never reaches another model or the first-person hands.
 * Referenced only when EMF, Better Combat and playerAnimator are loaded.</p>
 */
final class EmfReapply {

    private static LivingEntity entity;
    private static PlayerModel<?> model;
    private static EMFModelPartRoot root;
    private static AnimationApplier applier;
    private static boolean broken;
    private static boolean everApplied;
    private static int misses;
    private static final int MISSES_BEFORE_GIVING_UP = 200;

    private EmfReapply() {
    }

    static void install() {
        EmfAnimateHook.afterAnimate = EmfReapply::afterAnimate;
    }

    /**
     * Holds {@code pose} for {@code model} until EMF has animated it. False when EMF does not animate the model (no
     * animation pack for it), in which case the caller poses it at once.
     */
    static boolean defer(LivingEntity forEntity, PlayerModel<?> onModel, AnimationApplier pose) {
        if (broken || EmfAnimateHook.afterAnimate == null || !(onModel.body instanceof EMFModelPart part)) {
            return false;
        }
        final EMFModelPartRoot modelRoot = part.getRoot();
        if (modelRoot == null || !modelRoot.hasAnimation()) {
            return false;
        }
        entity = forEntity;
        model = onModel;
        root = modelRoot;
        applier = pose;
        return true;
    }

    static void clear() {
        entity = null;
        model = null;
        root = null;
        applier = null;
    }

    /**
     * At the end of an entity's render. A pose still held means EMF did not report animating the model; if that keeps
     * happening before EMF has ever reported one, the hook is not in place (another EMF version), and deferring would
     * only hide the attacks, so it stops: the pair are posed before the pack's animation again, as without this.
     */
    static void endOfRender() {
        if (root != null && !everApplied && ++misses > MISSES_BEFORE_GIVING_UP) {
            broken = true;
            OreSpawnIntegrations.LOGGER.warn("Bipeds compat: Entity Model Features never reported animating a model; "
                    + "Better Combat poses are no longer laid over the animation pack");
        }
        clear();
    }

    private static void afterAnimate(Object animated) {
        if (root == null || animated != root) {
            return;
        }
        final Object current = EMFAnimationApi.getCurrentEntity();
        if (current != null && current != entity) {
            return;
        }
        final PlayerModel<?> posed = model;
        final AnimationApplier pose = applier;
        clear();
        everApplied = true;
        try {
            BetterCombatSwings.poseOverPack(pose, posed);
        } catch (RuntimeException e) {
            broken = true;
            OreSpawnIntegrations.LOGGER.error("Bipeds compat: laying Better Combat's pose over Entity Model Features' "
                    + "animation failed; it is off until restart and the animation pack's pose shows", e);
        }
    }
}
