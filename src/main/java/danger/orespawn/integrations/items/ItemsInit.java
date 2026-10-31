package danger.orespawn.integrations.items;

import danger.orespawn.integrations.OreSpawnIntegrations;
import danger.orespawn.integrations.compat.curios.GirlfriendCharm;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * The addon's own registered items (Tier 3). Unlike the compat packages this
 * class is initialized unconditionally from the mod constructor: registry
 * events must always fire, whether or not any optional partner mod is present.
 *
 * <p>Items registered here must stay plain {@link Item}s. Any behavior that
 * touches an optional mod's classes (e.g. the Heart Locket's Curios trinket
 * logic in {@link GirlfriendCharm}) is attached from a guarded listener so the
 * optional class is never loaded when its mod is absent.
 */
public final class ItemsInit {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(OreSpawnIntegrations.MODID);

    /**
     * Heart Locket: a craftable keepsake (gold + ruby + poppy) that is also a
     * rare Girlfriend drop. As a Curios charm it grants a Regeneration aura
     * near a Girlfriend/Boyfriend and keeps Girlfriends from turning on the
     * wearer; see {@link GirlfriendCharm} for the verified behavior notes.
     */
    public static final DeferredItem<Item> HEART_LOCKET = ITEMS.registerSimpleItem(
            "heart_locket", new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));

    /**
     * Your Eggshell (item-hatchling-shell): given once at a new player's first join; using it rolls the kit table
     * {@code orespawn_integrations:gameplay/hatchling_shell} and crumbles. Stack 1. Its lore lines are on the item
     * itself, so every shell reads the same.
     */
    public static final DeferredItem<Item> HATCHLING_SHELL = ITEMS.register("hatchling_shell",
            () -> new HatchlingShellItem(new Item.Properties().stacksTo(1)
                    .component(DataComponents.LORE, lore(
                            "orespawn_integrations.hatchling_shell.lore",
                            "orespawn_integrations.hatchling_shell.lore_use"))));

    /**
     * Ledger Scrap (item-ledger-scrap): the court's first coin. Stack 64; one default lore line, the same on every
     * scrap so stacks always merge. Its behavior (the field Correction, the sources) belongs to the registry group
     * and reaches the item by id.
     */
    public static final DeferredItem<Item> LEDGER_SCRAP = ITEMS.registerSimpleItem("ledger_scrap",
            new Item.Properties().stacksTo(64)
                    .component(DataComponents.LORE, lore("orespawn_integrations.ledger_scrap.lore")));

    /** Correction Slip (item-ledger-scrap): pays one Correction. Stack 64; two fixed lore lines, so slips from two days stack. */
    public static final DeferredItem<Item> GUIA_DE_AVERBACAO = ITEMS.registerSimpleItem("guia_de_averbacao",
            new Item.Properties().stacksTo(64)
                    .component(DataComponents.LORE, lore(
                            "orespawn_integrations.guia_de_averbacao.lore_1",
                            "orespawn_integrations.guia_de_averbacao.lore_2")));

    private ItemsInit() {}

    /** Default lore as translate keys, grey and upright like a printed label rather than the purple italic of a renamed item. */
    private static ItemLore lore(String... keys) {
        List<Component> lines = new java.util.ArrayList<>();
        for (String key : keys) {
            lines.add(Component.translatable(key).withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GRAY)));
        }
        return new ItemLore(lines);
    }

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
            event.accept(HATCHLING_SHELL);
            event.accept(LEDGER_SCRAP);
            event.accept(GUIA_DE_AVERBACAO);
        }
    }
}
