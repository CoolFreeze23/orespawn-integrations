package danger.orespawn.integrations.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import danger.orespawn.integrations.world.regions.TitleFit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Keeps titles and subtitles on the screen ({@link TitleFit}): region names and their subtitles, and any other mod's,
 * shrink only when they wouldn't fit. "The Greenwood"'s subtitle ran off both edges on the Legion Go S at the auto GUI
 * scale. Vanilla's two {@code scale} calls in {@code renderTitle} are the title's (4x) and the subtitle's (2x).
 */
@Mixin(Gui.class)
public abstract class GuiTitleFitMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    private Component title;

    @Shadow
    private Component subtitle;

    @Shadow
    public abstract Font getFont();

    @WrapOperation(method = "renderTitle", require = 0, at = @At(value = "INVOKE", ordinal = 0,
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V"))
    private void orespawn_integrations$fitTitle(PoseStack pose, float x, float y, float z, Operation<Void> original) {
        float s = orespawn_integrations$fit(this.title, x);
        original.call(pose, s, s, s);
    }

    @WrapOperation(method = "renderTitle", require = 0, at = @At(value = "INVOKE", ordinal = 1,
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V"))
    private void orespawn_integrations$fitSubtitle(PoseStack pose, float x, float y, float z, Operation<Void> original) {
        float s = orespawn_integrations$fit(this.subtitle, x);
        original.call(pose, s, s, s);
    }

    private float orespawn_integrations$fit(Component line, float base) {
        return line == null ? base : TitleFit.scale(base, getFont().width(line), this.minecraft.getWindow().getGuiScaledWidth());
    }
}
