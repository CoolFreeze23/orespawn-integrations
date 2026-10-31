package danger.orespawn.integrations.story.week;

import danger.orespawn.integrations.story.StoryCore;
import danger.orespawn.integrations.story.deed.DeedLog;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * How the week reads the ranks group's deed log (system-deed-log). The week compiles against the log's two hand-off
 * calls only ({@link DeedLog#record}, {@link DeedLog#week}); the log's richer readers (Saturday's lines told one rung
 * bigger, the Saturday chat budget and its Sunday overflow, the Gazette's headline) arrive with the merge. Each is
 * looked up once by name and used when present; until then the week falls back on {@link DeedLog#week} and keeps
 * its own count, so the retelling, the paper and Lindalva's notice work on either side of the merge.
 */
final class DeedBridge {

    private static boolean looked;
    private static Method saturdayLines;
    private static Method saturdayBudget;
    private static Method fileForGazette;
    private static Method takeOverflow;
    private static Method headline;

    private DeedBridge() {
    }

    private static synchronized void look() {
        if (looked) {
            return;
        }
        looked = true;
        saturdayLines = find("saturdayLines", MinecraftServer.class, ServerPlayer.class);
        saturdayBudget = find("saturdayBudget", ServerPlayer.class);
        fileForGazette = find("fileForGazette", MinecraftServer.class, String.class, Object[].class);
        takeOverflow = find("takeGazetteOverflow", MinecraftServer.class);
        headline = find("headline", MinecraftServer.class, long.class);
    }

    private static Method find(String name, Class<?>... types) {
        try {
            return DeedLog.class.getMethod(name, types);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /** This week's deeds whose subject is {@code player}, oldest first. */
    static List<DeedLog.Deed> weekOf(MinecraftServer server, UUID player) {
        List<DeedLog.Deed> out = new ArrayList<>();
        for (DeedLog.Deed d : DeedLog.week(server)) {
            if (player.equals(d.player())) {
                out.add(d);
            }
        }
        return out;
    }

    /**
     * Saturday's lines for a listener: the log's own pick (their week's unretold lines newest first, then their best
     * older one, each a rung bigger, at most four), or with only the hand-off calls, their week's lines newest first.
     */
    @SuppressWarnings("unchecked")
    static List<Component> saturdayLines(MinecraftServer server, ServerPlayer listener) {
        look();
        if (saturdayLines != null) {
            try {
                return (List<Component>) saturdayLines.invoke(null, server, listener);
            } catch (Throwable t) {
                StoryCore.logOnce("truco.deed_bridge.lines", t);
            }
        }
        List<DeedLog.Deed> own = weekOf(server, listener.getUUID());
        List<Component> out = new ArrayList<>();
        for (int i = own.size() - 1; i >= 0 && out.size() < TrucoRules.LINES; i--) {
            out.add(own.get(i).text());
        }
        return out;
    }

    /**
     * The Saturday chat budget: true while the player has room for one more line (and counts it). Without the log's
     * helper the truco's own four lines are always inside the eight.
     */
    static boolean saturdayBudget(ServerPlayer player) {
        look();
        if (saturdayBudget != null) {
            try {
                return (Boolean) saturdayBudget.invoke(null, player);
            } catch (Throwable t) {
                StoryCore.logOnce("truco.deed_bridge.budget", t);
            }
        }
        return true;
    }

    /** Files a line that went over the Saturday budget for Sunday's Gazette. */
    static void fileForGazette(MinecraftServer server, String key, Object... args) {
        look();
        if (fileForGazette != null) {
            try {
                fileForGazette.invoke(null, server, key, args);
            } catch (Throwable t) {
                StoryCore.logOnce("truco.deed_bridge.file", t);
            }
        }
    }

    /** The lines filed for Sunday's paper, taken once. */
    @SuppressWarnings("unchecked")
    static List<Component> takeOverflow(MinecraftServer server) {
        look();
        if (takeOverflow != null) {
            try {
                return (List<Component>) takeOverflow.invoke(null, server);
            } catch (Throwable t) {
                StoryCore.logOnce("truco.deed_bridge.overflow", t);
            }
        }
        return List.of();
    }

    /** The biggest deed of a Land's day, or null on a quiet day. */
    static DeedLog.Deed headline(MinecraftServer server, long day) {
        look();
        if (headline != null) {
            try {
                return (DeedLog.Deed) headline.invoke(null, server, day);
            } catch (Throwable t) {
                StoryCore.logOnce("truco.deed_bridge.headline", t);
            }
        }
        DeedLog.Deed best = null;
        for (DeedLog.Deed d : DeedLog.week(server)) {
            if (d.day() == day && (best == null || TrucoRules.priority(d.key()) < TrucoRules.priority(best.key()))) {
                best = d;
            }
        }
        return best;
    }

    /** The week's biggest deed, the one Lindalva rhymes: by the Gazette's order, a party deed over a solo one, then the latest. */
    static DeedLog.Deed weekBiggest(MinecraftServer server) {
        DeedLog.Deed best = null;
        for (DeedLog.Deed d : DeedLog.week(server)) {
            if (best == null) {
                best = d;
                continue;
            }
            int a = TrucoRules.priority(d.key());
            int b = TrucoRules.priority(best.key());
            boolean party = d.witnesses().size() > 1;
            boolean bestParty = best.witnesses().size() > 1;
            if (a < b || (a == b && party && !bestParty) || (a == b && party == bestParty && d.day() >= best.day())) {
                best = d;
            }
        }
        return best;
    }
}
