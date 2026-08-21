package danger.orespawn.integrations.items;

import danger.orespawn.integrations.OreSpawnIntegrations;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registered items for the "modern wonders" content (Tier 3): the two boss
 * vault keys and the Kraken armor-trim smithing template. Like
 * {@link ItemsInit} this class is initialized unconditionally from the mod
 * constructor — registry events must always fire regardless of which optional
 * partner mods are present — and every item here stays a plain {@link Item}:
 * the keys are loot-table/lock tokens and the template is referenced only from
 * data (trim_pattern/kraken.json and the smithing recipes), so none of them
 * need item-class behavior.
 */
public final class ModernItemsInit {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(OreSpawnIntegrations.MODID);

    /** Opens the Mobzilla vault (chests/mobzilla_vault loot). */
    public static final DeferredItem<Item> MOBZILLA_VAULT_KEY = ITEMS.registerSimpleItem(
            "mobzilla_vault_key", new Item.Properties().stacksTo(16).rarity(Rarity.RARE));

    /** Opens the Kraken vault (chests/kraken_vault loot). */
    public static final DeferredItem<Item> KRAKEN_VAULT_KEY = ITEMS.registerSimpleItem(
            "kraken_vault_key", new Item.Properties().stacksTo(16).rarity(Rarity.RARE));

    /**
     * Template item for the orespawn_integrations:kraken trim pattern. The
     * smithing-trim and duplication recipes live in data/, mirroring how
     * vanilla wires its own trim templates.
     */
    public static final DeferredItem<Item> KRAKEN_TRIM_TEMPLATE = ITEMS.registerSimpleItem(
            "kraken_armor_trim_smithing_template",
            new Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON));

    private ModernItemsInit() {}

    /** Called once, unconditionally, from the {@code OreSpawnIntegrations} constructor. */
    public static void init(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(ModernItemsInit::addToCreativeTabs);
    }

    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        // Same tab vanilla uses for its smithing templates and trim materials.
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(MOBZILLA_VAULT_KEY);
            event.accept(KRAKEN_VAULT_KEY);
            event.accept(KRAKEN_TRIM_TEMPLATE);
        }
    }
}
