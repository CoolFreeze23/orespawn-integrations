package danger.orespawn.integrations.story.shelf9;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

/** Small scoreboard helpers for the dummy scores the data side reads ({@code cc_shell_casket}, {@code cc_copa}, ...). */
final class Scores {

    private Scores() {
    }

    static Objective dummy(MinecraftServer server, String name) {
        Scoreboard board = server.getScoreboard();
        Objective objective = board.getObjective(name);
        if (objective == null) {
            objective = board.addObjective(name, ObjectiveCriteria.DUMMY, Component.literal(name),
                    ObjectiveCriteria.RenderType.INTEGER, false, null);
        }
        return objective;
    }

    static void set(MinecraftServer server, ScoreHolder holder, String name, int value) {
        server.getScoreboard().getOrCreatePlayerScore(holder, dummy(server, name)).set(value);
    }

    /** The score, or {@code fallback} when the objective or the score is unset. */
    static int get(MinecraftServer server, ScoreHolder holder, String name, int fallback) {
        Objective objective = server.getScoreboard().getObjective(name);
        if (objective == null) {
            return fallback;
        }
        ReadOnlyScoreInfo info = server.getScoreboard().getPlayerScoreInfo(holder, objective);
        return info == null ? fallback : info.value();
    }
}
