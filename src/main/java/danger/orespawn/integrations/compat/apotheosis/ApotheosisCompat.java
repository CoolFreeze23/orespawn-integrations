package danger.orespawn.integrations.compat.apotheosis;

import danger.orespawn.integrations.OreSpawnIntegrations;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Entry point for the Apotheosis (8.7.0) Tier 3 integration: a dedicated
 * {@code orespawn_integrations:orespawn_gun} loot category for OreSpawn's
 * gun-like items (Ray Gun, Squid Zooka), so they can roll affixes like any
 * other Apotheosis weapon.
 *
 * <p>Wiring is deliberately different from the reflective COMPAT table in
 * {@link danger.orespawn.integrations.OreSpawnIntegrations}: loot categories
 * live in a NeoForge custom registry ({@code apotheosis:loot_category},
 * created by Placebo's {@code DeferredHelper.registry} through
 * {@code RegistryBuilder} / {@code NewRegistryEvent}), so entries must be
 * contributed during {@link RegisterEvent} on our own mod bus. This class is
 * discovered via {@link EventBusSubscriber} and needs no init() call.
 *
 * <p>Classloading safety: this outer class references no Apotheosis types.
 * A {@link RegisterEvent} for the {@code apotheosis:loot_category} registry
 * can only ever fire when Apotheosis is installed (the registry does not
 * exist otherwise), so the registry-key check below doubles as the
 * mod-presence guard; only then is {@link OreSpawnGunCategory} classloaded.
 */
@EventBusSubscriber(modid = OreSpawnIntegrations.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class ApotheosisCompat {

    /** Id of Apotheosis' loot category registry (verified against 8.7.0 bytecode). */
    private static final ResourceLocation LOOT_CATEGORY_REGISTRY_ID =
            ResourceLocation.fromNamespaceAndPath("apotheosis", "loot_category");

    private ApotheosisCompat() {
    }

    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        if (!event.getRegistryKey().location().equals(LOOT_CATEGORY_REGISTRY_ID)) {
            return;
        }
        try {
            OreSpawnGunCategory.register(event);
            OreSpawnIntegrations.LOGGER.info(
                    "Tier 3 compat active: apotheosis loot category 'orespawn_gun' registered.");
        } catch (Throwable t) {
            OreSpawnIntegrations.LOGGER.error(
                    "Apotheosis gun loot-category registration failed - continuing without it", t);
        }
    }
}
