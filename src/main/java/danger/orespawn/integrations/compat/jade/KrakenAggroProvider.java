package danger.orespawn.integrations.compat.jade;

import danger.orespawn.entity.Kraken;
import danger.orespawn.integrations.OreSpawnIntegrations;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Server-synced "the Kraken is after you" line.
 *
 * <p>Note on the revenge mechanic: {@code danger.orespawn.KrakenRevengeHandler}
 * was inspected via {@code javap -c} and holds no queryable state - it is a
 * single {@code LivingDeathEvent} listener (plus static config constants) that
 * rolls a die when an Attack Squid dies in the Crystal dimension, spawns a
 * revenge Kraken near the killer and forgets about it. There is nothing to
 * reflect or read back. The revenge state that actually persists lives on the
 * spawned Kraken itself as its attack target, so this provider syncs
 * {@code Mob#getTarget() == viewer} - which the client can never know on its
 * own - and the tooltip shows the warning only while the Kraken is genuinely
 * hunting the player looking at it.
 */
enum KrakenAggroProvider implements IComponentProvider<EntityAccessor>, IServerDataProvider<EntityAccessor> {
    INSTANCE;

    private static final String TAG_AGGRO = "orespawn_kraken_aggro";

    private static final Component REVENGE = Component
            .translatableWithFallback("tooltip.orespawn_integrations.jade.kraken.revenge",
                    "Vengeful — this Kraken is hunting you!")
            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC);

    private static boolean warnedServer = false;
    private static boolean warnedClient = false;

    @Override
    public ResourceLocation getUid() {
        return OreSpawnJadePlugin.UID_KRAKEN_AGGRO;
    }

    @Override
    public void appendServerData(CompoundTag tag, EntityAccessor accessor) {
        try {
            if (accessor.getEntity() instanceof Kraken kraken) {
                tag.putBoolean(TAG_AGGRO,
                        kraken.getTarget() != null && kraken.getTarget() == accessor.getPlayer());
            }
        } catch (Throwable t) {
            if (!warnedServer) {
                warnedServer = true;
                OreSpawnIntegrations.LOGGER.error(
                        "Jade compat: Kraken aggro sync failed; suppressing further errors", t);
            }
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        try {
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(accessor.getEntity().getType());
            if (!OreSpawnJadePlugin.ORESPAWN.equals(id.getNamespace())) {
                return;
            }
            if (!config.get(OreSpawnJadePlugin.CFG_BOSS_INFO)) {
                return;
            }
            if (accessor.getServerData().getBoolean(TAG_AGGRO)) {
                tooltip.add(REVENGE);
            }
        } catch (Throwable t) {
            if (!warnedClient) {
                warnedClient = true;
                OreSpawnIntegrations.LOGGER.error(
                        "Jade compat: Kraken aggro tooltip failed; suppressing further errors", t);
            }
        }
    }
}
