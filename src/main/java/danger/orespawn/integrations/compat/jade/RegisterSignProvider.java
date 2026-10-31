package danger.orespawn.integrations.compat.jade;

import java.util.Map;
import java.util.UUID;

import danger.orespawn.integrations.OreSpawnIntegrations;
import danger.orespawn.integrations.story.shelf9.HatchingGroundData;
import danger.orespawn.integrations.story.shelf9.RegisterLayout;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * The Hatch Register at Shelf 9 (structure-hatching-ground): each sign row carries a name alone, and Jade over the sign
 * adds every row's origin and day, as "{player} · {origin} · day {day}". Origin and day live in Shelf 9's SavedData
 * ({@link HatchingGroundData}), so the server sends the looked-at sign's rows; the client names the origin with Neo
 * Origins' own key ({@code origins.<namespace>.<path>.name}).
 */
enum RegisterSignProvider implements IComponentProvider<BlockAccessor>, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final String TAG_ROWS = "orespawn_integrations_register";

    private static boolean warned = false;

    @Override
    public ResourceLocation getUid() {
        return OreSpawnJadePlugin.UID_REGISTER;
    }

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        try {
            if (!(accessor.getLevel() instanceof ServerLevel level)) {
                return;
            }
            HatchingGroundData data = HatchingGroundData.get(level.getServer());
            int sign = data.registerSigns.indexOf(accessor.getPosition());
            if (sign < 0 || !data.dimension.equals(level.dimension().location().toString())) {
                return;
            }
            ListTag rows = new ListTag();
            for (Map.Entry<UUID, Integer> line : data.lines.entrySet()) {
                if (RegisterLayout.sign(line.getValue()) != sign) {
                    continue;
                }
                CompoundTag row = new CompoundTag();
                row.putInt("r", RegisterLayout.row(line.getValue()));
                row.putString("n", data.names.getOrDefault(line.getKey(), ""));
                row.putString("o", data.origins.getOrDefault(line.getKey(), ""));
                row.putInt("d", data.days.getOrDefault(line.getKey(), 0));
                rows.add(row);
            }
            tag.put(TAG_ROWS, rows);
        } catch (Throwable t) {
            warn(t);
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        try {
            ListTag rows = accessor.getServerData().getList(TAG_ROWS, Tag.TAG_COMPOUND);
            Component[] lines = new Component[RegisterLayout.ROWS];
            for (int i = 0; i < rows.size(); i++) {
                CompoundTag row = rows.getCompound(i);
                int r = row.getInt("r");
                if (r >= 0 && r < lines.length) {
                    lines[r] = Component.translatable("orespawn_integrations.shelf9.register.jade", row.getString("n"),
                            origin(row.getString("o")), row.getInt("d")).withStyle(ChatFormatting.GRAY);
                }
            }
            for (Component line : lines) {
                if (line != null) {
                    tooltip.add(line);
                }
            }
        } catch (Throwable t) {
            warn(t);
        }
    }

    private static Component origin(String id) {
        ResourceLocation location = id.isEmpty() ? null : ResourceLocation.tryParse(id);
        if (location == null) {
            return Component.literal("?");
        }
        return Component.translatableWithFallback(
                "origins." + location.getNamespace() + "." + location.getPath() + ".name", location.getPath());
    }

    private static void warn(Throwable t) {
        if (!warned) {
            warned = true;
            OreSpawnIntegrations.LOGGER.error("Jade compat: Register line failed; suppressing further errors", t);
        }
    }
}
