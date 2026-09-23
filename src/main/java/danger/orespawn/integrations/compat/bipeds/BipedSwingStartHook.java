package danger.orespawn.integrations.compat.bipeds;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;

/**
 * Where {@code LivingEntitySwingMixin} reports each {@code LivingEntity.swing} call, i.e. on the client each swing the
 * server announces. Up to OreSpawn 2.0.0-beta.9 the Girlfriend and the Boyfriend never advanced their swing timer
 * (vanilla ticks it only for monsters and players, and the pair did not tick it themselves), so after their first swing
 * {@code swinging} stayed true and a swing could not be told from the fields; the call itself marks it, on every
 * version. Null unless the bipeds compat installed it.
 */
public final class BipedSwingStartHook {

    public interface Listener {
        void swung(LivingEntity entity, InteractionHand hand);
    }

    public static volatile Listener listener;

    private BipedSwingStartHook() {
    }
}
