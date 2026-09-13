package danger.orespawn.integrations.mixin;

import net.minecraft.client.resources.language.I18n;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Neo Origins draws the label of every resource bar ("Energy", "Essence", "Stamina",
 * and this mod's "Tide", "Heat", "Fox-fire", ...) straight from the {@code hud_render.label}
 * string of the power JSON: the server sends it as-is and the client renders the raw
 * string, so it never passes through the language files and stays English in every
 * language.
 *
 * This looks the label up as {@code orespawn_integrations.hud_label.<label>} (lower-cased,
 * runs of anything but letters and digits folded to a single underscore, so "Fox-fire"
 * becomes {@code fox_fire}) and substitutes the translation when the current language
 * defines it. Languages without the key keep the original text.
 *
 * Only applied when the target record loads, i.e. when Neo Origins is present on the
 * client.
 */
@Mixin(targets = "com.cyberday1.neoorigins.client.ClientResourceState$ResourceEntry", remap = false)
public class NeoOriginsHudLabelMixin {

    private static final String KEY_PREFIX = "orespawn_integrations.hud_label.";

    @Inject(method = "label()Ljava/lang/String;", at = @At("RETURN"), cancellable = true, require = 0)
    private void orespawn_integrations$translateLabel(CallbackInfoReturnable<String> cir) {
        String label = cir.getReturnValue();
        if (label == null || label.isEmpty()) {
            return;
        }
        String key = KEY_PREFIX + slug(label);
        if (I18n.exists(key)) {
            cir.setReturnValue(I18n.get(key));
        }
    }

    private static String slug(String label) {
        StringBuilder out = new StringBuilder(label.length());
        boolean pendingSeparator = false;
        for (int i = 0; i < label.length(); i++) {
            char c = Character.toLowerCase(label.charAt(i));
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                if (pendingSeparator && out.length() > 0) {
                    out.append('_');
                }
                out.append(c);
                pendingSeparator = false;
            } else {
                pendingSeparator = true;
            }
        }
        return out.toString();
    }
}
