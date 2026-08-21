package danger.orespawn.integrations.compat.journeymap.structures;

import danger.orespawn.integrations.OreSpawnIntegrations;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.common.JourneyMapPlugin;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * Second JourneyMap 6.x (api 2.0.0) client plugin of this mod: structure
 * markers. Discovered by JourneyMap's annotation scan like the waystone
 * bridge plugin, so it only ever classloads when JourneyMap is installed
 * and initializes on the client only.
 *
 * Why a separate plugin class instead of extending the existing one:
 * the structures feature is its own territory (own lifecycle, own overlay
 * ownership) and JourneyMap happily initializes any number of plugins as
 * long as their {@code getModId()} keys differ - PluginHelper (verified in
 * the 6.0.4 bytecode) stores client plugins in a map keyed by that string,
 * which is why this class reports {@link StructureMarkerManager#OWNER_ID}
 * ("orespawn_integrations_structures") rather than the real mod id used by
 * {@code OreSpawnJourneyMapPlugin}. The id is a pure ownership/namespacing
 * key; JourneyMap never resolves it against the mod list.
 *
 * All failures are swallowed: a broken map integration must never take the
 * pack down.
 */
@JourneyMapPlugin(apiVersion = "2.0.0")
public class OreSpawnStructuresPlugin implements IClientPlugin {

    @Override
    public String getModId() {
        return StructureMarkerManager.OWNER_ID;
    }

    @Override
    public void initialize(IClientAPI api) {
        try {
            if (FMLEnvironment.dist != Dist.CLIENT) {
                return;
            }
            // Listeners install even when the launch toggle is off so the
            // static boolean can be flipped on later; every hot path checks
            // the toggle itself.
            StructureMarkerManager.install(api);
            OreSpawnIntegrations.LOGGER.info(
                    "JourneyMap compat: structure markers active ({} tracked structures{})",
                    TrackedStructures.count(),
                    StructureMarkersConfig.isEnabled() ? "" : ", currently disabled via -D"
                            + StructureMarkersConfig.SYSTEM_PROPERTY);
        } catch (Throwable t) {
            OreSpawnIntegrations.LOGGER.error(
                    "JourneyMap structure markers failed to initialize - continuing without them", t);
        }
    }
}
