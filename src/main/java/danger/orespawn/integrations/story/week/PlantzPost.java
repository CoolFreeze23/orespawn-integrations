package danger.orespawn.integrations.story.week;

import danger.orespawn.integrations.story.StoryCore;
import danger.orespawn.integrations.story.StoryScheduler;
import danger.orespawn.integrations.story.firsthour.FirstHourUtil;
import joshxviii.plantz.block.entity.MailboxBlockEntity;
import joshxviii.plantz.block.entity.MailboxMailQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Gazette's second route: the subscriber's registered Plants & Zombies mailbox ({@code plantz:mailbox} in any
 * colour). P&Z boxes have no owner, so OI keeps one: whoever places a box is recorded, and a box placed before the
 * update is claimed by sneak-using it with an empty hand; one box a player, so a second claim moves it. A copy for a
 * loaded box goes straight in (P&Z's own {@code tryInsertIntoMailbox}); for an unloaded one it waits in OI's record,
 * one copy a box (today's replaces yesterday's unread), and enters the box when its chunk next loads.
 */
public final class PlantzPost implements Gazette.Mailman {

    private PlantzPost() {
    }

    public static void init(IEventBus modBus) {
        Gazette.plantz = new PlantzPost();
        NeoForge.EVENT_BUS.addListener(PlantzPost::onPlace);
        NeoForge.EVENT_BUS.addListener(PlantzPost::onClaim);
        NeoForge.EVENT_BUS.addListener(PlantzPost::onChunkLoad);
    }

    static boolean isMailbox(BlockState state) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return "plantz".equals(id.getNamespace()) && id.getPath().endsWith("mailbox");
    }

    private static void register(ServerPlayer player, ServerLevel level, BlockPos pos) {
        GazetteStore store = GazetteStore.get(player.getServer());
        GazetteStore.Box box = new GazetteStore.Box(level.dimension().location().toString(), pos.immutable());
        UUID owner = store.ownerOf(box);
        if (owner != null && !owner.equals(player.getUUID())) {
            store.boxes.remove(owner);
        }
        store.boxes.put(player.getUUID(), box);
        store.setDirty();
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent event) {
        try {
            if (event.getEntity() instanceof ServerPlayer player && event.getLevel() instanceof ServerLevel level
                    && isMailbox(event.getPlacedBlock())) {
                register(player, level, event.getPos());
            }
        } catch (Throwable t) {
            StoryCore.logOnce("gazette.plantz.place", t);
        }
    }

    private static void onClaim(PlayerInteractEvent.RightClickBlock event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer player) || event.getHand() != InteractionHand.MAIN_HAND
                    || !player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()
                    || !(event.getLevel() instanceof ServerLevel level)
                    || !isMailbox(level.getBlockState(event.getPos()))) {
                return;
            }
            register(player, level, event.getPos());
            FirstHourUtil.actionbar(player, Component.translatable(Gazette.KEY + "claimed"));
        } catch (Throwable t) {
            StoryCore.logOnce("gazette.plantz.claim", t);
        }
    }

    @Override
    public Gazette.Delivery deliver(MinecraftServer server, UUID player, ItemStack copy) {
        GazetteStore store = GazetteStore.get(server);
        GazetteStore.Box box = store.boxes.get(player);
        if (box == null) {
            return null;
        }
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(box.dim()));
        ServerLevel level = server.getLevel(dim);
        if (level == null) {
            return null;
        }
        if (level.isLoaded(box.pos())) {
            if (!isMailbox(level.getBlockState(box.pos()))) {
                // The box is gone: the claim lapses and the copy goes to the rack.
                store.boxes.remove(player);
                store.setDirty();
                return null;
            }
            if (insert(level, box.pos(), copy)) {
                return new Gazette.Delivery(dim, box.pos());
            }
        }
        store.waiting.put(box.id(), copy);
        store.setDirty();
        return new Gazette.Delivery(dim, box.pos());
    }

    private static boolean insert(ServerLevel level, BlockPos pos, ItemStack copy) {
        Gazette.clearUnread(level, pos);
        return level.getBlockEntity(pos) instanceof MailboxBlockEntity be
                && MailboxMailQueue.Companion.tryInsertIntoMailbox(be, copy);
    }

    private static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        GazetteStore store = GazetteStore.get(level.getServer());
        if (store.waiting.isEmpty()) {
            return;
        }
        ChunkPos chunk = event.getChunk().getPos();
        String dim = level.dimension().location().toString();
        List<String> due = new ArrayList<>();
        for (String id : store.waiting.keySet()) {
            GazetteStore.Box box = GazetteStore.Box.parse(id);
            if (box.dim().equals(dim) && new ChunkPos(box.pos()).equals(chunk)) {
                due.add(id);
            }
        }
        if (due.isEmpty()) {
            return;
        }
        // One tick later the chunk's block entities are in place.
        StoryScheduler.schedule(level.getServer(), 1, "gazette.plantz.load", () -> {
            for (String id : due) {
                ItemStack copy = store.waiting.get(id);
                GazetteStore.Box box = GazetteStore.Box.parse(id);
                if (copy != null && level.isLoaded(box.pos()) && insert(level, box.pos(), copy)) {
                    store.waiting.remove(id);
                    store.setDirty();
                }
            }
        });
    }

    static Map<String, ItemStack> waiting(MinecraftServer server) {
        return GazetteStore.get(server).waiting;
    }
}
