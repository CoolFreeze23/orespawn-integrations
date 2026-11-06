package danger.orespawn.integrations.story.freguesia.jade;

import danger.orespawn.integrations.OreSpawnIntegrations;
import danger.orespawn.integrations.story.freguesia.StoneRecords;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * The stone's lines, pt-BR or EN as the client reads them: the Freguesia's name; "Manor standing: 212. Next step:
 * Parish, 300."; "Days at home: 9 of 14." while the next stage counts days; and, when standing and days are met and
 * only a key is missing, the stage's key line (the Parish line without the Show prize until 0.14.0).
 */
enum MarcoProvider implements IComponentProvider<BlockAccessor>, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(OreSpawnIntegrations.MODID, "freguesia");
    private static final String TAG = "orespawn_integrations_freguesia";
    private static final String K = "orespawn_integrations.freguesia.";
    private static final String[] STAGES = {"house", "homestead", "hamlet", "parish", "town"};
    private static boolean warned;

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        try {
            if (accessor.getLevel() instanceof ServerLevel level) {
                CompoundTag view = StoneRecords.jadeView(level, accessor.getPosition());
                if (view != null) {
                    tag.put(TAG, view);
                }
            }
        } catch (Throwable t) {
            warn(t);
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        try {
            if (!accessor.getServerData().contains(TAG)) {
                return;
            }
            CompoundTag v = accessor.getServerData().getCompound(TAG);
            String arg = v.getString("na");
            Component name = switch (v.getInt("nk")) {
                case 2 -> Component.literal(arg);
                case 1 -> Component.translatable(K + "name.epithet", Component.translatable(arg));
                default -> Component.translatable(K + "name.default", arg);
            };
            tooltip.add(name.copy().withStyle(ChatFormatting.GOLD));
            int next = v.getInt("next");
            if (next < 1 || next >= STAGES.length) {
                return;
            }
            tooltip.add(Component.translatable(K + "jade.standing", v.getInt("standing"),
                    Component.translatable(K + "stage." + STAGES[next]), v.getInt("threshold"))
                    .withStyle(ChatFormatting.GRAY));
            if (v.getInt("days_needed") > 0) {
                tooltip.add(Component.translatable(K + "jade.days", Math.min(v.getInt("days"), v.getInt("days_needed")),
                        v.getInt("days_needed")).withStyle(ChatFormatting.GRAY));
            }
            int key = v.getInt("key");
            if (key == 3) {
                tooltip.add(Component.translatable(K + "jade.key_parish_base").withStyle(ChatFormatting.YELLOW));
            } else if (key == 4) {
                tooltip.add(Component.translatable(K + "jade.key_town").withStyle(ChatFormatting.YELLOW));
            }
        } catch (Throwable t) {
            warn(t);
        }
    }

    private static void warn(Throwable t) {
        if (!warned) {
            warned = true;
            OreSpawnIntegrations.LOGGER.error("Jade compat: Parish Stone line failed; suppressing further errors", t);
        }
    }
}
