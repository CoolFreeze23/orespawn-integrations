package danger.orespawn.integrations.story.chaos;

import danger.orespawn.integrations.OreSpawnIntegrations;
import danger.orespawn.integrations.config.IntegrationsConfig;
import danger.orespawn.integrations.story.Ranks;
import danger.orespawn.integrations.story.StoryCore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Spawner;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Set;

/**
 * The royal seal (item-the-larder, the rules printed on system-material-ladder): a spawn egg in
 * {@code #orespawn_integrations:royal_seal_eggs} opens for a Knight, or on a stage, and never on a spawner.
 * <ul>
 *   <li><b>Key A, the rank.</b> Knight, read through {@link Ranks} (in 0.10.0 the Apotheosis {@code ascent} holder).</li>
 *   <li><b>Key B, a stage</b> ({@code #orespawn_integrations:royal_stages}: the Mobzilla Arena, the Kraken Lair, the
 *       royal altars, the Challenge Towers), at any rank, except the King's and the Queen's eggs, which open there from
 *       Squire.</li>
 *   <li><b>The strict two</b>, the Ender Dragon and Wither eggs: Knight only, never a stage.</li>
 *   <li>On any spawner (a vanilla or trial spawner block entity), always refused. Operators bypass.</li>
 * </ul>
 * The egg is refused before {@code SpawnEggItem} acts: on a block, in the air (a fluid), on a mob, and from a
 * dispenser, whose behaviour for each sealed egg is wrapped once the tags load (a dispenser has no rank, so it opens a
 * sealed egg only on a stage). Whatever a sealed egg hatches is tagged {@code cc_ovo}: a hatched boss, not a won one.
 */
public final class RoyalSeal {

    public static final TagKey<Item> SEALED = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(OreSpawnIntegrations.MODID, "royal_seal_eggs"));
    public static final TagKey<Structure> STAGES = TagKey.create(Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath(OreSpawnIntegrations.MODID, "royal_stages"));
    public static final String HATCHED_TAG = "cc_ovo";
    static final Set<ResourceLocation> STRICT = Set.of(
            ResourceLocation.withDefaultNamespace("ender_dragon_spawn_egg"),
            ResourceLocation.withDefaultNamespace("wither_spawn_egg"));
    static final Set<ResourceLocation> CROWNED = Set.of(
            ResourceLocation.fromNamespaceAndPath("orespawn", "the_king_spawn_egg"),
            ResourceLocation.fromNamespaceAndPath("orespawn", "the_queen_spawn_egg"));

    /** What the seal says to one use. */
    public enum Verdict {
        OPEN(null),
        RANK("orespawn_integrations.royal_seal.refused"),
        STAGE_SQUIRE("orespawn_integrations.royal_seal.stage_squire"),
        SPAWNER("orespawn_integrations.royal_seal.spawner");

        public final String line;

        Verdict(String line) {
            this.line = line;
        }
    }

    /** The hatch watch: a sealed egg just opened here, this tick, so whatever joins beside it is tagged cc_ovo. */
    private static ServerLevel watchLevel;
    private static Vec3 watchPos;
    private static long watchTick = -1;

    private RoyalSeal() {
    }

    public static void init(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, RoyalSeal::onUseOnBlock);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, RoyalSeal::onUseItem);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, RoyalSeal::onUseOnEntity);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, RoyalSeal::onUseOnEntitySpecific);
        NeoForge.EVENT_BUS.addListener(RoyalSeal::onTagsUpdated);
        NeoForge.EVENT_BUS.addListener(RoyalSeal::onJoin);
        NeoForge.EVENT_BUS.addListener(RoyalSeal::onServerTick);
        NeoForge.EVENT_BUS.addListener(RoyalSeal::onTooltip);
    }

    // --- the rule ---

    /**
     * The seal's rule, pure: {@code knight} and {@code squire} are the user's keys (a dispenser has neither).
     */
    public static Verdict rule(ResourceLocation egg, boolean spawner, boolean stage, boolean knight, boolean squire) {
        if (spawner) {
            return Verdict.SPAWNER;
        }
        if (knight) {
            return Verdict.OPEN;
        }
        if (STRICT.contains(egg) || !stage) {
            return Verdict.RANK;
        }
        if (CROWNED.contains(egg) && !squire) {
            return Verdict.STAGE_SQUIRE;
        }
        return Verdict.OPEN;
    }

    /** The seal on one use of {@code stack} at {@code pos}; {@code user} is null for a dispenser. */
    public static Verdict judge(ServerLevel level, BlockPos pos, ItemStack stack, ServerPlayer user) {
        if (!IntegrationsConfig.on(IntegrationsConfig.storyRoyalSeal) || !stack.is(SEALED)) {
            return Verdict.OPEN;
        }
        if (user != null && user.hasPermissions(2)) {
            return Verdict.OPEN;
        }
        boolean spawner = level.getBlockEntity(pos) instanceof Spawner;
        boolean stage = onStage(level, pos);
        boolean knight = user != null && Ranks.atLeast(user, Ranks.KNIGHT);
        boolean squire = user != null && Ranks.atLeast(user, Ranks.SQUIRE);
        return rule(BuiltInRegistries.ITEM.getKey(stack.getItem()), spawner, stage, knight, squire);
    }

    public static boolean onStage(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos) && level.structureManager().getStructureWithPieceAt(pos, STAGES).isValid();
    }

    // --- the four hooks ---

    private static void onUseOnBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getLevel() instanceof ServerLevel level) {
            handle(event, player, level, event.getPos(), event.getItemStack());
        }
    }

    private static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getLevel() instanceof ServerLevel level) {
            handle(event, player, level, player.blockPosition(), event.getItemStack());
        }
    }

    private static void onUseOnEntity(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getLevel() instanceof ServerLevel level) {
            handle(event, player, level, event.getTarget().blockPosition(), event.getItemStack());
        }
    }

    private static void onUseOnEntitySpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getLevel() instanceof ServerLevel level) {
            handle(event, player, level, event.getTarget().blockPosition(), event.getItemStack());
        }
    }

    private static void handle(PlayerInteractEvent event, ServerPlayer player, ServerLevel level, BlockPos pos,
            ItemStack stack) {
        if (!stack.is(SEALED)) {
            return;
        }
        try {
            Verdict verdict = judge(level, pos, stack, player);
            if (verdict == Verdict.OPEN) {
                watch(level, Vec3.atCenterOf(pos));
                return;
            }
            if (event instanceof net.neoforged.bus.api.ICancellableEvent cancellable) {
                cancellable.setCanceled(true);
            }
            if (event instanceof PlayerInteractEvent.RightClickBlock click) {
                click.setCancellationResult(InteractionResult.FAIL);
            } else if (event instanceof PlayerInteractEvent.RightClickItem use) {
                use.setCancellationResult(InteractionResult.FAIL);
            } else if (event instanceof PlayerInteractEvent.EntityInteract interact) {
                interact.setCancellationResult(InteractionResult.FAIL);
            } else if (event instanceof PlayerInteractEvent.EntityInteractSpecific interact) {
                interact.setCancellationResult(InteractionResult.FAIL);
            }
            player.displayClientMessage(Component.translatable(verdict.line), true);
        } catch (Throwable t) {
            StoryCore.logOnce("royal_seal.use", t);
        }
    }

    // --- the dispenser ---

    private static void onTagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() != TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) {
            return;
        }
        try {
            for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(SEALED)) {
                Item item = holder.value();
                DispenseItemBehavior current = DispenserBlock.DISPENSER_REGISTRY.get(item);
                if (!(current instanceof SealedDispense)) {
                    DispenserBlock.registerBehavior(item, new SealedDispense(current));
                }
            }
        } catch (Throwable t) {
            StoryCore.logOnce("royal_seal.dispenser", t);
        }
    }

    /** A sealed egg's dispenser behaviour: refused unless the dispenser faces a stage; then the egg's own behaviour. */
    public record SealedDispense(DispenseItemBehavior wrapped) implements DispenseItemBehavior {
        @Override
        public ItemStack dispense(BlockSource source, ItemStack stack) {
            Direction facing = source.state().getValue(DispenserBlock.FACING);
            BlockPos target = source.pos().relative(facing);
            if (judge(source.level(), target, stack, null) != Verdict.OPEN) {
                source.level().levelEvent(1001, source.pos(), 0);
                return stack;
            }
            watch(source.level(), Vec3.atCenterOf(target));
            return wrapped.dispense(source, stack);
        }
    }

    // --- cc_ovo: hatched, not won ---

    private static void watch(ServerLevel level, Vec3 pos) {
        watchLevel = level;
        watchPos = pos;
        watchTick = level.getGameTime();
    }

    private static void onJoin(EntityJoinLevelEvent event) {
        if (watchLevel == null || event.getLevel() != watchLevel || event.loadedFromDisk()) {
            return;
        }
        Entity entity = event.getEntity();
        if (entity instanceof LivingEntity && !(entity instanceof Player) && watchLevel.getGameTime() == watchTick
                && entity.position().distanceToSqr(watchPos) <= 16 * 16) {
            entity.addTag(HATCHED_TAG);
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        watchLevel = null;
        watchPos = null;
    }

    // --- the egg's tooltip teaches the keys ---

    private static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!stack.is(SEALED)) {
            return;
        }
        boolean strict = STRICT.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        event.getToolTip().add(Component.translatable(strict
                ? "orespawn_integrations.royal_seal.tooltip_strict"
                : "orespawn_integrations.royal_seal.tooltip").withStyle(net.minecraft.ChatFormatting.GOLD));
    }
}
