package danger.orespawn.integrations.compat.alive;

import danger.orespawn.integrations.OreSpawnIntegrations;
import danger.orespawn.integrations.story.Ranks;
import danger.orespawn.integrations.story.StoryCore;
import danger.orespawn.integrations.story.firsthour.FirstHourUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;

/**
 * The vault (event-hoard-fall, "The vault's sampler" and "What a vault holds"): one {@code ironchest:crystal_chest}
 * filled by {@link #placeVault}, which is callable without the meteor (the King's Plate, Inside-Out Week). In order:
 * <ol>
 *   <li>one item from the Saci's stash first, when the stash ({@code orespawn_integrations:brecho}, list
 *       {@code stash}, from 0.18.0) is not empty, with its Second-hand line;</li>
 *   <li>one roll on the hallmark band table of the centre player's rank ({@code orespawn_integrations:hallmark/h<n>});
 *       until those tables ship (0.10.2) that roll is one more sampler draw;</li>
 *   <li>eight sampler draws, uniform over the item registry (blocks come as their items) minus
 *       {@code #orespawn_integrations:pinata_blacklist}, which holds {@code #orespawn_integrations:story_keys}, and
 *       minus every spawn egg and every creative, debug or admin item; the list is rebuilt when tags reload;</li>
 *   <li>one Inventory Pets pet ({@code orespawn_integrations:hoard_fall/pet});</li>
 *   <li>the Notice of Loss: a paper named in both languages, its six lines as lore, carrying the
 *       {@code orespawn_integrations:notice_of_loss} component.</li>
 * </ol>
 * The first vault a player opens grants R5 (none if the First Fall's label already did) and Wonders row 8, once.
 */
public final class HoardVault {

    static final String K = "event.orespawn_integrations.hoard_fall.";
    public static final TagKey<Item> PINATA_BLACKLIST = TagKey.create(Registries.ITEM, FirstHourUtil.oi("pinata_blacklist"));
    public static final TagKey<Item> STORY_KEYS = TagKey.create(Registries.ITEM, FirstHourUtil.oi("story_keys"));
    static final ResourceLocation STASH = FirstHourUtil.oi("brecho");
    private static final ResourceKey<LootTable> PET = ResourceKey.create(Registries.LOOT_TABLE, FirstHourUtil.oi("hoard_fall/pet"));

    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, OreSpawnIntegrations.MODID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Unit>> NOTICE_OF_LOSS =
            COMPONENTS.registerComponentType("notice_of_loss",
                    b -> b.persistent(Unit.CODEC).networkSynchronized(StreamCodec.unit(Unit.INSTANCE)));

    private static List<Item> sampler = List.of();

    private HoardVault() {
    }

    static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
    }

    // --- the sampler ---

    /** Rebuilds the sampler's list from the registry and the loaded tags (on every tag reload). */
    static void rebuild() {
        List<Item> items = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (eligible(item)) {
                items.add(item);
            }
        }
        sampler = List.copyOf(items);
    }

    /** True when a sampler may draw {@code item}. Public for the GameTests. */
    public static boolean eligible(Item item) {
        if (item == Items.AIR || item instanceof SpawnEggItem) {
            return false;
        }
        ItemStack probe = new ItemStack(item);
        if (probe.is(PINATA_BLACKLIST) || probe.is(STORY_KEYS)) {
            return false;
        }
        String path = BuiltInRegistries.ITEM.getKey(item).getPath();
        return !path.contains("creative") && !path.contains("debug") && !path.contains("admin");
    }

    /** One sampler draw: a single item, never a story key or a technical item. */
    public static ItemStack draw(RandomSource random) {
        List<Item> list = sampler;
        if (list.isEmpty()) {
            rebuild();
            list = sampler;
        }
        return list.isEmpty() ? ItemStack.EMPTY : new ItemStack(list.get(random.nextInt(list.size())));
    }

    // --- the vault ---

    /**
     * Places a filled vault at {@code pos} (a crystal chest, or a chest without Iron Chests) for {@code centre}, the
     * player the vault is scaled by (null: rank 0), and remembers it for the open handler. Returns the chest's spot.
     */
    public static BlockPos placeVault(ServerLevel level, BlockPos pos, ServerPlayer centre) {
        BlockState chest = AliveWorldCompat.blockState("ironchest", "crystal_chest");
        level.setBlockAndUpdate(pos, chest != null ? chest : Blocks.CHEST.defaultBlockState());
        if (level.getBlockEntity(pos) instanceof Container box) {
            List<ItemStack> items = contents(level, pos, centre);
            int slot = 0;
            for (ItemStack stack : items) {
                if (!stack.isEmpty() && slot < box.getContainerSize()) {
                    box.setItem(slot++, stack);
                }
            }
            box.setChanged();
        }
        HoardVaults.get(level.getServer()).add(level, pos);
        return pos;
    }

    /** What the vault holds, in the card's order. Public for the GameTests. */
    public static List<ItemStack> contents(ServerLevel level, BlockPos pos, ServerPlayer centre) {
        RandomSource random = level.random;
        List<ItemStack> items = new ArrayList<>(12);
        ItemStack secondHand = stash(level.getServer());
        if (!secondHand.isEmpty()) {
            items.add(secondHand);
        }
        items.addAll(bandRoll(level, pos, centre));
        for (int i = 0; i < HoardFallRules.SAMPLER_DRAWS; i++) {
            items.add(draw(random));
        }
        items.addAll(roll(level, pos, PET, centre));
        items.add(notice());
        return items;
    }

    private static List<ItemStack> bandRoll(ServerLevel level, BlockPos pos, ServerPlayer centre) {
        int rank = Ranks.HATCHLING;
        if (centre != null) {
            for (int r = Ranks.FIDALGO; r > Ranks.HATCHLING; r--) {
                if (Ranks.atLeast(centre, r)) {
                    rank = r;
                    break;
                }
            }
        }
        ResourceKey<LootTable> band = ResourceKey.create(Registries.LOOT_TABLE,
                FirstHourUtil.oi("hallmark/h" + HoardFallRules.band(rank)));
        if (!level.getServer().reloadableRegistries().getKeys(Registries.LOOT_TABLE).contains(band.location())) {
            return List.of(draw(level.random)); // the band tables come with 0.10.2
        }
        return roll(level, pos, band, centre);
    }

    private static List<ItemStack> roll(ServerLevel level, BlockPos pos, ResourceKey<LootTable> key, ServerPlayer centre) {
        LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
        LootParams.Builder params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos));
        if (centre != null) {
            params.withParameter(LootContextParams.THIS_ENTITY, centre);
        }
        return table.getRandomItems(params.create(LootContextParamSets.GIFT));
    }

    /** The Saci's stash's first item with its Second-hand line, taken from the stash; empty when there is none. */
    static ItemStack stash(MinecraftServer server) {
        try {
            CompoundTag storage = server.getCommandStorage().get(STASH);
            ListTag list = storage.getList("stash", Tag.TAG_COMPOUND);
            if (list.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = ItemStack.parseOptional(server.registryAccess(), list.getCompound(0));
            list.remove(0);
            CompoundTag copy = storage.copy();
            copy.put("stash", list);
            server.getCommandStorage().set(STASH, copy);
            if (!stack.isEmpty()) {
                List<Component> lore = new ArrayList<>();
                ItemLore old = stack.get(DataComponents.LORE);
                if (old != null) {
                    lore.addAll(old.lines());
                }
                lore.add(Component.translatable(K + "second_hand").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GRAY)));
                stack.set(DataComponents.LORE, new ItemLore(lore));
            }
            return stack;
        } catch (Throwable t) {
            StoryCore.logOnce("hoard_fall.stash", t);
            return ItemStack.EMPTY;
        }
    }

    /** The Notice of Loss: a paper with its name, six lore lines and the notice component. */
    public static ItemStack notice() {
        ItemStack paper = new ItemStack(Items.PAPER);
        paper.set(DataComponents.ITEM_NAME, Component.translatable(K + "notice.name"));
        List<Component> lines = new ArrayList<>(6);
        for (int i = 1; i <= 6; i++) {
            lines.add(Component.translatable(K + "notice." + i).withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GRAY)));
        }
        paper.set(DataComponents.LORE, new ItemLore(lines));
        paper.set(NOTICE_OF_LOSS.get(), Unit.INSTANCE);
        return paper;
    }

    /** A vault opened: R5 once (none if the First Fall's label already granted it) and Wonders row 8, once. */
    static void opened(ServerPlayer player) {
        FirstHourUtil.award(player, "livro/r5");
        FirstHourUtil.award(player, "wonder/8");
    }
}
