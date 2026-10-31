package danger.orespawn.integrations.compat.jade;

import danger.orespawn.integrations.OreSpawnIntegrations;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * The Larder's line (item-the-larder): Jade over a dried egg block adds its Larder class, from the four block tags
 * {@code #orespawn_integrations:larder/<farm|menagerie|quarry|royal>} written from the card's table of 119 egg
 * blocks. At most four tag checks per look.
 */
enum LarderProvider implements IComponentProvider<BlockAccessor> {
    INSTANCE;

    private static final String[] CLASSES = {"farm", "menagerie", "quarry", "royal"};
    private static final TagKey<Block>[] TAGS = tags();
    private static final Component[] LINES = new Component[CLASSES.length];

    static {
        for (int i = 0; i < CLASSES.length; i++) {
            LINES[i] = Component.translatable("orespawn_integrations.larder.jade",
                    Component.translatable("orespawn_integrations.larder.class." + CLASSES[i]))
                    .withStyle(ChatFormatting.GRAY);
        }
    }

    private static boolean warned = false;

    @SuppressWarnings("unchecked")
    private static TagKey<Block>[] tags() {
        TagKey<Block>[] out = new TagKey[CLASSES.length];
        for (int i = 0; i < CLASSES.length; i++) {
            out[i] = TagKey.create(Registries.BLOCK,
                    ResourceLocation.fromNamespaceAndPath(OreSpawnIntegrations.MODID, "larder/" + CLASSES[i]));
        }
        return out;
    }

    @Override
    public ResourceLocation getUid() {
        return OreSpawnJadePlugin.UID_LARDER;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        try {
            BlockState state = accessor.getBlockState();
            for (int i = 0; i < TAGS.length; i++) {
                if (state.is(TAGS[i])) {
                    tooltip.add(LINES[i]);
                    return;
                }
            }
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                OreSpawnIntegrations.LOGGER.error("Jade compat: Larder line failed; suppressing further errors", t);
            }
        }
    }
}
