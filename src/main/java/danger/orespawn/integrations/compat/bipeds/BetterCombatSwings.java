package danger.orespawn.integrations.compat.bipeds;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.kosmx.playerAnim.api.IPlayable;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.AnimationStack;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.api.layered.modifier.MirrorModifier;
import dev.kosmx.playerAnim.api.layered.modifier.SpeedModifier;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.impl.animation.AnimationApplier;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import danger.orespawn.ModEntities;
import danger.orespawn.integrations.OreSpawnIntegrations;
import danger.orespawn.integrations.config.IntegrationsConfig;
import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.client.animation.AttackAnimationSubStack;
import net.bettercombat.client.animation.PoseSubStack;
import net.bettercombat.logic.WeaponRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Better Combat's attack animations on the Girlfriend and the Boyfriend, played the way Better Combat plays a player's
 * ({@code AbstractClientPlayerEntityMixin.playAttackAnimation}, read from its 2.4.0 bytecode): the main-hand weapon
 * looked up in Better Combat's synced weapon registry and its attacks taken in turn (back to the first after two seconds
 * without a swing); the attack's animation copied with the torso fully driven, the head's pitch left to the creature's
 * gaze and, while it walks, swims, rides or is in the air, the legs left to their stride; fitted to the weapon's attack
 * time (the animation's {@code endTick} over {@code 20 / attack speed} ticks, within the creature's melee cycle);
 * mirrored for a left-handed creature; faded in over the animation's lead-in, from the previous swing when one is still
 * playing; the whole-body motion applied about the hips as playerAnimator applies a player's
 * ({@link #bodyTransform}), and the weapon turned in the hand as playerAnimator turns a player's ({@link #itemTransform},
 * through {@code ItemInHandLayerSwingMixin}), so a two-handed weapon stays gripped the right way round. A swing begins on
 * the client when the server's animate packet arrives ({@link #onSwing}, through {@code LivingEntitySwingMixin}), one
 * attack for each swing the server sends: up to OreSpawn 2.0.0-beta.9 the pair's swing timer never advanced, so
 * {@code swinging} stayed true after their first swing and could not mark the next one (read as "a new swing" it
 * restarted the attack every other tick); the packet marks a swing on every version. A weapon with an idle pose in its attributes is held
 * in it between swings ({@link #updateHold}), both hands on a claymore's hilt as a player holds it. A held item Better
 * Combat does not know swings the vanilla way.
 *
 * <p>A player animation pack drawn by Entity Model Features animates the model after {@code setupAnim}, so on its own it
 * would draw over the attack. With EMF the pose is laid again right after EMF's animation ({@link EmfReapply}), over the
 * pack's pose; the same is done for players, whose Better Combat attacks and weapon poses the pack would otherwise
 * cover. Referenced only when Better Combat and playerAnimator are loaded.</p>
 */
final class BetterCombatSwings {

    private static final int COMBO_RESET_TICKS = 40;
    /** The height above the feet, in blocks, about which playerAnimator turns a player's whole body. */
    private static final float BODY_PIVOT = 0.7F;

    /**
     * One creature's Better Combat layers, stacked as Better Combat stacks a player's: the weapon's idle pose underneath
     * (a two-handed weapon gripped with both hands) and the attack on top, kept across swings so that a combo swing fades
     * from the one before it and an attack eases back into the grip.
     */
    private static final class Swing {
        private final SpeedModifier speed = new SpeedModifier(1.0F);
        private final MirrorModifier mirror = new MirrorModifier(false);
        private final ModifierLayer<IAnimation> layer = new ModifierLayer<>(null, this.speed, this.mirror);
        private final MirrorModifier holdMirror = new MirrorModifier(false);
        private final ModifierLayer<IAnimation> hold = new ModifierLayer<>(null, this.holdMirror);
        private final AnimationStack stack = new AnimationStack();
        private final AnimationApplier applier = new AnimationApplier(this.stack);
        /** The idle pose playing (its id and hand), or null. */
        private String holdKey;
        private int lastStart = Integer.MIN_VALUE / 2;
        private int combo = -1;

        private Swing() {
            this.stack.addAnimLayer(1, this.hold);
            this.stack.addAnimLayer(2000, this.layer);
        }

        private boolean isActive() {
            return this.layer.isActive() || this.hold.isActive();
        }
    }

    private static final int HOLD_FADE_TICKS = 5;

    /** A player's own Better Combat layers (its four weapon poses and its attack), in Better Combat's order. */
    private static final class PlayerLayers {
        private final AnimationStack stack = new AnimationStack();
        private final AnimationApplier applier = new AnimationApplier(this.stack);
    }

    private static boolean emf;
    private static final Map<LivingEntity, Swing> SWINGS = new WeakHashMap<>();
    /** Per creature: the frame in which a swing was announced and not yet played. */
    private static final Map<LivingEntity, Integer> SWUNG = new WeakHashMap<>();
    /**
     * A swing is played if the creature is drawn within this many frames of the announcing packet. Counted in frames,
     * not ticks: a client that falls behind handles that frame's packets first and then runs several catch-up ticks
     * before it draws, so a tick count dropped swings from a creature that was on screen the whole time.
     */
    private static final int STALE_SWING_FRAMES = 2;
    /** Frames begun since the game started ({@link #onRenderFramePre}). */
    private static int frame;
    private static final Map<AbstractClientPlayer, PlayerLayers> PLAYER_LAYERS = new WeakHashMap<>();
    /** Better Combat's layer fields on {@code AbstractClientPlayer} (added by its mixin) and their priorities; null until sought. */
    private static Field[] layerFields;
    private static int[] layerPriorities;
    private static boolean playersUnavailable;
    private static boolean failed;

    private BetterCombatSwings() {
    }

    static void register(IEventBus modBus, boolean emfLoaded) {
        emf = emfLoaded;
        BipedsClient.swingHook = new BipedsClient.SwingHook() {
            @Override
            public void apply(LivingEntity entity, PlayerModel<?> model, float ageInTicks) {
                BetterCombatSwings.apply(entity, model, ageInTicks);
            }

            @Override
            public void bodyTransform(LivingEntity entity, PoseStack poseStack, float partialTick, float scale) {
                BetterCombatSwings.bodyTransform(entity, poseStack, partialTick, scale);
            }
        };
        BipedItemHook.transform = BetterCombatSwings::itemTransform;
        BipedSwingStartHook.listener = BetterCombatSwings::onSwing;
        NeoForge.EVENT_BUS.addListener(BetterCombatSwings::onClientTick);
        NeoForge.EVENT_BUS.addListener(BetterCombatSwings::onRenderFramePre);
        if (emfLoaded) {
            modBus.addListener(FMLClientSetupEvent.class, event -> EmfReapply.install());
            NeoForge.EVENT_BUS.addListener(BetterCombatSwings::onRenderLivingPre);
            NeoForge.EVENT_BUS.addListener(BetterCombatSwings::onRenderLivingPost);
        }
    }

    static boolean enabled() {
        return !IntegrationsConfig.SPEC.isLoaded() || IntegrationsConfig.betterCombatBipeds.getAsBoolean();
    }

    static boolean playersEnabled() {
        return !IntegrationsConfig.SPEC.isLoaded() || IntegrationsConfig.betterCombatOnPlayers.getAsBoolean();
    }

    /**
     * Lays the pose {@code applier} holds over an animation pack's pose (called right after Entity Model Features has
     * animated the model). The pack moves the torso, head and shoulders as it walks, bobs and leans, so Better Combat's
     * positions go on as offsets from each part's rest position (playerAnimator's rests: arms at x -5 / 5 and y 2, legs at
     * x -1.9 / 1.9, y 12, z 0.1, the rest at 0) and the part moves with the pack's body instead of being pinned where a
     * still vanilla body would be; rotations are Better Combat's, as for a player.
     */
    static void poseOverPack(AnimationApplier applier, PlayerModel<?> model) {
        overPart(applier, "head", model.head, 0.0F, 0.0F, 0.0F);
        overPart(applier, "torso", model.body, 0.0F, 0.0F, 0.0F);
        overPart(applier, "rightArm", model.rightArm, -5.0F, 2.0F, 0.0F);
        overPart(applier, "leftArm", model.leftArm, 5.0F, 2.0F, 0.0F);
        overPart(applier, "rightLeg", model.rightLeg, -1.9F, 12.0F, 0.1F);
        overPart(applier, "leftLeg", model.leftLeg, 1.9F, 12.0F, 0.1F);
        copyOverlays(model);
    }

    private static void overPart(AnimationApplier applier, String name, ModelPart part, float restX, float restY, float restZ) {
        final Vec3f position = applier.get3DTransform(name, TransformType.POSITION, new Vec3f(restX, restY, restZ));
        part.x += position.getX() - restX;
        part.y += position.getY() - restY;
        part.z += position.getZ() - restZ;
        final Vec3f rotation = applier.get3DTransform(name, TransformType.ROTATION, new Vec3f(part.xRot, part.yRot, part.zRot));
        part.setRotation(rotation.getX(), rotation.getY(), rotation.getZ());
        final Vec3f size = applier.get3DTransform(name, TransformType.SCALE, new Vec3f(part.xScale, part.yScale, part.zScale));
        part.xScale = size.getX();
        part.yScale = size.getY();
        part.zScale = size.getZ();
    }

    private static void copyOverlays(PlayerModel<?> model) {
        model.hat.copyFrom(model.head);
        model.jacket.copyFrom(model.body);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightPants.copyFrom(model.rightLeg);
        model.leftPants.copyFrom(model.leftLeg);
    }

    /** Lays the pose {@code applier} holds over vanilla's pose (no animation pack), the overlays following their parts. */
    static void pose(AnimationApplier applier, PlayerModel<?> model) {
        applier.updatePart("head", model.head);
        applier.updatePart("torso", model.body);
        applier.updatePart("rightArm", model.rightArm);
        applier.updatePart("leftArm", model.leftArm);
        applier.updatePart("rightLeg", model.rightLeg);
        applier.updatePart("leftLeg", model.leftLeg);
        copyOverlays(model);
    }

    private static void onRenderFramePre(RenderFrameEvent.Pre event) {
        frame++;
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().isPaused()) {
            return;
        }
        final Iterator<Map.Entry<LivingEntity, Swing>> it = SWINGS.entrySet().iterator();
        while (it.hasNext()) {
            final Map.Entry<LivingEntity, Swing> entry = it.next();
            if (entry.getKey().isRemoved()) {
                it.remove();
            } else {
                entry.getValue().stack.tick();
            }
        }
    }

    private static void apply(LivingEntity entity, PlayerModel<?> model, float ageInTicks) {
        if (failed || !enabled()) {
            return;
        }
        try {
            updateHold(entity);
            startIfSwingBegan(entity);
            final Swing swing = SWINGS.get(entity);
            if (swing == null || !swing.isActive()) {
                return;
            }
            swing.applier.setTickDelta(Mth.clamp(ageInTicks - entity.tickCount, 0.0F, 1.0F));
            if (emf && EmfReapply.defer(entity, model, swing.applier)) {
                return;
            }
            pose(swing.applier, model);
        } catch (RuntimeException e) {
            fail("the pair's swings", e);
        }
    }

    /** The attack's whole-body motion, as playerAnimator applies a player's at the end of {@code setupRotations}. */
    private static void bodyTransform(LivingEntity entity, PoseStack poseStack, float partialTick, float scale) {
        if (failed || !enabled()) {
            return;
        }
        final Swing swing = SWINGS.get(entity);
        if (swing == null || !swing.isActive()) {
            return;
        }
        try {
            bodyTransform(swing.applier, poseStack, partialTick, scale);
        } catch (RuntimeException e) {
            fail("the pair's swings", e);
        }
    }

    /**
     * The attack's turn of the held item ({@code rightItem} / {@code leftItem}, swapped by the mirror for a left-handed
     * creature), applied as playerAnimator applies a player's: scale, move (in pixels), then roll, yaw and pitch.
     */
    private static void itemTransform(LivingEntity entity, HumanoidArm arm, PoseStack poseStack) {
        if (failed || !enabled()) {
            return;
        }
        final Swing swing = SWINGS.get(entity);
        if (swing == null || !swing.isActive()) {
            return;
        }
        try {
            final String part = arm == HumanoidArm.LEFT ? "leftItem" : "rightItem";
            final AnimationApplier applier = swing.applier;
            final Vec3f size = applier.get3DTransform(part, TransformType.SCALE, new Vec3f(1.0F, 1.0F, 1.0F));
            final Vec3f rotation = applier.get3DTransform(part, TransformType.ROTATION, Vec3f.ZERO);
            final Vec3f position = applier.get3DTransform(part, TransformType.POSITION, Vec3f.ZERO).scale(1.0F / 16.0F);
            poseStack.scale(size.getX(), size.getY(), size.getZ());
            poseStack.translate(position.getX(), position.getY(), position.getZ());
            poseStack.mulPose(Axis.ZP.rotation(rotation.getZ()));
            poseStack.mulPose(Axis.YP.rotation(rotation.getY()));
            poseStack.mulPose(Axis.XP.rotation(rotation.getX()));
        } catch (RuntimeException e) {
            fail("the pair's swings", e);
        }
    }

    private static void bodyTransform(AnimationApplier applier, PoseStack poseStack, float partialTick, float scale) {
        applier.setTickDelta(partialTick);
        final Vec3f size = applier.get3DTransform("body", TransformType.SCALE, new Vec3f(1.0F, 1.0F, 1.0F));
        poseStack.scale(size.getX(), size.getY(), size.getZ());
        final Vec3f position = applier.get3DTransform("body", TransformType.POSITION, Vec3f.ZERO);
        poseStack.translate(position.getX() * scale, (position.getY() + BODY_PIVOT) * scale, position.getZ() * scale);
        final Vec3f rotation = applier.get3DTransform("body", TransformType.ROTATION, Vec3f.ZERO);
        poseStack.mulPose(Axis.ZP.rotation(rotation.getZ()));
        poseStack.mulPose(Axis.YP.rotation(rotation.getY()));
        poseStack.mulPose(Axis.XP.rotation(rotation.getX()));
        poseStack.translate(0.0F, -BODY_PIVOT * scale, 0.0F);
    }

    /**
     * The held weapon's idle pose ({@code pose} in its Better Combat attributes, e.g. both hands on a claymore's hilt):
     * started, switched or faded out as the main-hand item changes. Only the arms and the item are posed, as Better
     * Combat's item channel does for a player; the animation pack keeps the head, torso and legs.
     */
    private static void updateHold(LivingEntity entity) {
        final ItemStack held = entity.getMainHandItem();
        String poseId = null;
        // as Better Combat drops a player's weapon pose: not while swimming, climbing, gliding, sleeping or using an item
        final boolean busy = entity.isSwimming() || entity.onClimbable() || entity.isFallFlying() || entity.isSleeping()
                || entity.isUsingItem();
        if (!held.isEmpty() && !busy) {
            final WeaponAttributes attributes = WeaponRegistry.getAttributes(held);
            if (attributes != null && attributes.pose() != null && !attributes.pose().isEmpty()) {
                poseId = attributes.pose();
            }
        }
        Swing swing = SWINGS.get(entity);
        if (poseId == null) {
            if (swing != null && swing.holdKey != null) {
                swing.holdKey = null;
                swing.hold.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(HOLD_FADE_TICKS, Ease.INOUTSINE), null);
            }
            return;
        }
        final boolean left = entity.getMainArm() == HumanoidArm.LEFT;
        final String key = poseId + (left ? "@left" : "@right");
        if (swing != null && key.equals(swing.holdKey)) {
            return;
        }
        final ResourceLocation id = ResourceLocation.tryParse(poseId);
        final IPlayable playable = id == null ? null : PlayerAnimationRegistry.getAnimation(id);
        if (!(playable instanceof KeyframeAnimation animation)) {
            return;
        }
        if (swing == null) {
            swing = new Swing();
            SWINGS.put(entity, swing);
        }
        swing.holdKey = key;
        final KeyframeAnimation.AnimationBuilder builder = animation.mutableCopy();
        builder.head.setEnabled(false);
        builder.torso.setEnabled(false);
        builder.body.setEnabled(false);
        builder.rightLeg.setEnabled(false);
        builder.leftLeg.setEnabled(false);
        swing.holdMirror.setEnabled(left);
        swing.hold.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(HOLD_FADE_TICKS, Ease.INOUTSINE),
                new KeyframeAnimationPlayer(builder.build()), true);
    }

    /**
     * A main-hand swing announced for the Girlfriend or the Boyfriend on the client ({@code LivingEntitySwingMixin}): one
     * Better Combat attack per swing the server sends. Up to OreSpawn 2.0.0-beta.9 their swing timer never advanced
     * (vanilla ticks it only for monsters and players), so {@code swinging} stayed true after their first swing and could
     * not mark the next one; the call marks it on every version.
     */
    private static void onSwing(LivingEntity entity, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !entity.level().isClientSide) {
            return;
        }
        final EntityType<?> type = entity.getType();
        if (type == ModEntities.GIRLFRIEND.get() || type == ModEntities.BOYFRIEND.get()) {
            SWUNG.put(entity, frame);
        }
    }

    private static void startIfSwingBegan(LivingEntity entity) {
        final Integer swungAt = SWUNG.remove(entity);
        // a swing announced while the creature was not being drawn is not played late
        if (swungAt == null || frame - swungAt > STALE_SWING_FRAMES) {
            return;
        }
        Swing swing = SWINGS.get(entity);
        final ItemStack held = entity.getMainHandItem();
        if (held.isEmpty()) {
            return;
        }
        final WeaponAttributes attributes = WeaponRegistry.getAttributes(held);
        if (attributes == null || attributes.attacks() == null || attributes.attacks().length == 0) {
            return;
        }
        if (swing == null) {
            swing = new Swing();
            SWINGS.put(entity, swing);
        }
        final int combo = entity.tickCount - swing.lastStart <= COMBO_RESET_TICKS ? swing.combo + 1 : 0;
        final WeaponAttributes.Attack attack = attributes.attacks()[Math.floorMod(combo, attributes.attacks().length)];
        final String name = attack == null ? null : attack.animation();
        final ResourceLocation id = name == null || name.isEmpty() ? null : ResourceLocation.tryParse(name);
        final IPlayable playable = id == null ? null : PlayerAnimationRegistry.getAnimation(id);
        if (!(playable instanceof KeyframeAnimation animation)) {
            return;
        }
        swing.lastStart = entity.tickCount;
        swing.combo = combo;

        final KeyframeAnimation.AnimationBuilder builder = animation.mutableCopy();
        if (entity.isPassenger() || entity.isSwimming() || !entity.onGround() || entity.walkAnimation.speed() > 0.1F) {
            builder.rightLeg.setEnabled(false);
            builder.leftLeg.setEnabled(false);
        }
        builder.torso.fullyEnablePart(true);
        builder.head.pitch.setEnabled(false);
        // Better Combat fades over the lead-in; at least one tick, as a zero-length fade divides 0 by 0 on a tick boundary
        final int fadeIn = Math.max(1, builder.beginTick);
        final KeyframeAnimation fitted = builder.build();
        swing.speed.speed = animation.endTick > 0 ? Math.max(0.25F, animation.endTick / attackTicks(held)) : 1.0F;
        swing.mirror.setEnabled(entity.getMainArm() == HumanoidArm.LEFT);
        swing.layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(fadeIn, Ease.INOUTSINE), new KeyframeAnimationPlayer(fitted));
    }

    /** The weapon's attack time in ticks, as Better Combat fits a player's attack: 20 / (4 + the weapon's attack speed). */
    private static float attackTicks(ItemStack stack) {
        double attackSpeed = 4.0D;
        for (ItemAttributeModifiers.Entry entry : stack.getAttributeModifiers().modifiers()) {
            if (entry.attribute().value() == Attributes.ATTACK_SPEED.value() && entry.slot().test(EquipmentSlot.MAINHAND)
                    && entry.modifier().operation() == AttributeModifier.Operation.ADD_VALUE) {
                attackSpeed += entry.modifier().amount();
            }
        }
        return Mth.clamp((float) (20.0D / Math.max(0.5D, attackSpeed)), 6.0F, 25.0F);
    }

    private static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        EmfReapply.clear();
        if (playersUnavailable || !(event.getEntity() instanceof AbstractClientPlayer player) || !playersEnabled()) {
            return;
        }
        if (!(event.getRenderer().getModel() instanceof PlayerModel<?> model)) {
            return;
        }
        try {
            final PlayerLayers layers = playerLayers(player);
            if (layers == null || !layers.stack.isActive()) {
                return;
            }
            layers.applier.setTickDelta(event.getPartialTick());
            EmfReapply.defer(player, model, layers.applier);
        } catch (RuntimeException e) {
            unavailable(e);
        }
    }

    private static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
        EmfReapply.endOfRender();
    }

    private static PlayerLayers playerLayers(AbstractClientPlayer player) {
        if (playersUnavailable) {
            return null;
        }
        final PlayerLayers known = PLAYER_LAYERS.get(player);
        if (known != null) {
            return known;
        }
        if (layerFields == null && !findLayerFields()) {
            return null;
        }
        final PlayerLayers layers = new PlayerLayers();
        try {
            for (int i = 0; i < layerFields.length; i++) {
                final Object value = layerFields[i].get(player);
                final ModifierLayer<?> base = value instanceof AttackAnimationSubStack attack ? attack.base
                        : value instanceof PoseSubStack pose ? pose.base : null;
                if (base != null) {
                    layers.stack.addAnimLayer(layerPriorities[i], base);
                }
            }
        } catch (IllegalAccessException e) {
            unavailable(e);
            return null;
        }
        PLAYER_LAYERS.put(player, layers);
        return layers;
    }

    /**
     * Better Combat's client mixin adds its layers to every client player as fields and registers them in the player's
     * animation stack at these priorities: off-hand item pose 1, off-hand body pose 2, main-hand item pose 3, main-hand
     * body pose 4, attack 2000.
     */
    private static boolean findLayerFields() {
        final String[] names = {"offHandItemPose", "offHandBodyPose", "mainHandItemPose", "mainHandBodyPose", "attackAnimation"};
        final int[] priorities = {1, 2, 3, 4, 2000};
        final Field[] fields = new Field[names.length];
        int found = 0;
        try {
            for (Field field : AbstractClientPlayer.class.getDeclaredFields()) {
                for (int i = 0; i < names.length; i++) {
                    if (field.getName().equals(names[i])
                            && (field.getType() == AttackAnimationSubStack.class || field.getType() == PoseSubStack.class)) {
                        field.setAccessible(true);
                        fields[i] = field;
                        found++;
                    }
                }
            }
        } catch (RuntimeException e) {
            unavailable(e);
            return false;
        }
        if (fields[names.length - 1] == null) {
            unavailable(null);
            return false;
        }
        final Field[] present = new Field[found];
        final int[] presentPriorities = new int[found];
        for (int i = 0, j = 0; i < names.length; i++) {
            if (fields[i] != null) {
                present[j] = fields[i];
                presentPriorities[j++] = priorities[i];
            }
        }
        layerFields = present;
        layerPriorities = presentPriorities;
        return true;
    }

    private static void unavailable(Exception e) {
        EmfReapply.clear();
        if (!playersUnavailable) {
            playersUnavailable = true;
            OreSpawnIntegrations.LOGGER.warn("Bipeds compat: Better Combat's player animation layers are not usable; "
                    + "a player animation pack keeps drawing over players' attacks", e);
        }
    }

    /** Turns the pair's swings off for the session after an unexpected error, rather than failing every frame. */
    static void fail(String what, Throwable e) {
        EmfReapply.clear();
        if (!failed) {
            failed = true;
            OreSpawnIntegrations.LOGGER.error("Bipeds compat: {} failed and are off until restart", what, e);
        }
    }
}
