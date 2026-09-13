package danger.orespawn.integrations.mixin;

import net.minecraft.client.resources.language.I18n;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * The Slime origin's moisture bar is the one Neo Origins HUD bar that does not come from
 * a resource power: its "Moisture" label is a string constant inside the overlay class.
 * Same treatment as {@link NeoOriginsHudLabelMixin}: the constant is replaced with the
 * {@code orespawn_integrations.hud_label.moisture} translation when the current language
 * defines it, and left alone otherwise.
 *
 * Only applied when the target class loads, i.e. when Neo Origins is present on the
 * client.
 */
@Mixin(targets = "com.cyberday1.neoorigins.client.MoistureHudOverlay", remap = false)
public class NeoOriginsMoistureLabelMixin {

    @ModifyConstant(method = "onRenderGui", constant = @Constant(stringValue = "Moisture"), require = 0)
    private static String orespawn_integrations$translateMoistureLabel(String label) {
        String key = "orespawn_integrations.hud_label.moisture";
        return I18n.exists(key) ? I18n.get(key) : label;
    }
}
