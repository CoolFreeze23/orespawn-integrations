package danger.orespawn.integrations.compat.travelersbackpack;

import com.tiviacz.travelersbackpack.blocks.TravelersBackpackBlock;
import com.tiviacz.travelersbackpack.components.RenderInfo;
import com.tiviacz.travelersbackpack.init.ModBlockEntityTypes;
import com.tiviacz.travelersbackpack.init.ModDataComponents;
import com.tiviacz.travelersbackpack.items.TravelersBackpackItem;
import danger.orespawn.integrations.OreSpawnIntegrations;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Travelers Backpack integration (Thread 4 "Big Game"): three OreSpawn-themed
 * backpack variants — Mobzilla-Hide, Kraken, and Girlfriend's.
 *
 * <p>API surface verified against {@code travelersbackpack-neoforge-1.21.1-10.1.38.jar}
 * (javap on the class files; version-range note: 10.1.x line — re-verify on a
 * major TB bump because the legacy texture-field members are
 * {@code @Deprecated(forRemoval)}):
 * <ul>
 *   <li>{@code TravelersBackpackBlock(BlockBehaviour.Properties)} — public ctor;
 *       TB's own ModBlocks uses exactly
 *       {@code Properties.of().mapColor(...).sound(SoundType.WOOL)}.</li>
 *   <li>{@code TravelersBackpackItem(Block)} — the one PUBLIC ctor that is NOT
 *       {@code @Deprecated(forRemoval)} in 10.1.38 (javap: the {@code texture}
 *       field, the (Block, String) and (Block, ResourceLocation) overloads and
 *       {@code getBackpackTexture()} all carry the for-removal marker; the
 *       plain (Block) ctor does not). It applies {@code stacksTo(1)} + TB's
 *       TIER/IS_VISIBLE default components itself. The legacy texture RL is
 *       dead weight in 10.1.38: rendering is fully driven by the
 *       {@code travelersbackpack:backpack} model loader (worn rendering goes
 *       through {@code BackpackModel} -> {@code ItemRenderer.renderStatic},
 *       i.e. the item's baked model), so our variants' single 64x64 texture is
 *       wired purely through the block-model JSON.</li>
 *   <li>{@code ModBlockEntityTypes.BACKPACK} — its validBlocks array is a fixed
 *       44-entry list of TB's own blocks, so third-party variant blocks MUST be
 *       injected via NeoForge's {@link BlockEntityTypeAddBlocksEvent} (present
 *       in neoforge-21.1.223 sources, GameData posts it after registry freeze;
 *       pack runtime 21.1.248 — stable across 21.1.x). Without the injection,
 *       1.21.1 LevelChunk.setBlockEntity rejects the placed backpack's BE.</li>
 *   <li>Creative tab {@code travelersbackpack:travelersbackpack} — id verified
 *       in TB's ModCreativeTabs.</li>
 * </ul>
 *
 * <p>Assets per variant (formats verified against TB's bat variant): a single
 * 64x64 texture under {@code textures/block/backpack/}, a 4-line loader model,
 * a parent-only item model, a 4-rotation blockstate, and a copy_components
 * block loot table. Recipes are pure data gated on
 * [travelersbackpack, orespawn, thread_enabled:big_game].
 *
 * <p>Only ever classloaded when the travelersbackpack mod is present — the main
 * mod class invokes {@link #init(IEventBus)} reflectively (COMPAT table), so TB
 * classes referenced here are compileOnly and never load without their jar.
 */
public final class OreSpawnPacksCompat {

    private static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(OreSpawnIntegrations.MODID);
    private static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(OreSpawnIntegrations.MODID);

    private static final ResourceLocation TB_TAB =
            ResourceLocation.fromNamespaceAndPath("travelersbackpack", "travelersbackpack");

    /** Mobzilla-Hide — black/purple scale plates; crafted from Mobzilla's hide. */
    public static final DeferredBlock<TravelersBackpackBlock> MOBZILLA_HIDE_BACKPACK_BLOCK =
            backpackBlock("mobzilla_hide_backpack", MapColor.COLOR_BLACK);
    public static final DeferredItem<TravelersBackpackItem> MOBZILLA_HIDE_BACKPACK =
            backpackItem("mobzilla_hide_backpack", MOBZILLA_HIDE_BACKPACK_BLOCK);

    /** Kraken — teal/ink with tentacle sucker rows; kraken tooth + sea monster scale. */
    public static final DeferredBlock<TravelersBackpackBlock> KRAKEN_BACKPACK_BLOCK =
            backpackBlock("kraken_backpack", MapColor.COLOR_CYAN);
    public static final DeferredItem<TravelersBackpackItem> KRAKEN_BACKPACK =
            backpackItem("kraken_backpack", KRAKEN_BACKPACK_BLOCK);

    /** Girlfriend's — pink with a heart clasp; cross-links Thread 3's arc. */
    public static final DeferredBlock<TravelersBackpackBlock> GIRLFRIEND_BACKPACK_BLOCK =
            backpackBlock("girlfriend_backpack", MapColor.COLOR_PINK);
    public static final DeferredItem<TravelersBackpackItem> GIRLFRIEND_BACKPACK =
            backpackItem("girlfriend_backpack", GIRLFRIEND_BACKPACK_BLOCK);

    private OreSpawnPacksCompat() {}

    private static DeferredBlock<TravelersBackpackBlock> backpackBlock(String name, MapColor color) {
        // Mirrors TB's own ModBlocks properties exactly (mapColor + wool sound,
        // default strength) so our variants behave like every stock variant.
        return BLOCKS.register(name, () -> new TravelersBackpackBlock(
                BlockBehaviour.Properties.of().mapColor(color).sound(SoundType.WOOL)));
    }

    private static DeferredItem<TravelersBackpackItem> backpackItem(
            String name, DeferredBlock<TravelersBackpackBlock> block) {
        // The (Block) ctor is the only non-@Deprecated(forRemoval) public ctor
        // in 10.1.38; the legacy texture RL it stores is unused (rendering is
        // model-loader-driven, see class javadoc), so nothing else is needed.
        return ITEMS.register(name, () -> new TravelersBackpackItem(block.get()));
    }

    public static void init(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(OreSpawnPacksCompat::onAddValidBlocks);
        modBus.addListener(OreSpawnPacksCompat::onBuildCreativeTabs);
        // TB registers its BEWLR client extension by iterating ITS OWN item
        // registry (ModClientEventHandler.registerClientExtenstions, bytecode
        // 10.1.38), so foreign TravelersBackpackItems render as nothing in GUI/
        // hand/worn without this — the loader model has no standard quads.
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            modBus.addListener(OreSpawnPacksClient::onRegisterClientExtensions);
        }
    }

    /**
     * Injects the three variant blocks into TB's BACKPACK block-entity type.
     * Fired once by GameData after all registries are frozen, so every
     * DeferredHolder below is resolvable. All of TB's valid blocks are
     * TravelersBackpackBlock instances, so the event's common-superclass
     * check accepts ours.
     */
    private static void onAddValidBlocks(BlockEntityTypeAddBlocksEvent event) {
        event.modify(ModBlockEntityTypes.BACKPACK.get(),
                MOBZILLA_HIDE_BACKPACK_BLOCK.get(),
                KRAKEN_BACKPACK_BLOCK.get(),
                GIRLFRIEND_BACKPACK_BLOCK.get());
    }

    /**
     * Lists the three packs alongside the stock variants in TB's own tab.
     * Tab entries carry TB's showcase RenderInfo component — in 10.x, tanks
     * are an installable upgrade, and TB's own tab stacks only render side
     * tanks because ModCreativeTabs sets RENDER_INFO to
     * {@link RenderInfo#createCreativeTabInfo()} (bytecode-verified 10.1.38).
     * Without it our packs display in the bare no-upgrade state.
     */
    private static void onBuildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().location().equals(TB_TAB)) {
            event.accept(displayStack(MOBZILLA_HIDE_BACKPACK));
            event.accept(displayStack(KRAKEN_BACKPACK));
            event.accept(displayStack(GIRLFRIEND_BACKPACK));
        }
    }

    private static net.minecraft.world.item.ItemStack displayStack(DeferredItem<TravelersBackpackItem> item) {
        net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item.get());
        stack.set(ModDataComponents.RENDER_INFO.get(), RenderInfo.createCreativeTabInfo());
        return stack;
    }
}
