package danger.orespawn.integrations.compat.artifacts;

import artifacts.component.ability.EntityCondition;
import artifacts.component.ability.mobeffect.AttackEffects;
import artifacts.component.ability.mobeffect.MobEffectProvider;
import artifacts.config.value.Value;
import artifacts.registry.ModDataComponents;
import danger.orespawn.integrations.OreSpawnIntegrations;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * Emperor's Chitin Band (Thread 4 "Big Game") — a wearable trophy carved from
 * Emperor Scorpion chitin. While equipped in any Artifacts-visible slot it
 * envenoms the wearer's melee hits: Poison II for 4s, on a 5s per-item cooldown.
 *
 * <p><b>Why this works with zero subclassing</b> (verified against
 * artifacts-neoforge-13.2.1, javap bytecode): Artifacts ability handlers are
 * event-driven pure component lookups. {@code AttackEffects.onLivingHurt}
 * resolves the attacker and calls
 * {@code EquipmentHelper.iterateAbilities(ModDataComponents.ATTACK_EFFECTS.get(), ...)},
 * whose per-stack lambda does a raw {@code stack.get(component)} — there is no
 * {@code instanceof WearableArtifactItem} check anywhere in the read path. Any
 * equipped stack carrying the {@code artifacts:attack_effects} component fires.
 * Slot coverage in this pack: ArmorSlotProvider (always) + AccessoriesSlotProvider
 * (accessories-neoforge-1.1.0-beta.42) + CuriosSlotProvider (curios-neoforge-9.5.1,
 * no cclayer), so the band is seen from armor, Accessories, and Curios slots.
 *
 * <p><b>API citations (policy 4)</b>, all javap-verified in artifacts-neoforge-13.2.1:
 * <ul>
 *   <li>{@code artifacts.registry.ModDataComponents.ATTACK_EFFECTS} —
 *       {@code Supplier<DataComponentType<AttackEffects>>}</li>
 *   <li>{@code AttackEffects(List<AttackEffects.Entry>)};
 *       {@code AttackEffects.Entry(MobEffectProvider, Value<Double> chance, Value<Integer> cooldown)}
 *       — cooldown is in SECONDS ({@code addCooldown(item, cooldown * 20)})</li>
 *   <li>{@code MobEffectProvider(Holder<MobEffect>, Value<Integer> level, Value<Integer> duration,
 *       Value<Boolean> spawnParticles, Value<Boolean> showIcon, EntityCondition)} —
 *       duration is in SECONDS ({@code duration * 20 * n + 19} ticks), amplifier is
 *       {@code level - 1}, so level 2 = Poison II</li>
 *   <li>{@code artifacts.config.value.Value.of(T)} — constant wrapper</li>
 *   <li>{@code EntityCondition.ALWAYS}</li>
 * </ul>
 * The component is attached through NeoForge's
 * {@code ModifyDefaultComponentsEvent.modify(ItemLike, Consumer<DataComponentPatch.Builder>)}
 * (neoforge-21.1.223 sources; pack runtime 21.1.248 — stable 21.1.x API), which fires
 * after all registries, so no ordering games against Artifacts' own component
 * registration are needed. Version-range note: verified against Artifacts 13.2.1;
 * the 13.x component API is stable — re-verify on a major-version bump.
 *
 * <p>Equip path + tooltip are data-driven: the band is tag-merged into
 * {@code artifacts:artifacts} and {@code artifacts:slot/hands}
 * (same slot family as {@code artifacts:onion_ring} / {@code artifacts:withered_bracelet}),
 * and Artifacts' component-driven tooltip writer renders the poison ability line
 * automatically. Drop (8%, player-kill, Emperor Scorpion) + scale-craft fallback
 * live in data, gated [artifacts, orespawn, thread_enabled:big_game].
 *
 * <p>Only ever classloaded when the artifacts mod is present — the main mod class
 * invokes {@link #init(IEventBus)} reflectively behind {@code ModList.isLoaded}.
 */
public final class ChitinBandCompat {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(OreSpawnIntegrations.MODID);

    /** Plain item; all Artifacts behavior is component payload, not a class. */
    public static final DeferredItem<Item> EMPERORS_CHITIN_BAND = ITEMS.registerSimpleItem(
            "emperors_chitin_band", new Item.Properties().stacksTo(1).rarity(Rarity.RARE));

    private ChitinBandCompat() {}

    public static void init(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(ChitinBandCompat::attachArtifactAbilities);
        modBus.addListener(ChitinBandCompat::addToCreativeTabs);
    }

    /**
     * Attaches {@code artifacts:attack_effects} as a default component:
     * poison-on-hit, Poison II (level 2), 4s, 100% chance, 5s per-item cooldown.
     */
    private static void attachArtifactAbilities(ModifyDefaultComponentsEvent event) {
        try {
            event.modify(EMPERORS_CHITIN_BAND.get(), builder -> builder.set(
                    ModDataComponents.ATTACK_EFFECTS.get(),
                    new AttackEffects(List.of(new AttackEffects.Entry(
                            new MobEffectProvider(
                                    MobEffects.POISON,
                                    Value.of(2),     // level 2 -> amplifier 1 (Poison II)
                                    Value.of(4),     // seconds
                                    Value.of(true),  // spawn particles
                                    Value.of(true),  // show icon
                                    EntityCondition.ALWAYS),
                            Value.of(1.0),           // chance
                            Value.of(5))))));        // cooldown, seconds
        } catch (Throwable t) {
            // An Artifacts API mismatch must degrade the band to a plain trophy,
            // never kill the pack.
            OreSpawnIntegrations.LOGGER.error(
                    "Emperor's Chitin Band ability attachment failed - it stays a plain item", t);
        }
    }

    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(EMPERORS_CHITIN_BAND);
        }
    }
}
