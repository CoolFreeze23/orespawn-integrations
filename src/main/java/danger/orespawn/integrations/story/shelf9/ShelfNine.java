package danger.orespawn.integrations.story.shelf9;

import danger.orespawn.integrations.config.IntegrationsConfig;
import danger.orespawn.integrations.story.StoryClock;
import danger.orespawn.integrations.story.StoryCore;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Shelf 9's join handler (structure-hatching-ground, item-hatchling-shell): every first login on the road starts here.
 * In order: the Eggshell once, the Hatch Register line (late for a pre-0.10.0 player), and for a new line the straw's
 * top-up and the Shelf 9 waystone; then the canopy cap's new-day reset. Each step runs in its own try, so one failing
 * step never stops the others or the login. It listens at HIGH priority so it reads "joined before 0.10.0" before the
 * shipped Guide handler can set its flag on a new player.
 */
public final class ShelfNine {

    /** The shipped Guide's join flag: a player who has it joined before 0.10.0. */
    static final String OLD_GUIDE_FLAG = "orespawn_integrations:guide_given";

    private ShelfNine() {
    }

    public static void init(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, ShelfNine::onLogin);
        StoryClock.everySecondPerPlayer("shelf9.register_origin", HatchRegister::checkPendingOrigin);
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        boolean veteran = joinedBefore0100(player);
        try {
            giveShell(player, veteran);
        } catch (Throwable t) {
            StoryCore.logOnce("shelf9.join.shell", t);
        }
        boolean newLine = false;
        try {
            newLine = HatchRegister.enter(player, veteran);
        } catch (Throwable t) {
            StoryCore.logOnce("shelf9.join.register", t);
        }
        if (newLine) {
            onFirstJoin(player);
        }
        try {
            Eggshell.resetCopaOnNewDay(player);
        } catch (Throwable t) {
            StoryCore.logOnce("shelf9.join.copa", t);
        }
    }

    /** A player's first login since 0.10.0 (their Register line was just written). */
    private static void onFirstJoin(ServerPlayer player) {
    }

    /**
     * The Eggshell is the join's only item. A veteran (joined before 0.10.0) gets theirs in the Back-Dated Fall's
     * late-delivery crate instead (event-first-fall), so the join skips them while that Fall is switched on.
     */
    private static void giveShell(ServerPlayer player, boolean veteran) {
        if (!IntegrationsConfig.on(IntegrationsConfig.storyHatchlingShell) || Eggshell.isGiven(player)) {
            return;
        }
        boolean crateDelivers = veteran && ModList.get().isLoaded("orespawn")
                && IntegrationsConfig.on(IntegrationsConfig.aliveFirstFall)
                && IntegrationsConfig.on(IntegrationsConfig.aliveFirstFallExistingPlayers);
        if (!crateDelivers) {
            Eggshell.giveOnce(player);
        }
    }

    /**
     * True for a player who was on this world before 0.10.0: they carry the shipped Guide's join flag, or they already
     * have play time while holding no Register line and no shell bit (the stats file loads before this event).
     */
    static boolean joinedBefore0100(ServerPlayer player) {
        if (Eggshell.isGiven(player) || HatchRegister.lineOf(player) > 0) {
            return false;
        }
        if (player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getBoolean(OLD_GUIDE_FLAG)) {
            return true;
        }
        return player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) > 0;
    }
}
