package danger.orespawn.integrations.compat.bipeds;

import java.util.function.Consumer;

import danger.orespawn.integrations.OreSpawnIntegrations;

/**
 * Where {@code EmfModelPartRootMixin} reports that Entity Model Features has just animated a model (the end of
 * {@code EMFModelPartRoot.animate()}, which runs as the model starts drawing, after {@code setupAnim}). Null unless the
 * bipeds compat installed a listener ({@link EmfReapply}); the mixin references nothing else of this mod, so it is safe
 * with or without Better Combat.
 */
public final class EmfAnimateHook {

    /** Receives the {@code EMFModelPartRoot} just animated. Render thread only. */
    public static volatile Consumer<Object> afterAnimate;

    private EmfAnimateHook() {
    }

    /** A library the listener needs failed to link: drop the listener rather than fail every frame. */
    public static void disable(Throwable cause) {
        if (afterAnimate != null) {
            afterAnimate = null;
            OreSpawnIntegrations.LOGGER.error("Bipeds compat: the Entity Model Features hook failed and is off until restart", cause);
        }
    }
}
