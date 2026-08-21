package danger.orespawn.integrations.items;

import java.util.List;

import danger.orespawn.integrations.OreSpawnIntegrations;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registered items for Thread 1 - "It Was Always Uranium": Bottled Uranium
 * (the Uranium Rush drink) and the Uranium Arc Reactor (the dirty palladium
 * substitute the Iron Man suit-upgrade clones consume). Like
 * {@link ModernItemsInit} this class is initialized unconditionally from the
 * mod constructor - registry events must always fire regardless of which
 * optional partner mods are present. Every cross-mod hook that touches these
 * items is pure data gated by {@code neoforge:mod_loaded} +
 * {@code orespawn_integrations:thread_enabled("uranium")} conditions, so this
 * class references no partner classes at all.
 *
 * <p>POLICY 4 - APIs verified against jars (2026-08-17):</p>
 * <ul>
 * <li>{@link FoodProperties} record ({@code nutrition}, {@code saturation},
 *     {@code canAlwaysEat}, {@code eatSeconds}, {@code usingConvertsTo},
 *     {@code effects} of {@code PossibleEffect(effect, probability)}) and the
 *     drink pattern ({@code getUseAnimation -> UseAnim.DRINK}, default
 *     {@code Item.use} starting the food use, default
 *     {@code Item.getUseDuration -> eatDurationTicks()}) - verified in the
 *     decompiled 1.21.1 sources (neoFormJoined1.21.1-20240808.144430,
 *     NeoForge 21.1.223 dev): {@code net.minecraft.world.food.FoodProperties},
 *     {@code net.minecraft.world.item.OminousBottleItem},
 *     {@code net.minecraft.world.item.Item} (use/getUseDuration/finishUsingItem),
 *     {@code net.minecraft.world.entity.player.Player#eat} (which returns the
 *     {@code using_converts_to} container - our empty glass bottle).</li>
 * <li>Partner surfaces consumed by this thread's DATA (no Java references):
 *     marvel-2.1.0-pre6-1.21.1-neoforge.jar {@code marvel:suit_upgrading}
 *     recipe shape (mark 11/15/17 chestplates consume
 *     {@code marvel:diamond_arc_reactor}); moonlight-1.21.1-3.3.3-neoforge.jar
 *     {@code data/<ns>/moonlight/soft_fluid/} datapack registry;
 *     amendments-neoforge-1.21-2.1.7.jar
 *     {@code data/amendments/tags/moonlight/soft_fluid/can_glow.json};
 *     ProjectE-1.21.1-PE1.1.0.jar {@code pe_custom_conversions} codec
 *     ({@code IPECodecHelper.positiveLong()} or the {@code "free"} sentinel -
 *     literal 0 is rejected, so EMC-less pinning uses {@code "free"}).</li>
 * </ul>
 * <p>Version-range note: the food component surface is stable across 1.21.0/
 * 1.21.1; the partner formats above were verified only against the exact pack
 * jars listed and are all condition-gated data, so a partner format change
 * degrades to unloaded JSON, never a crash.</p>
 */
public final class UraniumItemsInit {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(OreSpawnIntegrations.MODID);

    /**
     * "Uranium Rush" - reward, never punish: ~20s of Glowing + Night Vision +
     * Speed II, no negative effects, hands the glass bottle back. Drinkable at
     * full hunger (alwaysEdible) because it is a buff drink, not food.
     */
    private static final FoodProperties URANIUM_RUSH = new FoodProperties.Builder()
            .nutrition(1)
            .saturationModifier(0.1F)
            .alwaysEdible()
            .usingConvertsTo(Items.GLASS_BOTTLE)
            .effect(() -> new MobEffectInstance(MobEffects.GLOWING, 20 * 20, 0), 1.0F)
            .effect(() -> new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * 20, 0), 1.0F)
            .effect(() -> new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 20, 1), 1.0F)
            .build();

    /**
     * Bottled Uranium. Crafted from a glass bottle + 2 uranium nuggets
     * (recipe/uranium_bottle.json, orespawn+thread gated) and doubles as the
     * BOTTLE container of the moonlight soft fluid
     * {@code orespawn_integrations:liquid_uranium}, so it can be poured into
     * and drawn from an Amendments liquid cauldron. Deliberately EMC-less
     * (pe_custom_conversions/uranium_thread_hygiene.json) so the condenser can
     * never print a triple-buff drink.
     */
    public static final DeferredItem<Item> URANIUM_BOTTLE = ITEMS.register(
            "uranium_bottle",
            () -> new UraniumBottleItem(new Item.Properties().stacksTo(16).food(URANIUM_RUSH)));

    /**
     * Uranium Arc Reactor. Ring of c:ingots/uranium around
     * orespawn:block_uranium (recipe/dirty_arc_reactor.json); consumed in
     * place of marvel's diamond arc reactor by the cloned mark 11/15/17
     * chestplate suit-upgrading recipes. Also EMC-less on purpose.
     */
    public static final DeferredItem<Item> DIRTY_ARC_REACTOR = ITEMS.register(
            "dirty_arc_reactor",
            () -> new DirtyArcReactorItem(new Item.Properties().rarity(Rarity.RARE)));

    private UraniumItemsInit() {}

    /** Called once, unconditionally, from the {@code OreSpawnIntegrations} constructor. */
    public static void init(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(UraniumItemsInit::addToCreativeTabs);
    }

    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        // Same tab the rest of the addon's craft-material items use.
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(URANIUM_BOTTLE);
            event.accept(DIRTY_ARC_REACTOR);
        }
    }

    /**
     * Vanilla ominous/honey-bottle drink pattern: the FOOD component drives
     * use duration and effect application, this class only swaps the animation
     * and sounds from eating to drinking. {@code Player.eat} returns the
     * {@code using_converts_to} glass bottle for us.
     */
    private static final class UraniumBottleItem extends Item {

        UraniumBottleItem(Item.Properties properties) {
            super(properties);
        }

        @Override
        public UseAnim getUseAnimation(ItemStack stack) {
            return UseAnim.DRINK;
        }

        @Override
        public SoundEvent getDrinkingSound() {
            return SoundEvents.GENERIC_DRINK;
        }

        @Override
        public SoundEvent getEatingSound() {
            return SoundEvents.GENERIC_DRINK;
        }
    }

    /** Plain item plus the thread's signature tooltip line. */
    private static final class DirtyArcReactorItem extends Item {

        DirtyArcReactorItem(Item.Properties properties) {
            super(properties);
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                List<Component> tooltip, TooltipFlag flag) {
            super.appendHoverText(stack, context, tooltip, flag);
            tooltip.add(Component
                    .translatable("item.orespawn_integrations.dirty_arc_reactor.tooltip")
                    .withStyle(ChatFormatting.GREEN, ChatFormatting.ITALIC));
        }
    }
}
