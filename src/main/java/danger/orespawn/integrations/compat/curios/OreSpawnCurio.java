package danger.orespawn.integrations.compat.curios;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared curio behavior for OreSpawn trinkets registered by {@link CuriosCompat}.
 *
 * <p>Every trinket built from this class keeps three traits:
 * <ul>
 *   <li>flat attribute bonuses while equipped (empty list = none),</li>
 *   <li>an optional water-breathing aura ticked server-side,</li>
 *   <li>{@code ALWAYS_KEEP} on death while the wearer is inside any OreSpawn
 *       dimension (namespace check on the level's dimension id), so dying to the
 *       boss that dropped the trinket never eats the trinket.</li>
 * </ul>
 */
final class OreSpawnCurio implements ICurioItem {

    /**
     * One flat attribute bonus. {@code idSuffix} is appended to the slot-stable
     * ResourceLocation Curios hands us, keeping every modifier id unique per
     * attribute while staying identical across re-equips of the same slot.
     */
    record Bonus(Holder<Attribute> attribute, String idSuffix, double amount) {}

    /**
     * Water-breathing refresh threshold/duration in ticks. Re-applying only when
     * the remaining duration dips below the threshold avoids re-sync spam while
     * never letting the effect visibly run out.
     */
    private static final int EFFECT_REFRESH_BELOW = 210;
    private static final int EFFECT_DURATION = 230;

    private final List<Bonus> bonuses;
    private final boolean waterBreathing;
    private final String tooltipKey; // nullable: extra tooltip line under the Curios slot lines

    OreSpawnCurio(List<Bonus> bonuses, boolean waterBreathing, String tooltipKey) {
        this.bonuses = List.copyOf(bonuses);
        this.waterBreathing = waterBreathing;
        this.tooltipKey = tooltipKey;
    }

    @Override
    public Multimap<Holder<Attribute>, AttributeModifier> getAttributeModifiers(
            SlotContext slotContext, ResourceLocation id, ItemStack stack) {
        Multimap<Holder<Attribute>, AttributeModifier> modifiers = HashMultimap.create();
        for (Bonus bonus : bonuses) {
            modifiers.put(bonus.attribute(), new AttributeModifier(
                    id.withSuffix("_" + bonus.idSuffix()),
                    bonus.amount(),
                    AttributeModifier.Operation.ADD_VALUE));
        }
        return modifiers;
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        if (!waterBreathing) {
            return;
        }
        LivingEntity entity = slotContext.entity();
        if (entity == null || entity.level().isClientSide()) {
            return;
        }
        MobEffectInstance current = entity.getEffect(MobEffects.WATER_BREATHING);
        if (current == null || current.getDuration() < EFFECT_REFRESH_BELOW) {
            // ambient=true, visible=false (no particle spam), showIcon=true
            entity.addEffect(new MobEffectInstance(
                    MobEffects.WATER_BREATHING, EFFECT_DURATION, 0, true, false, true));
        }
    }

    @Override
    public ICurio.DropRule getDropRule(SlotContext slotContext, DamageSource source,
                                       boolean recentlyHit, ItemStack stack) {
        return dropRule(slotContext);
    }

    // Deprecated-for-removal upstream, but in Curios 9.5.1 this int-arity overload
    // is the one ItemizedCurioCapability forwards to on death (verified in the jar's
    // bytecode), and its default skips straight to defaultInstance — so it must be
    // overridden too or the drop rule never fires.
    @SuppressWarnings("removal")
    @Override
    public ICurio.DropRule getDropRule(SlotContext slotContext, DamageSource source,
                                       int lootingLevel, boolean recentlyHit, ItemStack stack) {
        return dropRule(slotContext);
    }

    /** ALWAYS_KEEP while the wearer is in any OreSpawn dimension. */
    private static ICurio.DropRule dropRule(SlotContext slotContext) {
        LivingEntity entity = slotContext.entity();
        if (entity != null && CuriosCompat.ORESPAWN_NAMESPACE
                .equals(entity.level().dimension().location().getNamespace())) {
            return ICurio.DropRule.ALWAYS_KEEP;
        }
        return ICurio.DropRule.DEFAULT;
    }

    @Override
    public List<Component> getSlotsTooltip(List<Component> tooltips, Item.TooltipContext context,
                                           ItemStack stack) {
        List<Component> result = ICurioItem.super.getSlotsTooltip(tooltips, context, stack);
        if (tooltipKey != null) {
            result = new ArrayList<>(result);
            result.add(Component.translatable(tooltipKey).withStyle(ChatFormatting.DARK_AQUA));
        }
        return result;
    }
}
