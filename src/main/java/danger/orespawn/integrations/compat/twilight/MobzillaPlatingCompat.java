package danger.orespawn.integrations.compat.twilight;

import com.mojang.serialization.Codec;
import danger.orespawn.integrations.OreSpawnIntegrations;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Unit;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Mobzilla Plating (Thread 4 "Big Game") — marker-component registration backing
 * the data-driven Twilight Forest travellers-gear modifier at
 * {@code data/orespawn_integrations/twilight/travellers_modifiers/mobzilla_plating.json}
 * (+4 Armor, +0.3 knockback resistance on the Travellers Vest, crafted from four
 * Godzilla Scales).
 *
 * <p><b>Why this class exists</b> (verified against
 * twilightforest-1.21.1-4.8.3345-universal, javap bytecode): TF's
 * {@code twilightforest.item.travellers_gear.modifiers.TravellersEntryModifier} —
 * the {@code "type": "twilightforest:attribute"} modifier — has a REQUIRED codec
 * field {@code "component"} parsed with {@code DataComponentType.CODEC}
 * (registry-backed) and used as a {@code DataComponentType<Unit>} presence marker:
 * {@code addModifier} does {@code stack.set(markerComponent, Unit.INSTANCE)} and
 * {@code removeModifier} clears it. Every shipped TF attribute modifier points at a
 * TF-registered Unit component (e.g. {@code twilightforest:aquatic_agility}), so a
 * third-party modifier must supply its own registered Unit component — that is the
 * single registration below ({@code orespawn_integrations:mobzilla_plating}).
 *
 * <p>The builder chain mirrors TF's own Unit markers byte-for-byte
 * ({@code TFDataComponents}, javap):
 * {@code DataComponentType.builder().persistent(Codec.unit(Unit.INSTANCE))
 * .networkSynchronized(StreamCodec.unit(Unit.INSTANCE)).cacheEncoding().build()} —
 * persistent so the plating survives save/load on the vest, synced so the client
 * tooltip/JEI paths see it.
 *
 * <p><b>Zero hard deps (policy 1)</b>: this class touches only vanilla/NeoForge
 * API — no Twilight Forest classes — so it compiles and runs without TF on the
 * classpath. The modifier JSON and its
 * {@code twilightforest:travellers_gear_modifier_shaped_recipe} both carry
 * {@code neoforge:conditions} [twilightforest, orespawn, thread_enabled:big_game];
 * the datapack-registry loader evaluates them (RegistryDataLoader is
 * ConditionalOps-wrapped in NeoForge 21.1 — neoforge-21.1.223 sources line 207,
 * runtime 21.1.248, stable range). With TF absent the {@code twilight/travellers_modifiers}
 * registry simply never loads and this component sits unused.
 *
 * <p>Registered from the main mod class's guarded compat table like the other
 * modules; the guard is safe because the gated JSONs are the only consumers.
 */
public final class MobzillaPlatingCompat {

    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, OreSpawnIntegrations.MODID);

    /**
     * Unit marker TF stamps on a Travellers Vest that has the plating installed.
     * Referenced by name from the modifier JSON's required {@code "component"} field.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Unit>> MOBZILLA_PLATING =
            COMPONENTS.register("mobzilla_plating", () -> DataComponentType.<Unit>builder()
                    .persistent(Codec.unit(Unit.INSTANCE))
                    .networkSynchronized(StreamCodec.unit(Unit.INSTANCE))
                    .cacheEncoding()
                    .build());

    private MobzillaPlatingCompat() {}

    public static void init(IEventBus modBus) {
        COMPONENTS.register(modBus);
    }
}
