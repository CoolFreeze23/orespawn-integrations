package danger.orespawn.integrations.compat.journeymap;

import danger.orespawn.integrations.OreSpawnIntegrations;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.common.JourneyMapPlugin;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * JourneyMap 6.x (api 2.0.0) client plugin. Discovered by JourneyMap's own
 * annotation scan, so this class is only ever loaded when JourneyMap is
 * installed and only on the client - no wiring in the main mod class.
 *
 * Its single job: hand the client API to {@link WaystoneWaypointBridge},
 * which colors dungeon-origin Waystones waypoints on the map. All failures
 * are swallowed here; a broken map integration must never take the pack down.
 *
 * Uses the v2/common annotation (the v2/client one is deprecated for
 * removal); JourneyMap 6.0.4's NeoForgeHooks scans for both.
 */
@JourneyMapPlugin(apiVersion = "2.0.0")
public class OreSpawnJourneyMapPlugin implements IClientPlugin {

    @Override
    public String getModId() {
        return OreSpawnIntegrations.MODID;
    }

    @Override
    public void initialize(IClientAPI api) {
        try {
            if (FMLEnvironment.dist != Dist.CLIENT) {
                return;
            }
            if (!ModList.get().isLoaded("waystones")) {
                OreSpawnIntegrations.LOGGER.info(
                        "JourneyMap compat: Waystones not installed, nothing to bridge");
                return;
            }
            // Guarded branch: WaystoneWaypointBridge references Waystones
            // classes and is only classloaded once the mod check above passed.
            WaystoneWaypointBridge.install(api);
            OreSpawnIntegrations.LOGGER.info("JourneyMap compat: waystone waypoint bridge active");
        } catch (Throwable t) {
            OreSpawnIntegrations.LOGGER.error(
                    "JourneyMap compat failed to initialize - continuing without it", t);
        }
    }
}
