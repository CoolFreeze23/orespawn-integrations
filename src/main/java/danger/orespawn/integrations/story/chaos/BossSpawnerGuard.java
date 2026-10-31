package danger.orespawn.integrations.story.chaos;

import danger.orespawn.integrations.OreSpawnIntegrations;
import danger.orespawn.integrations.config.IntegrationsConfig;
import danger.orespawn.integrations.story.StoryCore;
import dev.shadowsoffire.apothic_spawners.block.ApothSpawnerTile;
import dev.shadowsoffire.apothic_spawners.modifiers.SpawnerModifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.Optional;

/**
 * A boss's spawner stays where it stands (system-lancamentos-em-duplicidade, inside npc-mobzilla's boss tap): on a
 * {@code minecraft:spawner} whose SpawnData entity is in {@code #c:bosses} or {@code #orespawn_integrations:heavyweights}
 * (the Alien Boss's dungeon, the Basilisk Maze, the Kyuubi and Shadow dungeons), a Silk Touch break is cancelled and
 * every Apothic Spawners modifier item is refused, so nobody moves one home and speeds it up. It keeps spawning where
 * it is, and breaking it without Silk Touch writes it off as usual. Apothic Spawners replaces the vanilla spawner's
 * block entity with {@code ApothSpawnerTile}, and {@code SpawnerModifier.findMatch} is the same lookup its own
 * {@code useItemOn} makes, so exactly the items Apothic would apply are refused. Switch: {@code bigame.boss_cage_refusal}
 * (the same boss tap).
 */
public final class BossSpawnerGuard {

    public static final TagKey<EntityType<?>> BOSSES = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath("c", "bosses"));
    public static final TagKey<EntityType<?>> HEAVYWEIGHTS = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(OreSpawnIntegrations.MODID, "heavyweights"));
    static final String LINE = "orespawn_integrations.duplicates.boss_refused";

    private BossSpawnerGuard() {
    }

    public static void init(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, BossSpawnerGuard::onBreak);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, BossSpawnerGuard::onUse);
    }

    /** The entity a spawner at {@code pos} spawns, if it is a boss or a heavyweight. */
    public static boolean guardsBoss(LevelAccessor level, BlockPos pos) {
        if (!IntegrationsConfig.on(IntegrationsConfig.bigameBossCageRefusal) || !level.getBlockState(pos).is(Blocks.SPAWNER)) {
            return false;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return false;
        }
        CompoundTag data = be.saveWithoutMetadata(level.registryAccess());
        String id = data.getCompound("SpawnData").getCompound("entity").getString("id");
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null) {
            return false;
        }
        Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(key);
        return type.isPresent() && (type.get().is(BOSSES) || type.get().is(HEAVYWEIGHTS));
    }

    private static void onBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.isCreative() || !event.getState().is(Blocks.SPAWNER)) {
            return;
        }
        try {
            ItemStack tool = player.getMainHandItem();
            var silk = player.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                    .getHolder(Enchantments.SILK_TOUCH);
            if (silk.isEmpty() || EnchantmentHelper.getItemEnchantmentLevel(silk.get(), tool) <= 0) {
                return;
            }
            if (guardsBoss(event.getLevel(), event.getPos())) {
                event.setCanceled(true);
                tell(player);
            }
        } catch (Throwable t) {
            StoryCore.logOnce("boss_spawner.break", t);
        }
    }

    private static void onUse(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (!(level.getBlockEntity(event.getPos()) instanceof ApothSpawnerTile tile)) {
            return;
        }
        try {
            Player player = event.getEntity();
            if (SpawnerModifier.findMatch(tile, player.getMainHandItem(), player.getOffhandItem()) == null) {
                return;
            }
            if (guardsBoss(level, event.getPos())) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                tell(player);
            }
        } catch (Throwable t) {
            StoryCore.logOnce("boss_spawner.modifier", t);
        }
    }

    private static void tell(Player player) {
        if (player instanceof ServerPlayer server) {
            server.displayClientMessage(Component.translatable(LINE), true);
        }
    }
}
