package danger.orespawn.integrations.items;

import danger.orespawn.integrations.OreSpawnIntegrations;
import danger.orespawn.integrations.compat.curios.GirlfriendCharm;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The addon's own registered items (Tier 3). Unlike the compat packages this
 * class is initialized unconditionally from the mod constructor: registry
 * events must always fire, whether or not any optional partner mod is present.
 *
 * <p>Items registered here must stay plain {@link Item}s — any behavior that
 * touches an optional mod's classes (e.g. the Heart Locket's Curios trinket
 * logic in {@link GirlfriendCharm}) is attached from a guarded listener so the
 * optional class is never loaded when its mod is absent.
 */
public final class ItemsInit {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(OreSpawnIntegrations.MODID);

    /**
     * Heart Locket — a craftable keepsake (gold + ruby + poppy) that is also a
     * rare Girlfriend drop. As a Curios charm it grants a Regeneration aura
     * near a Girlfriend/Boyfriend and keeps Girlfriends from turning on the
     * wearer; see {@link GirlfriendCharm} for the verified behavior notes.
     */
    public static final DeferredItem<Item> HEART_LOCKET = ITEMS.registerSimpleItem(
            "heart_locket", new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));

    private ItemsInit() {}

    /** Called once, unconditionally, from the {@code OreSpawnIntegrations} constructor. */
    public static void init(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(ItemsInit::addToCreativeTabs);

        // Same FMLCommonSetupEvent + enqueueWork path CuriosCompat uses for the
        // five OreSpawn-item trinkets; this listener only handles the addon's
        // own Heart Locket, so the two registrations never overlap. The
        // isLoaded guard keeps GirlfriendCharm (which implements Curios'
        // ICurioItem) from ever being classloaded without the curios mod.
        if (ModList.get().isLoaded("curios")) {
            modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
                try {
                    GirlfriendCharm.register();
                } catch (Throwable t) {
                    // A Curios API mismatch must degrade the trinket, never kill the pack.
                    OreSpawnIntegrations.LOGGER.error(
                            "Heart Locket curio registration failed - it stays a plain item", t);
                }
            }));
        }
    }

    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(HEART_LOCKET);
        }
    }
}
