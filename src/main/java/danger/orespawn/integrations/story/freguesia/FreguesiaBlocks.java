package danger.orespawn.integrations.story.freguesia;

import danger.orespawn.integrations.OreSpawnIntegrations;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The two things that start a Freguesia (item-marco-da-freguesia): the Parish Stone, a two-block boundary stone
 * ({@code orespawn_integrations:marco_da_freguesia}, stack 16), and the Plot Stake
 * ({@code orespawn_integrations:estaca_de_lote}, stack 64), a thin stake placed on a block top as one corner of a plot.
 * Neither has a block entity: the stone's record lives in the Freguesia SavedData and on one marker entity. A piston
 * breaks the stone rather than moving it, so the record always knows where its stone stood.
 */
public final class FreguesiaBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(OreSpawnIntegrations.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(OreSpawnIntegrations.MODID);

    public static final DeferredBlock<MarcoBlock> MARCO = BLOCKS.register("marco_da_freguesia",
            () -> new MarcoBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.0F, 6.0F)
                    .sound(SoundType.STONE).noOcclusion().pushReaction(PushReaction.DESTROY)));
    public static final DeferredBlock<EstacaBlock> ESTACA = BLOCKS.register("estaca_de_lote",
            () -> new EstacaBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(0.5F)
                    .sound(SoundType.WOOD).noOcclusion().noCollission().pushReaction(PushReaction.DESTROY)));

    public static final DeferredItem<BlockItem> MARCO_ITEM = ITEMS.register("marco_da_freguesia",
            () -> new BlockItem(MARCO.get(), new Item.Properties().stacksTo(16)));
    public static final DeferredItem<BlockItem> ESTACA_ITEM = ITEMS.register("estaca_de_lote",
            () -> new BlockItem(ESTACA.get(), new Item.Properties().stacksTo(64)));

    private FreguesiaBlocks() {
    }

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(FreguesiaBlocks::onCreativeTabs);
    }

    private static void onCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(MARCO_ITEM.get());
            event.accept(ESTACA_ITEM.get());
        }
    }

    /** True when {@code block} is the Parish Stone (either half). */
    public static boolean isMarco(Block block) {
        return block == MARCO.get();
    }
}
