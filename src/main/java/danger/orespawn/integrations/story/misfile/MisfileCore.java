package danger.orespawn.integrations.story.misfile;

import danger.orespawn.integrations.config.IntegrationsConfig;
import danger.orespawn.integrations.story.StoryCore;
import danger.orespawn.integrations.story.firsthour.FirstHourUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;

/**
 * The Misfile's 0.10.1 half that is ours (system-extravio, item-etiqueta-de-extravio): the canopy's creature and stray
 * rows and the Misfile Tag are data ({@code extravio/copa/*}, the day tables {@code copa/dia/*}); this class gives
 * those functions the switch {@code story.misfile} as {@code #extravio cc_cfg} (1 on, 0 off), written at start-up and
 * on every config reload, and builds the Gazette's front-page box, TODAY'S MISFILE, from the day's row. The roll
 * itself, the cap reset and the dawn release are lines of the Land's dawn job (system-lands-week).
 */
public final class MisfileCore {

    static final String K = "orespawn_integrations.misfile.box.";
    /** The rows that print their own effect line under the heading (10 also prints the alert). */
    public static final int EVERYTHING_IN_PLACE = 10;

    private MisfileCore() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(MisfileCore::onConfigReload);
        NeoForge.EVENT_BUS.addListener(MisfileCore::onStarted);
    }

    private static void onStarted(ServerStartedEvent event) {
        try {
            writeSwitch(event.getServer());
        } catch (Throwable t) {
            StoryCore.logOnce("misfile.switch", t);
        }
    }

    private static void onConfigReload(ModConfigEvent.Reloading event) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && event.getConfig().getSpec() == IntegrationsConfig.SPEC) {
            server.execute(() -> writeSwitch(server));
        }
    }

    /** {@code #extravio cc_cfg}: 1 with story.misfile on, else 0. Public for the GameTests. */
    public static void writeSwitch(MinecraftServer server) {
        Scoreboard board = server.getScoreboard();
        board.getOrCreatePlayerScore(ScoreHolder.forNameOnly("#extravio"), FirstHourUtil.objective(board, "cc_cfg"))
                .set(IntegrationsConfig.on(IntegrationsConfig.storyMisfile) ? 1 : 0);
    }

    /**
     * The Gazette's box for a day: the heading, the row's name, row 10's alert, and the van's line when the dawn release
     * took any canopy creature. Empty with story.misfile off or before the first roll (row 0).
     */
    public static List<Component> box(int row, int released) {
        List<Component> lines = new ArrayList<>(4);
        if (!IntegrationsConfig.on(IntegrationsConfig.storyMisfile) || row < 1 || row > EVERYTHING_IN_PLACE) {
            return lines;
        }
        lines.add(Component.translatable(K + "heading"));
        lines.add(Component.translatable(K + "row." + row));
        if (row == EVERYTHING_IN_PLACE) {
            lines.add(Component.translatable(K + "alert"));
        }
        if (released > 0) {
            lines.add(Component.translatable(K + "van"));
        }
        return lines;
    }
}
