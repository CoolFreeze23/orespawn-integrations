package danger.orespawn.integrations.story.freguesia.jade;

import danger.orespawn.integrations.OreSpawnIntegrations;
import danger.orespawn.integrations.story.freguesia.MarcoBlock;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade on the Parish Stone (system-freguesia): its name, Manor standing and the next step, the stone days, and what
 * key is missing. The stone has no block entity, so the server sends the looked-at stone's numbers from the record.
 * Discovered by Jade's own annotation scan, so nothing references this class when Jade is absent.
 */
@WailaPlugin(OreSpawnIntegrations.MODID)
public class FreguesiaJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        try {
            registration.registerBlockDataProvider(MarcoProvider.INSTANCE, MarcoBlock.class);
        } catch (Throwable t) {
            OreSpawnIntegrations.LOGGER.error("Jade compat: Parish Stone server data failed to register", t);
        }
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        try {
            registration.registerBlockComponent(MarcoProvider.INSTANCE, MarcoBlock.class);
        } catch (Throwable t) {
            OreSpawnIntegrations.LOGGER.error("Jade compat: Parish Stone lines failed to register", t);
        }
    }
}
