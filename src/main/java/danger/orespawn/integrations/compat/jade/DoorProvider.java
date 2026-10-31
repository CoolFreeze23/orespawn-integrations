package danger.orespawn.integrations.compat.jade;

import danger.orespawn.integrations.OreSpawnIntegrations;
import danger.orespawn.integrations.config.IntegrationsConfig;
import danger.orespawn.integrations.story.Ranks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * The butterfly door (structure-chaos-antechamber): Jade over an OreSpawn butterfly or luna moth reads by the looker's
 * rank, "door: Antechamber" below Squire, "door: open Chaos (7) · sneaking: your ledge" from Squire. The rank is the
 * server's (an advancement in 0.10.0), so it is synced as one boolean.
 */
enum DoorProvider implements IComponentProvider<EntityAccessor>, IServerDataProvider<EntityAccessor> {
    INSTANCE;

    private static final String TAG_SQUIRE = "orespawn_integrations_squire";
    private static final Component DOOR = Component.translatable("orespawn_integrations.antechamber.jade.door")
            .withStyle(ChatFormatting.LIGHT_PURPLE);
    private static final Component DOOR_RANKED = Component
            .translatable("orespawn_integrations.antechamber.jade.door_ranked").withStyle(ChatFormatting.LIGHT_PURPLE);

    private static boolean warned = false;

    @Override
    public ResourceLocation getUid() {
        return OreSpawnJadePlugin.UID_DOOR;
    }

    private static boolean isDoor(EntityAccessor accessor) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(accessor.getEntity().getType());
        return OreSpawnJadePlugin.ORESPAWN.equals(id.getNamespace())
                && (id.getPath().equals("butterfly") || id.getPath().equals("luna_moth"));
    }

    @Override
    public void appendServerData(CompoundTag tag, EntityAccessor accessor) {
        try {
            if (isDoor(accessor) && accessor.getPlayer() instanceof ServerPlayer player) {
                tag.putBoolean(TAG_SQUIRE, Ranks.atLeast(player, Ranks.SQUIRE));
            }
        } catch (Throwable t) {
            warn(t);
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        try {
            if (isDoor(accessor) && IntegrationsConfig.on(IntegrationsConfig.storyAntechamber)) {
                tooltip.add(accessor.getServerData().getBoolean(TAG_SQUIRE) ? DOOR_RANKED : DOOR);
            }
        } catch (Throwable t) {
            warn(t);
        }
    }

    private static void warn(Throwable t) {
        if (!warned) {
            warned = true;
            OreSpawnIntegrations.LOGGER.error("Jade compat: butterfly door line failed; suppressing further errors", t);
        }
    }
}
