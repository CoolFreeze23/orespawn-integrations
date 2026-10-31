package danger.orespawn.integrations.items;

import danger.orespawn.integrations.story.shelf9.Eggshell;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Your Eggshell. Using it opens it once on the server ({@link Eggshell#open}): the kit drops into the inventory and
 * the shell crumbles. The client only swings the hand; everything else is server side, so a laggy click never opens a
 * shell twice. Touches only vanilla and our own classes, so it is safe to register unconditionally.
 */
public class HatchlingShellItem extends Item {

    public HatchlingShellItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            Eggshell.open(serverPlayer, stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
