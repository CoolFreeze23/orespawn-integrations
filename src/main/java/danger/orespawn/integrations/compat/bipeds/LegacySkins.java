package danger.orespawn.integrations.compat.bipeds;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;

import danger.orespawn.integrations.OreSpawnIntegrations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * The port's biped sheets are 1.7.10's 64x32 humanoid layout, in which the left arm and leg reuse the right ones'
 * texture, mirrored by the model. The player model takes a 64x64 sheet with the left limbs in their own place and an
 * overlay layer below. Each sheet is converted once, as it is first drawn, into a texture registered under this mod's
 * namespace: the top half copied, each right limb's box copied into the left limb's place mirrored the way the old
 * model mirrored it (the faces flipped, the two sides swapped; vanilla's legacy-skin conversion does the same for the
 * four-pixel arm, the three-pixel slim arm is handled by the same face rule), the overlay left clear. The sheet's own
 * alpha is kept (the port draws it cutout, not as a player skin). The texture re-reads its source on every resource
 * reload, so a resource pack that retextures the pair is followed. A sheet that is already 64x64 is used as it is.
 */
final class LegacySkins {

    private static final Map<ResourceLocation, ResourceLocation> WIDE = new HashMap<>();
    private static final Map<ResourceLocation, ResourceLocation> SLIM = new HashMap<>();

    private LegacySkins() {
    }

    static ResourceLocation upgraded(ResourceLocation legacy, boolean slim) {
        final Map<ResourceLocation, ResourceLocation> cache = slim ? SLIM : WIDE;
        final ResourceLocation known = cache.get(legacy);
        if (known != null) {
            return known;
        }
        final ResourceLocation converted = ResourceLocation.fromNamespaceAndPath(OreSpawnIntegrations.MODID,
                "player_model/" + (slim ? "slim/" : "wide/") + legacy.getNamespace() + "/" + legacy.getPath());
        Minecraft.getInstance().getTextureManager().register(converted, new UpgradedSkinTexture(legacy, slim));
        cache.put(legacy, converted);
        return converted;
    }

    /** The 64x32 sheet in the player's 64x64 layout; a sheet of any other size is returned as it is. */
    static NativeImage toPlayerLayout(NativeImage source, boolean slim) {
        if (source.getWidth() != 64 || source.getHeight() != 32) {
            return source;
        }
        final NativeImage out = new NativeImage(64, 64, true);
        out.copyFrom(source);
        source.close();
        mirrorBox(out, 0, 16, 16, 48, 4, 12, 4);
        mirrorBox(out, 40, 16, 32, 48, slim ? 3 : 4, 12, 4);
        return out;
    }

    /**
     * The mirrored copy of a box's six faces: a box of width w, height h, depth d textured at (u, v) lays out its top at
     * (u+d, v), its bottom at (u+d+w, v), then in the row below its first side at (u, v+d), its front at (u+d, v+d), its
     * second side at (u+d+w, v+d) and its back at (u+2d+w, v+d). A mirrored box shows every face flipped and its two
     * sides exchanged.
     */
    private static void mirrorBox(NativeImage image, int u, int v, int u2, int v2, int w, int h, int d) {
        copyMirrored(image, u + d, v, w, d, u2 + d, v2);
        copyMirrored(image, u + d + w, v, w, d, u2 + d + w, v2);
        copyMirrored(image, u + d + w, v + d, d, h, u2, v2 + d);
        copyMirrored(image, u + d, v + d, w, h, u2 + d, v2 + d);
        copyMirrored(image, u, v + d, d, h, u2 + d + w, v2 + d);
        copyMirrored(image, u + 2 * d + w, v + d, w, h, u2 + 2 * d + w, v2 + d);
    }

    private static void copyMirrored(NativeImage image, int sx, int sy, int w, int h, int dx, int dy) {
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                image.setPixelRGBA(dx + (w - 1 - x), dy + y, image.getPixelRGBA(sx + x, sy + y));
            }
        }
    }

    /** One converted sheet; {@code AbstractTexture.reset} re-registers it on a resource reload, which calls {@link #load} again. */
    static final class UpgradedSkinTexture extends AbstractTexture {

        private final ResourceLocation legacy;
        private final boolean slim;

        UpgradedSkinTexture(ResourceLocation legacy, boolean slim) {
            this.legacy = legacy;
            this.slim = slim;
        }

        @Override
        public void load(ResourceManager manager) throws IOException {
            final NativeImage converted;
            try (InputStream in = manager.open(this.legacy)) {
                converted = toPlayerLayout(NativeImage.read(in), this.slim);
            }
            if (RenderSystem.isOnRenderThreadOrInit()) {
                this.upload(converted);
            } else {
                RenderSystem.recordRenderCall(() -> this.upload(converted));
            }
        }

        private void upload(NativeImage image) {
            TextureUtil.prepareImage(this.getId(), 0, image.getWidth(), image.getHeight());
            image.upload(0, 0, 0, true);
        }
    }
}
