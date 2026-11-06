package danger.orespawn.integrations.compat.jade;

import danger.orespawn.integrations.OreSpawnIntegrations;
import danger.orespawn.integrations.story.hunt.WantedPosterBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * The Royal Hunt's and the Land's Jade lines (0.10.1), client side only, read from what the client already knows:
 * a Wanted poster names its row (and row 6's goat its caption); a Hunter's Workbench says the Hunt's board opens by a
 * sneak-use (npc-iracema); every member of {@code #orespawn_integrations:bichos_da_terra} is marked the Land's animal
 * (npc-mapinguari), and the Mapinguari himself is named Keeper of Sunday. One tag or id check per look.
 */
@WailaPlugin
public class HuntJadePlugin implements IWailaPlugin {

    static final ResourceLocation UID_POSTER = ResourceLocation.fromNamespaceAndPath(OreSpawnIntegrations.MODID, "wanted_poster");
    static final ResourceLocation UID_TABLE = ResourceLocation.fromNamespaceAndPath(OreSpawnIntegrations.MODID, "hunt_board");
    static final ResourceLocation UID_LAND = ResourceLocation.fromNamespaceAndPath(OreSpawnIntegrations.MODID, "bicho_da_terra");

    static final TagKey<EntityType<?>> BICHOS = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(OreSpawnIntegrations.MODID, "bichos_da_terra"));
    static final ResourceLocation TABLE = ResourceLocation.fromNamespaceAndPath("monster_hunter_villager", "hunters_table");
    static final ResourceLocation MAPINGUARI = ResourceLocation.fromNamespaceAndPath("brazil_legends", "mapinguari");

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        try {
            registration.registerBlockComponent(Poster.INSTANCE, WantedPosterBlock.class);
            registration.registerBlockComponent(Table.INSTANCE, Block.class);
            registration.registerEntityComponent(Land.INSTANCE, LivingEntity.class);
        } catch (Throwable t) {
            OreSpawnIntegrations.LOGGER.error("Jade compat: the Hunt's lines failed to register - continuing without them", t);
        }
    }

    enum Poster implements IComponentProvider<BlockAccessor> {
        INSTANCE;

        @Override
        public ResourceLocation getUid() {
            return UID_POSTER;
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            BlockState state = accessor.getBlockState();
            if (!state.hasProperty(WantedPosterBlock.ROW)) {
                return;
            }
            int row = state.getValue(WantedPosterBlock.ROW);
            tooltip.add(Component.translatable("orespawn_integrations.hunt.row." + row).withStyle(ChatFormatting.GOLD));
            if (row == 6) {
                tooltip.add(Component.translatable("orespawn_integrations.hunt.caption.craig").withStyle(ChatFormatting.GRAY));
            }
        }
    }

    enum Table implements IComponentProvider<BlockAccessor> {
        INSTANCE;

        @Override
        public ResourceLocation getUid() {
            return UID_TABLE;
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (TABLE.equals(BuiltInRegistries.BLOCK.getKey(accessor.getBlock()))) {
                tooltip.add(Component.translatable("orespawn_integrations.iracema.jade.table").withStyle(ChatFormatting.GRAY));
            }
        }
    }

    enum Land implements IComponentProvider<EntityAccessor> {
        INSTANCE;

        @Override
        public ResourceLocation getUid() {
            return UID_LAND;
        }

        @Override
        public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
            EntityType<?> type = accessor.getEntity().getType();
            if (type.is(BICHOS)) {
                tooltip.add(Component.translatable("orespawn_integrations.mapinguari.jade.bicho").withStyle(ChatFormatting.DARK_GREEN));
            } else if (MAPINGUARI.equals(BuiltInRegistries.ENTITY_TYPE.getKey(type))) {
                tooltip.add(Component.translatable("orespawn_integrations.mapinguari.jade.other").withStyle(ChatFormatting.DARK_GREEN));
            }
        }
    }
}
