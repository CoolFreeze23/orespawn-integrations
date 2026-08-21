package danger.orespawn.integrations.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.InputEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Portal Gun (xhyrom port 6.1.3) fires its "attack-side" portal from a raw
 * {@code event.getButton() == 0} check in its {@code InputEvent.MouseButton.Pre}
 * listener, ignoring the player's actual Attack keybind
 * (upstream issues xhyrom/portal-gun-mod#7 and #8).
 *
 * With remapped mouse buttons (e.g. attack on mouse.right, use on mouse.left)
 * this breaks both portals: the physical left button is swallowed by the raw
 * handler (cancelling the event before the Use keybind can fire the other
 * portal), while the real attack button just performs a melee swing.
 *
 * This redirect feeds the handler {@code 0} when the pressed button matches the
 * Attack keybind and a non-zero sentinel otherwise, so the handler triggers on
 * the player's actual attack button and leaves every other button alone. If
 * Attack is not mouse-bound, the original raw-button behaviour is preserved.
 *
 * Only applied when the target class loads, i.e. when the portalgun mod is
 * present on the client.
 */
@Mixin(targets = "tk.meowmc.portalgun.client.PortalGunClient", remap = false)
public class PortalGunKeybindFixMixin {

    @Redirect(
            method = "lambda$new$5(Lnet/neoforged/neoforge/client/event/InputEvent$MouseButton$Pre;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/neoforged/neoforge/client/event/InputEvent$MouseButton$Pre;getButton()I",
                    ordinal = 0
            )
    )
    private static int orespawn_integrations$respectAttackKeybind(InputEvent.MouseButton.Pre event) {
        KeyMapping attack = Minecraft.getInstance().options.keyAttack;
        if (attack.getKey().getType() == InputConstants.Type.MOUSE) {
            return attack.matchesMouse(event.getButton()) ? 0 : -1;
        }
        return event.getButton();
    }
}
