package danger.orespawn.integrations.mixin;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Refurbished Furniture 1.0.22 (and every later branch) builds its creative-inventory filter buttons when the creative
 * screen's widgets are set up ({@code injectWidgets}, from Framework's {@code MODIFY_WIDGETS}), but switches tabs from
 * a render hook ({@code AFTER_DRAW}) that assumes they exist: {@code onSwitchCreativeTab} sets
 * {@code scrollUpButton.visible} with no check. When a creative screen draws before its widgets were set up for
 * Refurbished, the game crashes with "Cannot assign field visible because this.scrollUpButton is null" (Alvin on the
 * Legion, 2026-11-01). This skips the switch while the buttons don't exist yet; Refurbished's own widget setup calls
 * {@code onSwitchCreativeTab} again once it has built them, so the filters come back on the next setup.
 *
 * <p>Applied only when Refurbished Furniture is present (pseudo target, {@code require = 0}).</p>
 */
@Pseudo
@Mixin(targets = "com.mrcrayfish.furniture.refurbished.client.CreativeFilters", remap = false)
public abstract class RefurbishedCreativeFiltersMixin {

    @Shadow(remap = false)
    private AbstractWidget scrollUpButton;

    @Shadow(remap = false)
    private AbstractWidget scrollDownButton;

    @Inject(method = "onSwitchCreativeTab", at = @At("HEAD"), cancellable = true, require = 0)
    private void orespawn_integrations$waitForButtons(CreativeModeTab tab, CreativeModeInventoryScreen screen, CallbackInfo ci) {
        if (this.scrollUpButton == null || this.scrollDownButton == null) {
            ci.cancel();
        }
    }
}
