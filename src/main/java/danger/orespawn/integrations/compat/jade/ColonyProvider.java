package danger.orespawn.integrations.compat.jade;

import java.util.Map;

import danger.orespawn.integrations.OreSpawnIntegrations;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * The court's post (item-court-post-and-doors): each anthill colony carries the road to one post province, and Jade
 * over the colony block names it. Black ants and termites have lines of their own; the other three share the pattern
 * with their colony and province names. Registered on OreSpawn's {@code CrystalAntBlock}, which backs all six ant
 * blocks; the crystal termite block is no post office and stays silent.
 */
enum ColonyProvider implements IComponentProvider<BlockAccessor> {
    INSTANCE;

    private static final String KEY = "orespawn_integrations.colony.";

    private static final Map<String, Component> LINES = Map.of(
            "ant_block", line(Component.translatable(KEY + "black_ant")),
            "termite_block", line(Component.translatable(KEY + "termite")),
            "red_ant_block", pattern("red_ant"),
            "rainbow_ant_block", pattern("rainbow_ant"),
            "unstable_ant_block", pattern("unstable_ant"));

    private static boolean warned = false;

    private static Component pattern(String colony) {
        return line(Component.translatable(KEY + "pattern", Component.translatable(KEY + "name." + colony),
                Component.translatable(KEY + "province." + colony)));
    }

    private static Component line(Component text) {
        return text.copy().withStyle(ChatFormatting.GOLD);
    }

    @Override
    public ResourceLocation getUid() {
        return OreSpawnJadePlugin.UID_COLONY;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        try {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(accessor.getBlock());
            if (OreSpawnJadePlugin.ORESPAWN.equals(id.getNamespace())) {
                Component line = LINES.get(id.getPath());
                if (line != null) {
                    tooltip.add(line);
                }
            }
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                OreSpawnIntegrations.LOGGER.error("Jade compat: colony line failed; suppressing further errors", t);
            }
        }
    }
}
