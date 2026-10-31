package danger.orespawn.integrations.story.chaos;

import danger.orespawn.integrations.config.IntegrationsConfig;
import danger.orespawn.integrations.story.StoryCore;
import danger.orespawn.integrations.story.StoryScheduler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.GrindstoneEvent;
import net.neoforged.neoforge.event.enchanting.EnchantmentLevelSetEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * The Loaner's counter (item-hoard-joke-armoury, on system-hoard-caskets): a Loaner carries {@code cc_loaner} custom
 * data, an int of swings left (the casket's Big Bertha starts at 24). Each attack made with it takes one off; at 0 the
 * stack breaks with the actionbar line. Durability never decides anything, so Mending, Unbreaking, Duct Tape or a
 * repair can't stretch it. An anvil or a grindstone with a Loaner in either slot gives nothing, and an enchanting
 * table (vanilla or Apothic, which fires the same event) offers it nothing.
 */
public final class LoanerWeapons {

    public static final String KEY = "cc_loaner";

    private LoanerWeapons() {
    }

    public static void init(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(LoanerWeapons::onAttack);
        NeoForge.EVENT_BUS.addListener(LoanerWeapons::onAnvil);
        NeoForge.EVENT_BUS.addListener(LoanerWeapons::onGrindstone);
        NeoForge.EVENT_BUS.addListener(LoanerWeapons::onEnchant);
        NeoForge.EVENT_BUS.addListener(LoanerWeapons::onTooltip);
    }

    public static boolean isLoaner(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.contains(KEY);
    }

    /** Swings left, or -1 for a stack that is not a Loaner. */
    public static int swingsLeft(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null || !data.contains(KEY) ? -1 : data.copyTag().getInt(KEY);
    }

    /** Takes one swing off; true when that was the last one. */
    public static boolean spendSwing(ItemStack stack) {
        int left = Math.max(0, swingsLeft(stack) - 1);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(KEY, left));
        return left <= 0;
    }

    private static boolean on() {
        return IntegrationsConfig.on(IntegrationsConfig.storyLoanerWeapons);
    }

    private static void onAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !on()) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!isLoaner(stack)) {
            return;
        }
        try {
            if (spendSwing(stack)) {
                // The last swing still lands with the Loaner in hand; it goes back to the Treasury a tick later.
                StoryScheduler.schedule(player.getServer(), 1, "loaner.break", () -> {
                    if (isLoaner(stack) && swingsLeft(stack) <= 0) {
                        stack.setCount(0);
                        player.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_BREAK,
                                SoundSource.PLAYERS, 1.0F, 1.0F);
                        player.displayClientMessage(Component.translatable("orespawn_integrations.loaner.broken"), true);
                    }
                });
            }
        } catch (Throwable t) {
            StoryCore.logOnce("loaner.attack", t);
        }
    }

    private static void onAnvil(AnvilUpdateEvent event) {
        if (on() && (isLoaner(event.getLeft()) || isLoaner(event.getRight()))) {
            event.setCanceled(true);
            refuse(event.getPlayer());
        }
    }

    private static void onGrindstone(GrindstoneEvent.OnPlaceItem event) {
        if (on() && (isLoaner(event.getTopItem()) || isLoaner(event.getBottomItem()))) {
            event.setCanceled(true);
        }
    }

    private static void onEnchant(EnchantmentLevelSetEvent event) {
        if (on() && isLoaner(event.getItem())) {
            event.setEnchantLevel(0);
            if (event.getEnchantRow() == 0 && !event.getLevel().isClientSide()) {
                Player near = event.getLevel().getNearestPlayer(event.getPos().getX() + 0.5, event.getPos().getY() + 0.5,
                        event.getPos().getZ() + 0.5, 8.0, false);
                refuse(near);
            }
        }
    }

    private static void refuse(Player player) {
        if (player instanceof ServerPlayer server) {
            server.displayClientMessage(Component.translatable("orespawn_integrations.loaner.refit"), true);
        }
    }

    private static void onTooltip(ItemTooltipEvent event) {
        int left = swingsLeft(event.getItemStack());
        if (left >= 0) {
            event.getToolTip().add(Component.translatable("orespawn_integrations.loaner.tooltip", left)
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
