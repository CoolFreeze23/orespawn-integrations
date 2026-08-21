package danger.orespawn.integrations.config;

import com.mojang.serialization.MapCodec;
import danger.orespawn.integrations.OreSpawnIntegrations;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * POLICY 2 infrastructure entry point: registers the
 * {@code orespawn_integrations:thread_enabled} condition codec and the
 * {@link IntegrationsConfig} COMMON spec. Called once, UNCONDITIONALLY, from
 * the {@code OreSpawnIntegrations} constructor — this is infrastructure, not
 * compat, so it never sits behind a ModList guard.
 *
 * <p>Registration point (verification report Q3, verified against
 * neoforge-21.1.223): the condition-codec registry is
 * {@code NeoForgeRegistries.CONDITION_SERIALIZERS}
 * ({@code Registry<MapCodec<? extends ICondition>>},
 * NeoForgeRegistries.java:42), keyed by
 * {@code NeoForgeRegistries.Keys.CONDITION_CODECS}
 * ({@code "neoforge:condition_codecs"}, line 57). In-pack precedent:
 * artifacts-neoforge-13.2.1's {@code artifacts.neoforge.registry.ModConditions}
 * registers its {@code artifacts:config_value} condition through exactly this
 * DeferredRegister surface (bytecode-verified).
 *
 * <p>COMMON config type (not SERVER): it loads at mod construction, so the
 * toggles exist before the first datapack parse — zero ordering risk — and
 * thread gating is pack-level policy anyway.
 * {@code ModContainer.registerConfig(ModConfig.Type, IConfigSpec)} signature
 * confirmed via javap against fancymodloader loader-4.0.42 (the FML shipped
 * with NeoForge 21.1.223; pack runtime 21.1.248, stable across 21.1.x).
 */
public final class ConfigInit {

    private static final DeferredRegister<MapCodec<? extends ICondition>> CONDITION_CODECS =
            DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, OreSpawnIntegrations.MODID);

    static {
        CONDITION_CODECS.register("thread_enabled", () -> ThreadEnabledCondition.CODEC);
    }

    private ConfigInit() {}

    /**
     * @param modBus    the mod event bus (registry events)
     * @param container this mod's own container — obtained via FML constructor
     *                  injection in the {@code OreSpawnIntegrations} ctor
     *                  (extend it to {@code (IEventBus, ModContainer)}), NOT
     *                  via the ModLoadingContext ambient lookup, so the config
     *                  can never be registered against the wrong mod.
     */
    public static void init(IEventBus modBus, ModContainer container) {
        CONDITION_CODECS.register(modBus);
        container.registerConfig(ModConfig.Type.COMMON, IntegrationsConfig.SPEC);
        OreSpawnIntegrations.LOGGER.info(
                "Thread config registered - 5 thread toggles, condition '{}:thread_enabled' active",
                OreSpawnIntegrations.MODID);
    }
}
