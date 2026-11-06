package danger.orespawn.integrations.story.week;

import danger.orespawn.integrations.story.firsthour.FirstHourUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.List;

/**
 * The Misfile's three lines of the Land's dawn job (system-extravio, from 0.10.1; system-lands-week's S): the
 * canopy's daily caps ({@code cc_copa}) reset for everyone; the Day's Misfile is rolled into
 * {@code #extravio cc_terra} on the Almanac's seeded stream; and every loaded canopy creature
 * ({@code cc.extraviado}) that nobody leashed, tamed or renamed is released, which the Gazette's box reports as the
 * Treasury's van. One in an unloaded chunk waits for the first dawn it is loaded at.
 */
public final class Misfile {

    public static final String COPA = "cc_copa";
    public static final String CREATURE_TAG = "cc.extraviado";

    /** Today's row and how many creatures the van took, for the Gazette's box. */
    private static int todayRow;
    private static int released;

    private Misfile() {
    }

    static void queueDawn(MinecraftServer server, long landDay) {
        ServerLedger.enqueue("extravio.copa", () -> resetCaps(server));
        ServerLedger.enqueue("extravio.roll", () -> roll(server, landDay));
        ServerLedger.enqueue("extravio.release", () -> release(server));
    }

    static void resetCaps(MinecraftServer server) {
        Scoreboard board = server.getScoreboard();
        Objective copa = board.getObjective(COPA);
        if (copa == null) {
            return;
        }
        List<String> owners = new ArrayList<>();
        for (PlayerScoreEntry entry : board.listPlayerScores(copa)) {
            owners.add(entry.owner());
        }
        for (String owner : owners) {
            board.getOrCreatePlayerScore(ScoreHolder.forNameOnly(owner), copa).set(0);
        }
    }

    static void roll(MinecraftServer server, long landDay) {
        Scoreboard board = server.getScoreboard();
        Objective terra = FirstHourUtil.objective(board, LandsWeek.OBJECTIVE);
        ScoreHolder holder = ScoreHolder.forNameOnly(LandsWeek.HOLDER_MISFILE);
        ReadOnlyScoreInfo previous = board.getPlayerScoreInfo(holder, terra);
        RandomSource random = RandomSource.create(
                AlmanacRules.mix(server.overworld().getSeed(), landDay, AlmanacRules.SALT_MISFILE));
        int row = MisfileRules.row(landDay, previous == null ? 0 : previous.value(), random::nextInt);
        board.getOrCreatePlayerScore(holder, terra).set(row);
        todayRow = row;
    }

    static void release(MinecraftServer server) {
        int count = 0;
        for (ServerLevel level : server.getAllLevels()) {
            List<Entity> gone = new ArrayList<>();
            for (Entity entity : level.getAllEntities()) {
                if (entity.getTags().contains(CREATURE_TAG) && unkept(entity)) {
                    gone.add(entity);
                }
            }
            for (Entity entity : gone) {
                entity.discard();
                count++;
            }
        }
        released = count;
    }

    /** Not leashed, not tamed, and still wearing the Misfile name it fell with (a name tag is a rename). */
    static boolean unkept(Entity entity) {
        if (entity instanceof Mob mob && mob.isLeashed()) {
            return false;
        }
        if (entity instanceof OwnableEntity owned && owned.getOwnerUUID() != null) {
            return false;
        }
        Component name = entity.getCustomName();
        if (name == null) {
            return true;
        }
        return name.getContents() instanceof TranslatableContents t
                && t.getKey().startsWith("orespawn_integrations.");
    }

    /** Today's Misfile row (0 before the first dawn of this run reads the holder). */
    public static int todayRow(MinecraftServer server) {
        if (todayRow == 0) {
            Scoreboard board = server.getScoreboard();
            Objective terra = board.getObjective(LandsWeek.OBJECTIVE);
            ReadOnlyScoreInfo info = terra == null ? null
                    : board.getPlayerScoreInfo(ScoreHolder.forNameOnly(LandsWeek.HOLDER_MISFILE), terra);
            todayRow = info == null ? 0 : info.value();
        }
        return todayRow;
    }

    /** How many canopy creatures this dawn's van took. */
    public static int releasedToday() {
        return released;
    }
}
