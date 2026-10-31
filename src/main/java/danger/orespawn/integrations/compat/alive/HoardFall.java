package danger.orespawn.integrations.compat.alive;

import danger.orespawn.integrations.story.StoryClock;
import danger.orespawn.integrations.story.StoryCore;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * The Hoard Fall's 0.10.1 rework (event-hoard-fall): the night's Fall on the shared clock ({@link MeteorEvent}), the
 * vault ({@link HoardVault}) with its sampler, rebuilt when tags reload, and the open handler that grants R5 and
 * Wonders row 8 from a Fall's vault. Only loaded with OreSpawn, like the rest of the alive world.
 */
public final class HoardFall {

    private HoardFall() {
    }

    public static void init(IEventBus modBus) {
        HoardVault.register(modBus);
        StoryClock.everySecond("hoard_fall", MeteorEvent::second);
        NeoForge.EVENT_BUS.addListener(HoardFall::onTags);
        NeoForge.EVENT_BUS.addListener(HoardFall::onRightClick);
        NeoForge.EVENT_BUS.addListener(HoardFall::onBreak);
        NeoForge.EVENT_BUS.addListener(HoardFall::onStopped);
    }

    /** The Gazette's headline for the last Fall ("Gold fell north of Ana last night. ..."), or null before any. */
    public static net.minecraft.network.chat.Component headline(net.minecraft.server.MinecraftServer server) {
        return MeteorEvent.headline(server);
    }

    private static void onTags(TagsUpdatedEvent event) {
        if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) {
            HoardVault.rebuild();
        }
    }

    private static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        try {
            if (HoardVaults.get(level.getServer()).contains(level, event.getPos())) {
                HoardVault.opened(player);
            }
        } catch (Throwable t) {
            StoryCore.logOnce("hoard_fall.open", t);
        }
    }

    private static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            HoardVaults.get(level.getServer()).remove(level, event.getPos());
        }
    }

    private static void onStopped(ServerStoppedEvent event) {
        MeteorEvent.reset();
    }
}
