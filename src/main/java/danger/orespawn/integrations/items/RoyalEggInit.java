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
 * THREAD 2 "The Royal Court" registered item: the Royal Dragon Egg
 * (CROSSMOD-THREADS.md item 9 — "long incubation; hatchling = living trophy").
 * Like {@link ItemsInit} / {@link ModernItemsInit} this class is initialized
 * unconditionally from the mod constructor — registry events must always fire
 * regardless of which optional partner mods are present — and the egg stays a
 * plain {@link Item}: the ENTIRE hatch feature is one data-driven
 * {@code aether:incubation} recipe
 * (data/orespawn_integrations/recipe/royal_dragon_egg_incubation.json), so no
 * partner class is ever referenced from Java and no partner jar is needed on
 * the compile classpath.
 *
 * <p>Feature contract (verified for policy 4, thread2-court-verification.json
 * Q2, all against the pack jars / port mirror):
 * <ul>
 *   <li>{@code aether:incubation} recipe schema —
 *       {@code com.aetherteam.aether.recipe.recipes.item.IncubationRecipe$Serializer}
 *       codec fields {@code entity} / {@code incubationtime} / {@code ingredient}
 *       (+ optional {@code tag}, {@code group}), verified via bytecode in
 *       aether-1.21.1-1.5.10-neoforge.jar. The Incubator's egg slot has no
 *       {@code mayPlace} filter ({@code IncubatorItemSlot}), so this custom egg
 *       item is accepted and simply waits for its matching recipe.</li>
 *   <li>Hatch behavior — {@code IncubatorBlockEntity.incubate} spawns the
 *       recipe's entity via {@code EntityType.spawn(..., MobSpawnType.TRIGGERED,
 *       ...)}; it never assigns ownership, and none is needed:
 *       {@code danger.orespawn.entity.ThePrince.customServerAiStep} (port
 *       mirror source) auto-tames to the nearest player within 10 blocks on its
 *       first server tick — the hatchling imprints on whoever is brooding over
 *       the incubator, zero code.</li>
 *   <li>Registration surface — NeoForge 21.1.223
 *       {@code DeferredRegister.Items#registerSimpleItem} /
 *       {@code DeferredItem}, same contract every other items class here builds
 *       on; pack runtime is NeoForge 21.1.248, API stable across 21.1.x.</li>
 * </ul>
 *
 * <p>Gating lives on the data files, not here: the shaped recipe (8x
 * {@code orespawn:queen_scale} around a {@code minecraft:egg}) carries
 * {@code [orespawn, royal_court]} conditions and the incubation recipe carries
 * {@code [aether, orespawn, royal_court]}, so with Aether or OreSpawn removed
 * — or the royal_court thread toggled off — the item still registers (ids must
 * never vanish from worlds) but becomes unobtainable and inert.
 */
public final class RoyalEggInit {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(OreSpawnIntegrations.MODID);

    /**
     * The Royal Dragon Egg — post-Queen trophy craft. A full day of brooding
     * royalty (24000 ticks) in the Aether Incubator hatches
     * {@code orespawn:the_prince}, a living trophy that imprints on the player
     * standing watch.
     */
    public static final DeferredItem<Item> ROYAL_DRAGON_EGG = ITEMS.registerSimpleItem(
            "royal_dragon_egg", new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));

    private RoyalEggInit() {}

    /** Called once, unconditionally, from the {@code OreSpawnIntegrations} constructor. */
    public static void init(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(RoyalEggInit::addToCreativeTabs);
    }

    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        // Beside the Heart Locket: the addon's companion/keepsake items live in
        // Tools & Utilities.
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ROYAL_DRAGON_EGG);
        }
    }
}
