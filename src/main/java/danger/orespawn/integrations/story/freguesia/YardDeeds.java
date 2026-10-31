package danger.orespawn.integrations.story.freguesia;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;

/**
 * The yard's deed (system-hoard-caskets, Somebody's Yard, 0.10.1): each casket the yard turned into the Land's return
 * raises its stone's marker score {@code cc_quintal} (the casket's function can't call the deed log), and once a second
 * this pass reads that one score per standing stone, by the marker's UUID and without touching the entity, and writes
 * the keeper's deed for each swap: "Somebody's casket turned into an ipê in Dona Cida's yard." The deed also counts for
 * the stone's Hamlet.
 */
final class YardDeeds {

    private static final String DEED = "orespawn_integrations.lucky.yard.deed";

    private YardDeeds() {
    }

    static void everySecond(MinecraftServer server) {
        Scoreboard board = server.getScoreboard();
        Objective quintal = board.getObjective(StoneRecords.QUINTAL);
        if (quintal == null || !StoneRecords.enabled()) {
            return;
        }
        FreguesiaData data = FreguesiaData.get(server);
        for (FreguesiaData.Stone s : data.stones()) {
            if (s.marker == null || s.filed) {
                continue;
            }
            ScoreHolder holder = ScoreHolder.forNameOnly(s.marker.toString());
            ReadOnlyScoreInfo info = board.getPlayerScoreInfo(holder, quintal);
            if (info == null || info.value() <= 0) {
                continue;
            }
            int swaps = Math.min(info.value(), 4);
            board.getOrCreatePlayerScore(holder, quintal).set(0);
            for (int i = 0; i < swaps; i++) {
                StoneRecords.deed(server, s, s.founder(), DEED, s.founderName());
            }
        }
    }
}
