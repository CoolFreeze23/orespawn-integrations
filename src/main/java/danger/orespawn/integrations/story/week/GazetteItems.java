package danger.orespawn.integrations.story.week;

import com.mojang.serialization.Codec;
import danger.orespawn.integrations.OreSpawnIntegrations;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.WrittenBookItem;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * The Interregnum Gazette's item (item-diario-oficial): {@code orespawn_integrations:diario_oficial}, a written-book
 * type built at dawn, one to a stack (every copy is a different day). Two data components: {@code edicao}, the Land's
 * day it was printed (the card's id), and {@code lida}, set the first time it is opened, which is how "never two
 * unread" tells a read copy in a box from an unread one. Old copies are ordinary items: they shelve like any written
 * book, and nothing reads them back.
 */
public final class GazetteItems {

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(OreSpawnIntegrations.MODID);
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, OreSpawnIntegrations.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> EDICAO =
            COMPONENTS.registerComponentType("edicao",
                    b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> LIDA =
            COMPONENTS.registerComponentType("lida",
                    b -> b.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));

    public static final DeferredItem<GazetteItem> DIARIO_OFICIAL =
            ITEMS.registerItem("diario_oficial", GazetteItem::new, new Item.Properties().stacksTo(1));

    private GazetteItems() {
    }

    static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
        ITEMS.register(modBus);
    }

    /** True for a copy nobody has opened yet. */
    public static boolean unread(ItemStack stack) {
        return stack.is(DIARIO_OFICIAL.get()) && !Boolean.TRUE.equals(stack.get(LIDA.get()));
    }

    /** The Gazette: a written book whose name and tooltip are its own translate lines, never the plain title. */
    public static final class GazetteItem extends WrittenBookItem {

        public GazetteItem(Item.Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (!level.isClientSide()) {
                stack.set(LIDA.get(), true);
            }
            return super.use(level, player, hand);
        }

        @Override
        public Component getName(ItemStack stack) {
            return Component.translatable(getDescriptionId(stack));
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                    TooltipFlag flag) {
            Integer day = stack.get(EDICAO.get());
            if (day != null) {
                tooltip.add(Component.translatable("item.orespawn_integrations.diario_oficial.edition", day)
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
            }
        }
    }
}
