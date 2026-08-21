package danger.orespawn.integrations.compat.curios;

import danger.orespawn.entity.Boyfriend;
import danger.orespawn.entity.Girlfriend;
import danger.orespawn.integrations.OreSpawnIntegrations;
import danger.orespawn.integrations.items.ItemsInit;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import org.slf4j.Logger;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Curio behavior for the addon's own Heart Locket item.
 *
 * <p>While worn in any curios slot:
 * <ul>
 *   <li><b>Love aura</b> — a {@code Girlfriend} or {@code Boyfriend} within
 *       {@value #RANGE} blocks grants the wearer Regeneration I (server-side,
 *       rescanned once a second, beacon-style short refresh).</li>
 *   <li><b>Calming</b> — Girlfriends never turn on the wearer. Verified via
 *       {@code javap} against orespawn-1.21.1-1.0.0-beta.3: the only hostile
 *       path a Girlfriend has is {@code Girlfriend$ValentineTargetGoal}
 *       (a {@code NearestAttackableTargetGoal} gated on
 *       {@code isValentineAngry()}, i.e. Feb 14 + {@code DATA_FEELING_BETTER
 *       == 0}) whose predicate targets any living entity except her owner and
 *       fellow tames — so the entity she acquires <i>is the wearer</i>, not
 *       "the wearer's attacker". There is no public anger setter, so calming
 *       means suppressing that acquisition: a {@link LivingChangeTargetEvent}
 *       hook cancels her targeting a locket wearer within range, and the tick
 *       scan clears a target already latched before the locket was equipped.</li>
 *   <li><b>ALWAYS_KEEP everywhere</b> — unlike {@link OreSpawnCurio}'s
 *       dimension-gated rule, a keepsake is never dropped on death, in any
 *       dimension (both drop-rule arities overridden; Curios 9.5.1's
 *       ItemizedCurioCapability forwards the int-arity one).</li>
 * </ul>
 *
 * <p>Only ever classloaded when the curios mod is present — {@code ItemsInit}
 * guards the {@link #register()} call behind {@code ModList.isLoaded("curios")}
 * on the same FMLCommonSetupEvent/enqueueWork path {@link CuriosCompat} uses.
 * CuriosCompat only registers behavior for the five {@code orespawn:*} drops,
 * and this class only registers the addon's {@code heart_locket}, so no item
 * is ever registered with Curios twice.
 */
public final class GirlfriendCharm implements ICurioItem {

    private static final Logger LOGGER = OreSpawnIntegrations.LOGGER;

    /** Aura + calming radius in blocks. */
    private static final double RANGE = 8.0D;
    private static final double RANGE_SQ = RANGE * RANGE;

    /** Entity scan cadence (ticks); effect outlives one scan so it never flickers. */
    private static final int SCAN_INTERVAL = 20;
    private static final int REGEN_DURATION = SCAN_INTERVAL * 3;

    private GirlfriendCharm() {}

    /** Runs inside FMLCommonSetupEvent#enqueueWork (main thread), exactly once. */
    public static void register() {
        CuriosApi.registerCurio(ItemsInit.HEART_LOCKET.get(), new GirlfriendCharm());
        NeoForge.EVENT_BUS.addListener(GirlfriendCharm::onLivingChangeTarget);
        LOGGER.info("Heart Locket registered as a Curios trinket");
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        LivingEntity wearer = slotContext.entity();
        if (wearer == null || wearer.level().isClientSide()) {
            return;
        }
        // Full entity scans are too heavy for every tick; stagger by slot index.
        if ((wearer.tickCount + slotContext.index()) % SCAN_INTERVAL != 0) {
            return;
        }
        AABB range = wearer.getBoundingBox().inflate(RANGE);
        List<TamableAnimal> partners = wearer.level().getEntitiesOfClass(
                TamableAnimal.class, range,
                e -> e instanceof Girlfriend || e instanceof Boyfriend);
        if (partners.isEmpty()) {
            return;
        }
        // ambient=true, visible=false (no particle spam), showIcon=true
        wearer.addEffect(new MobEffectInstance(
                MobEffects.REGENERATION, REGEN_DURATION, 0, true, false, true));
        for (TamableAnimal partner : partners) {
            // Safety net for anger latched before the locket went on: the
            // change-target hook below can only cancel new acquisitions.
            if (partner instanceof Girlfriend girlfriend && girlfriend.getTarget() == wearer) {
                girlfriend.setTarget(null);
            }
        }
    }

    /** A keepsake is never lost on death, anywhere. */
    @Override
    public ICurio.DropRule getDropRule(SlotContext slotContext, DamageSource source,
                                       boolean recentlyHit, ItemStack stack) {
        return ICurio.DropRule.ALWAYS_KEEP;
    }

    // Deprecated-for-removal upstream, but in Curios 9.5.1 this int-arity overload
    // is the one ItemizedCurioCapability forwards to on death (see OreSpawnCurio),
    // so it must be overridden too or the drop rule never fires.
    @SuppressWarnings("removal")
    @Override
    public ICurio.DropRule getDropRule(SlotContext slotContext, DamageSource source,
                                       int lootingLevel, boolean recentlyHit, ItemStack stack) {
        return ICurio.DropRule.ALWAYS_KEEP;
    }

    @Override
    public List<Component> getSlotsTooltip(List<Component> tooltips, Item.TooltipContext context,
                                           ItemStack stack) {
        List<Component> result =
                new ArrayList<>(ICurioItem.super.getSlotsTooltip(tooltips, context, stack));
        result.add(Component.translatable("curios.orespawn_integrations.heart_locket.tooltip")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        result.add(Component.translatable("curios.orespawn_integrations.heart_locket.tooltip2")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        result.add(Component.translatable("curios.orespawn_integrations.heart_locket.tooltip3")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        return result;
    }

    /**
     * Calming, part 1: a Girlfriend acquiring a locket wearer within range as
     * her attack target is cancelled at the source (same shape as the Kraken
     * ward in {@link CuriosCompat}).
     */
    private static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Girlfriend girlfriend)
                || !(event.getNewAboutToBeSetTarget() instanceof Player player)) {
            return;
        }
        if (girlfriend.distanceToSqr(player) > RANGE_SQ) {
            return;
        }
        try {
            boolean calmed = CuriosApi.getCuriosInventory(player)
                    .flatMap(inventory -> inventory.findFirstCurio(ItemsInit.HEART_LOCKET.get()))
                    .isPresent();
            if (calmed) {
                event.setCanceled(true);
            }
        } catch (Exception e) {
            // Never let a curios inventory hiccup break mob AI ticking.
            LOGGER.debug("Heart Locket calm check failed for {}", player.getScoreboardName(), e);
        }
    }
}
