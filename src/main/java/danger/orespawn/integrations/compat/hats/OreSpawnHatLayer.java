package danger.orespawn.integrations.compat.hats;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import danger.orespawn.integrations.OreSpawnIntegrations;
import danger.orespawn.integrations.config.IntegrationsConfig;
import me.guivnf.mods.hats.HatsMod;
import me.guivnf.mods.hats.client.cache.ClientHatCache;
import me.guivnf.mods.hats.client.render.HatRenderer;
import me.guivnf.mods.hats.client.render.LayerHat;
import me.guivnf.mods.hats.common.hat.HatPart;
import me.guivnf.mods.hats.common.hat.placement.HatPlacementInfo;
import me.guivnf.mods.hats.common.hat.placement.HatPlacementRegistry;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.state.BoneSnapshot;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * The hat on the head bone. GeckoLib calls {@link #renderForBone} for every bone with the pose stack already carrying
 * the bone's transform (its parents', its pivot, its current rotation), so the layer only has to step from there to
 * the top-centre of the head and hand the hat to Hats Renewed's renderer.
 *
 * <p>Geometry, measured once per head from the baked rig (so it is exact for the rig that ships, and GeckoLib's
 * bake-time conventions - X mirrored, Y up, X and Y rotations negated - never have to be restated here):
 * <ol>
 *   <li>The cube vertices of the anchor bone and its {@code also} bones, each in its own frame, are rotated by that
 *       bone's REST rotation about its pivot and then by each ancestor's rest rotation about theirs: the head as it
 *       stands when the creature is at rest.</li>
 *   <li>The top face's centre of that rest-pose head is the anchor; the head's width is its X extent (side to side;
 *       the Z extent stands in for a plate with no width).</li>
 *   <li>The anchor is mapped back into the anchor bone's own frame, so translating to it under the LIVE transform
 *       lands on the rest anchor at rest and follows the bone through any animation.</li>
 *   <li>The composed rest rotation is undone at the anchor, so the hat stands upright on a head that is tilted at
 *       rest (the Basilisk's, the Leon's, the Kyuubi's back-to-front one) and tilts only with the animation.</li>
 * </ol>
 * Then the anchor's pixel offset is applied, the frame is flipped ({@code scale(-1, -1, 1)}), which is what Hats
 * Renewed's own GeckoLib branch does before drawing, the placement file's offsets, rotation and scale are applied by
 * the renderer as everywhere else, and the hat is scaled to the head: width / 8 px (a player head) clamped by the
 * anchor's bounds, times the placement's own scale. {@code LayerHat.HAT_RENDERED_THIS_ENTITY} is raised so the mod's
 * dispatcher fallback (which resets it at the head of the entity render and reads it at the tail) does not draw a
 * second hat; for a {@code none} anchor it is raised without drawing, which is how a coin stays bare.
 */
final class OreSpawnHatLayer<T extends GeoAnimatable> extends GeoRenderLayer<T> {

    /** Vertices within this of the top are "the top face" (half a pixel, in blocks). */
    private static final float TOP_EPSILON = 0.5f / 16f;
    private static final float ONE_PX = 1f / 16f;
    private static final float PLAYER_HEAD_PX = 8f;

    /** Measured heads, by anchor bone (identity: GeckoLib bakes one bone object per rig) then by anchor spec. */
    private final Map<GeoBone, Map<HatAnchors.Anchor, Frame>> frames = new IdentityHashMap<>();
    private final Set<String> warned = new HashSet<>();
    @Nullable
    private BakedGeoModel currentModel;

    OreSpawnHatLayer(GeoRenderer<T> renderer) {
        super(renderer);
    }

    @Override
    public void preRender(PoseStack poseStack, T animatable, BakedGeoModel bakedModel, RenderType renderType,
                          MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight,
                          int packedOverlay) {
        this.currentModel = bakedModel;
    }

    @Override
    public void render(PoseStack poseStack, T animatable, BakedGeoModel bakedModel, RenderType renderType,
                       MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight,
                       int packedOverlay) {
        if (!enabled()) {
            return;
        }
        LivingEntity living = living(animatable);
        if (living == null) {
            return;
        }
        HatAnchors.Anchor anchor = HatAnchors.get(living.getType());
        if (anchor != null && anchor.none()) {
            // No hat for this species: silence the mod's fallback as well.
            LayerHat.HAT_RENDERED_THIS_ENTITY.set(Boolean.TRUE);
        }
    }

    @Override
    public void renderForBone(PoseStack poseStack, T animatable, GeoBone bone, RenderType renderType,
                              MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight,
                              int packedOverlay) {
        if (!enabled()) {
            return;
        }
        LivingEntity living = living(animatable);
        if (living == null) {
            return;
        }
        HatAnchors.Anchor anchor = HatAnchors.get(living.getType());
        if (anchor == null || anchor.none() || !anchor.bone().equals(bone.getName())) {
            return;
        }
        HatPart hat = ClientHatCache.getEntityHat(living.getUUID());
        if (hat == null || !hat.isShowing) {
            return;
        }
        if (living.isInvisible() && !HatsMod.getConfig().renderOnInvisible) {
            return;
        }
        Frame frame = frames.computeIfAbsent(bone, b -> new HashMap<>()).computeIfAbsent(anchor, a -> measure(bone, a));
        if (frame == null) {
            return;
        }
        // Ours now; the dispatcher fallback stays quiet for this entity.
        LayerHat.HAT_RENDERED_THIS_ENTITY.set(Boolean.TRUE);

        HatPlacementInfo placement = HatPlacementRegistry.get(living);
        float scale = placement.scale * Mth.clamp(frame.headWidthPx / PLAYER_HEAD_PX, anchor.minScale(), anchor.maxScale());

        poseStack.pushPose();
        poseStack.translate(frame.local.x(), frame.local.y(), frame.local.z());
        poseStack.mulPose(frame.uprightFix);
        if (anchor.offX() != 0f || anchor.offY() != 0f || anchor.offZ() != 0f) {
            poseStack.translate(anchor.offX() / 16f, anchor.offY() / 16f, anchor.offZ() / 16f);
        }
        poseStack.scale(-1f, -1f, 1f);
        HatRenderer.render(poseStack, bufferSource, packedLight, OverlayTexture.pack(0, 10), hat,
                placement.offsetX, placement.offsetY, placement.offsetZ,
                placement.rotX, placement.rotY, placement.rotZ, scale);
        poseStack.popPose();
    }

    private static boolean enabled() {
        return !IntegrationsConfig.SPEC.isLoaded() || IntegrationsConfig.hatsOnRigs.getAsBoolean();
    }

    @Nullable
    private LivingEntity living(T animatable) {
        if (animatable instanceof LivingEntity le) {
            return le;
        }
        if (getRenderer() instanceof GeoReplacedEntityRenderer<?, ?> replaced
                && replaced.getCurrentEntity() instanceof LivingEntity le) {
            return le;
        }
        return null;
    }

    // ------------------------------------------------------------------------------------------------------ geometry

    /** The anchor in the bone's own frame, the rotation that stands it upright at rest, and the head's width. */
    private record Frame(Vector3f local, Quaternionf uprightFix, float headWidthPx) {
    }

    /** A cube face at rest: its centre, how flat it lies (|normal.y|, 1 = horizontal) and its area. */
    private record Face(Vector3f centre, float flatness, float area) {
    }

    /** cos 45 degrees: a face at least this flat counts as the top of a head. */
    private static final float FLAT_FACE = 0.7f;

    @Nullable
    private Frame measure(GeoBone bone, HatAnchors.Anchor anchor) {
        List<GeoBone> parts = new ArrayList<>();
        parts.add(bone);
        for (String name : anchor.also()) {
            GeoBone extra = currentModel == null ? null : currentModel.getBone(name).orElse(null);
            if (extra == null) {
                if (warned.add(anchor.geo() + "/" + name)) {
                    OreSpawnIntegrations.LOGGER.warn("Hats compat: rig {} has no bone '{}' named in the anchor's 'also' list; ignored",
                            anchor.geo(), name);
                }
                continue;
            }
            parts.add(extra);
        }

        // 1. the head at rest: every vertex of every part through its own rest chain, and every face's centre
        List<Vector3f> rest = new ArrayList<>();
        List<Face> faces = new ArrayList<>();
        for (GeoBone part : parts) {
            List<GeoBone> chain = chain(part);
            for (GeoCube cube : part.getCubes()) {
                for (GeoQuad quad : cube.quads()) {
                    GeoVertex[] vertices = quad.vertices();
                    Vector3f[] w = new Vector3f[vertices.length];
                    for (int i = 0; i < vertices.length; i++) {
                        w[i] = new Vector3f(vertices[i].position());
                        for (GeoBone b : chain) {
                            rotateAboutPivot(w[i], b, false);
                        }
                        rest.add(w[i]);
                    }
                    if (w.length >= 3) {
                        Vector3f normal = new Vector3f(w[1]).sub(w[0]).cross(new Vector3f(w[2]).sub(w[0]));
                        float area = normal.length();
                        if (area > 1e-7f) {
                            Vector3f centre = new Vector3f();
                            for (Vector3f v : w) {
                                centre.add(v);
                            }
                            centre.div(w.length);
                            faces.add(new Face(centre, Math.abs(normal.y / area), area));
                        }
                    }
                }
            }
        }
        if (rest.isEmpty()) {
            if (warned.add(anchor.geo() + "/" + bone.getName() + "/empty")) {
                OreSpawnIntegrations.LOGGER.warn("Hats compat: rig {} bone '{}' has no cubes to anchor a hat on", anchor.geo(), bone.getName());
            }
            return null;
        }
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (Vector3f w : rest) {
            minX = Math.min(minX, w.x); maxX = Math.max(maxX, w.x);
            minZ = Math.min(minZ, w.z); maxZ = Math.max(maxZ, w.z);
            maxY = Math.max(maxY, w.y);
        }
        // 2. the crown: the centre of the highest face that lies flat (within ~45 degrees), so a head tilted at rest
        //    still carries its hat mid-skull rather than on the rim that happens to be highest. Faces of equal height
        //    (a split head, a flat pair of plates) are averaged by area. A rig with no flat face (a fin, a plate seen
        //    edge-on) falls back to the highest vertices.
        Vector3f top = null;
        float topY = -Float.MAX_VALUE;
        for (Face f : faces) {
            if (f.flatness >= FLAT_FACE && f.centre.y > topY) {
                topY = f.centre.y;
            }
        }
        if (topY > -Float.MAX_VALUE) {
            Vector3f sum = new Vector3f();
            float weight = 0f;
            for (Face f : faces) {
                if (f.flatness >= FLAT_FACE && f.centre.y >= topY - TOP_EPSILON) {
                    sum.add(new Vector3f(f.centre).mul(f.area));
                    weight += f.area;
                }
            }
            top = sum.div(weight);
        }
        if (top == null) {
            float sx = 0f, sz = 0f;
            int n = 0;
            for (Vector3f w : rest) {
                if (w.y >= maxY - TOP_EPSILON) {
                    sx += w.x; sz += w.z; n++;
                }
            }
            top = new Vector3f(sx / n, maxY, sz / n);
        }

        // 3. back into the anchor bone's frame: undo the ancestors from the root inward, then the bone itself
        List<GeoBone> chain = chain(bone);
        Vector3f local = new Vector3f(top);
        for (int i = chain.size() - 1; i >= 0; i--) {
            rotateAboutPivot(local, chain.get(i), true);
        }

        // 4. the composed rest rotation, root first, and its inverse
        Quaternionf total = new Quaternionf();
        for (int i = chain.size() - 1; i >= 0; i--) {
            total.mul(restRotation(chain.get(i)));
        }
        Quaternionf uprightFix = total.conjugate(new Quaternionf());

        float width = maxX - minX;
        if (width < ONE_PX) {
            width = Math.max(maxZ - minZ, ONE_PX);
        }
        return new Frame(local, uprightFix, 16f * width);
    }

    /** The bone first, then its parents outward to the root. */
    private static List<GeoBone> chain(GeoBone bone) {
        List<GeoBone> chain = new ArrayList<>();
        for (GeoBone b = bone; b != null; b = b.getParent()) {
            chain.add(b);
        }
        return chain;
    }

    /** GeckoLib applies a bone's rotation as Z, then Y, then X ({@code RenderUtil.rotateMatrixAroundBone}). */
    private static Quaternionf restRotation(GeoBone bone) {
        BoneSnapshot rest = bone.getInitialSnapshot();
        if (rest == null) {
            return new Quaternionf().rotationZYX(bone.getRotZ(), bone.getRotY(), bone.getRotX());
        }
        return new Quaternionf().rotationZYX(rest.getRotZ(), rest.getRotY(), rest.getRotX());
    }

    private static void rotateAboutPivot(Vector3f point, GeoBone bone, boolean inverse) {
        Quaternionf q = restRotation(bone);
        if (inverse) {
            q.conjugate();
        }
        float px = bone.getPivotX() / 16f, py = bone.getPivotY() / 16f, pz = bone.getPivotZ() / 16f;
        point.sub(px, py, pz);
        q.transform(point);
        point.add(px, py, pz);
    }
}
