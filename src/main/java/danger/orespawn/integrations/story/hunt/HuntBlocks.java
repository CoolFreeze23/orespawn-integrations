package danger.orespawn.integrations.story.hunt;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import danger.orespawn.integrations.OreSpawnIntegrations;
import danger.orespawn.integrations.config.IntegrationsConfig;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * The Royal Hunt's registrations: the Wanted poster block and its item, and the {@code hunt_switch} data condition
 * that keeps the Lodge's structure set (and other hunt data) only while its switch is on.
 */
public final class HuntBlocks {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(OreSpawnIntegrations.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(OreSpawnIntegrations.MODID);
    private static final DeferredRegister<MapCodec<? extends ICondition>> CONDITIONS =
            DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, OreSpawnIntegrations.MODID);

    public static final DeferredBlock<WantedPosterBlock> WANTED_POSTER = BLOCKS.register("wanted_poster",
            () -> new WantedPosterBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(0.5F)
                    .sound(SoundType.WOOD).noOcclusion().noCollission()));
    public static final DeferredItem<BlockItem> WANTED_POSTER_ITEM = ITEMS.registerSimpleBlockItem(WANTED_POSTER);

    static {
        CONDITIONS.register("hunt_switch", () -> Switch.CODEC);
    }

    private HuntBlocks() {
    }

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        CONDITIONS.register(modBus);
    }

    /**
     * {@code {"type":"orespawn_integrations:hunt_switch","switch":"hunt_lodge"}}: keeps a data file only while the named
     * switch is on (hunt_lodge, boss_ladder, royal_hunt, court_ranks). Fails open, like the other OI conditions.
     */
    public record Switch(String name) implements ICondition {
        public static final MapCodec<Switch> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("switch").forGetter(Switch::name)).apply(i, Switch::new));

        @Override
        public boolean test(IContext context) {
            if (!IntegrationsConfig.SPEC.isLoaded()) {
                return true;
            }
            return switch (name) {
                case "hunt_lodge" -> IntegrationsConfig.storyHuntLodge.getAsBoolean();
                case "boss_ladder" -> IntegrationsConfig.storyBossLadder.getAsBoolean();
                case "royal_hunt" -> IntegrationsConfig.storyRoyalHunt.getAsBoolean();
                case "court_ranks" -> IntegrationsConfig.storyCourtRanks.getAsBoolean();
                default -> true;
            };
        }

        @Override
        public MapCodec<? extends ICondition> codec() {
            return CODEC;
        }
    }
}
