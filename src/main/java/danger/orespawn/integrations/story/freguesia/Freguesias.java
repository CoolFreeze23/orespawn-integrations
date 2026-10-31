package danger.orespawn.integrations.story.freguesia;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Freguesia's calls for other systems and for the GameTests: whether a player's yard holds a spot (the Sunday
 * count's yard skip, the casket yard), a stone's record, and the stone's own handlers run by hand.
 */
public final class Freguesias {

    private Freguesias() {
    }

    /** True when {@code pos} is within 48 blocks of a standing stone {@code player} signs (the yard). */
    public static boolean inSignedYard(ServerPlayer player, BlockPos pos) {
        FreguesiaData data = FreguesiaData.get(player.getServer());
        for (FreguesiaData.Stone s : data.near(StoneRecords.dim(player.level()), pos.getX(), pos.getZ(),
                StandingRules.RADIUS)) {
            if (s.standing && s.isSigner(player.getUUID())) {
                return true;
            }
        }
        return false;
    }

    /** The standing record at {@code pos} (the stone's lower half), or null. */
    public static FreguesiaData.Stone at(ServerLevel level, BlockPos pos) {
        return FreguesiaData.get(level.getServer()).at(StoneRecords.dim(level), pos);
    }

    /** Founds or resumes a record at a stone placed at {@code pos} by {@code player}. */
    public static void placed(ServerLevel level, BlockPos pos, ServerPlayer player) {
        StoneRecords.onStonePlaced(level, pos, player);
    }

    /** Counts one decor placement, as the placement handler does. */
    public static void countDecor(ServerLevel level, ServerPlayer player, BlockPos pos) {
        StoneCounter.count(level, player, pos);
    }

    /** Runs one stone's dawn check now. */
    public static void check(ServerLevel level, FreguesiaData.Stone stone) {
        StoneDawn.check(level, FreguesiaData.get(level.getServer()), stone, StoneRecords.today(level.getServer()));
    }

    /** Runs the whole Freguesia dawn (every record, then Wednesday's pick on a Wednesday). */
    public static void dawn(MinecraftServer server) {
        StoneDawn.onDawn(server);
    }

    /** Clears today's scored positions and cap of one stone, as a dawn does, without its check. */
    public static void newDay(FreguesiaData.Stone stone) {
        stone.today.clear();
        stone.todayHalves = 0;
        stone.todayBit = false;
    }

    /** One sneak-use on the stone with the main-hand item; true when the stone handled it. */
    public static boolean sneakUse(ServerLevel level, FreguesiaData.Stone stone, ServerPlayer player) {
        return StoneSigning.use(level, FreguesiaData.get(level.getServer()), stone, player, player.getMainHandItem());
    }

    /** A signer's stake at {@code pos}, as the stake's placement does; true when it closed a plot. */
    public static boolean stake(ServerLevel level, BlockPos pos, ServerPlayer player) {
        // The stone the stake joins, chosen as the stake's placement chooses it: the first standing one the player signs.
        // Any other stone in the 48 blocks (a neighbour's) keeps its plots whatever the stake does.
        FreguesiaData.Stone before = null;
        int plots = 0;
        for (FreguesiaData.Stone s : FreguesiaData.get(level.getServer()).near(StoneRecords.dim(level), pos.getX(),
                pos.getZ(), StandingRules.RADIUS)) {
            if (s.standing && s.isSigner(player.getUUID())) {
                before = s;
                plots = s.plots.size();
                break;
            }
        }
        Plots.onStakePlaced(level, pos, player);
        return before != null && before.plots.size() > plots;
    }

    /** Sends Wednesday's peddler and his llamas to {@code pos} now; false when no loaded spot was found. */
    public static boolean peddler(ServerLevel level, BlockPos pos) {
        return WednesdayMarket.arrive(level, pos);
    }
}
