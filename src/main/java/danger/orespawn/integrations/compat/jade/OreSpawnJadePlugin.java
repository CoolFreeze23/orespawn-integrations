package danger.orespawn.integrations.compat.jade;

import danger.orespawn.block.BossSpawnerBlock;
import danger.orespawn.block.OreGenericEgg;
import danger.orespawn.entity.Kraken;
import danger.orespawn.integrations.OreSpawnIntegrations;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade integration for OreSpawn. Discovered by Jade's own annotation scan, so
 * nothing references this class when Jade is absent; the {@code "orespawn"}
 * annotation value additionally tells Jade to skip instantiation unless
 * OreSpawn itself is loaded (this class compiles against
 * {@code danger.orespawn.*} directly).
 *
 * <p>Provides: a danger-tier + signature-drop line for every OreSpawn mob, a
 * server-synced Kraken revenge-aggro line, and boss/activation info on the
 * boss-summoning blocks.
 */
@WailaPlugin("orespawn")
public class OreSpawnJadePlugin implements IWailaPlugin {

    /** Namespace gate used by every provider before doing any other work. */
    static final String ORESPAWN = "orespawn";

    static final ResourceLocation UID_DANGER_TIER =
            ResourceLocation.fromNamespaceAndPath(OreSpawnIntegrations.MODID, "danger_tier");
    static final ResourceLocation UID_KRAKEN_AGGRO =
            ResourceLocation.fromNamespaceAndPath(OreSpawnIntegrations.MODID, "kraken_aggro");
    static final ResourceLocation UID_BOSS_SPAWNER =
            ResourceLocation.fromNamespaceAndPath(OreSpawnIntegrations.MODID, "boss_spawner");

    /** Extra options shown in Jade's plugin config screen (both default on). */
    static final ResourceLocation CFG_BOSS_INFO =
            ResourceLocation.fromNamespaceAndPath(OreSpawnIntegrations.MODID, "boss_info");
    static final ResourceLocation CFG_DROP_HINTS =
            ResourceLocation.fromNamespaceAndPath(OreSpawnIntegrations.MODID, "drop_hints");

    @Override
    public void register(IWailaCommonRegistration registration) {
        // Server side: sync Kraken aggro. Registered per-class so the provider
        // never runs for unrelated entities.
        try {
            registration.registerEntityDataProvider(KrakenAggroProvider.INSTANCE, Kraken.class);
        } catch (Throwable t) {
            OreSpawnIntegrations.LOGGER.error(
                    "Jade compat: Kraken server-data provider failed to register - continuing without it", t);
        }
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        try {
            registration.addConfig(CFG_BOSS_INFO, true);
            registration.addConfig(CFG_DROP_HINTS, true);
        } catch (Throwable t) {
            OreSpawnIntegrations.LOGGER.error(
                    "Jade compat: config toggles failed to register - providers fall back to enabled", t);
        }
        try {
            registration.registerEntityComponent(DangerTierProvider.INSTANCE, LivingEntity.class);
        } catch (Throwable t) {
            OreSpawnIntegrations.LOGGER.error(
                    "Jade compat: danger-tier provider failed to register - continuing without it", t);
        }
        try {
            registration.registerEntityComponent(KrakenAggroProvider.INSTANCE, Kraken.class);
        } catch (Throwable t) {
            OreSpawnIntegrations.LOGGER.error(
                    "Jade compat: Kraken aggro display failed to register - continuing without it", t);
        }
        try {
            // BossSpawnerBlock backs king_spawner / queen_spawner / dungeon_spawner
            // (verified in ModBlocks bytecode); godzilla_spawn_block is an
            // OreGenericEgg like all other *_spawn_block decor, so the provider
            // id-filters and stays silent on the other ~120 egg blocks.
            registration.registerBlockComponent(BossSpawnerProvider.INSTANCE, BossSpawnerBlock.class);
            registration.registerBlockComponent(BossSpawnerProvider.INSTANCE, OreGenericEgg.class);
        } catch (Throwable t) {
            OreSpawnIntegrations.LOGGER.error(
                    "Jade compat: boss-spawner provider failed to register - continuing without it", t);
        }
    }
}
