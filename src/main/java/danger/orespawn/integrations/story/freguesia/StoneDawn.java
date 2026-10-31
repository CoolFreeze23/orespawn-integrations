package danger.orespawn.integrations.story.freguesia;

import danger.orespawn.integrations.story.StoryCore;
import danger.orespawn.integrations.story.deed.DeedLog;
import danger.orespawn.integrations.story.firsthour.FirstHourUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.List;

/**
 * The dawn stage check (system-freguesia), one per loaded Freguesia, from the Land's one dawn job. For each record it
 * clears today's scored positions and bit, and then, only when the stone's chunk is entity-ticking (no chunk is ever
 * loaded for it), confirms the stone still stands, raises at most one stage, says once per stage what key is missing,
 * and tops up the stage's arrivals ({@link Arrivals}). A stone skipped at dawn is checked when a signer next wakes
 * within 48 blocks of it, else at the next dawn it is loaded. A record whose signers have all stayed away 28 game days
 * is skipped until one logs in; a fallen stone's record waits out its grace and is then filed away. Nothing here ever
 * shrinks or despawns.
 */
final class StoneDawn {

    private static final String K = StoneRecords.K;
    private static final String[] STAGE_NODES = {null, "parish/homestead", "parish/hamlet", "parish/parish", "parish/town"};
    private static final String[] STAGE_DEEDS = {null, "deed.homestead", "deed.hamlet", "deed.parish", "deed.town"};
    private static long lastBellDay = -1;

    private StoneDawn() {
    }

    static void reset() {
        lastBellDay = -1;
    }

    /** The Land's dawn: every record, then Wednesday's pick when it is Wednesday. */
    static void onDawn(MinecraftServer server) {
        if (!StoneRecords.enabled()) {
            return;
        }
        FreguesiaData data = FreguesiaData.get(server);
        long today = StoneRecords.today(server);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            StoneCounter.seen(p);
        }
        boolean mirror = false;
        for (FreguesiaData.Stone s : data.stones()) {
            try {
                s.today.clear();
                s.todayHalves = 0;
                s.todayBit = false;
                if (s.pendingEpithet != null) {
                    if (s.customName == null && s.epithetKey == null) {
                        s.epithetKey = s.pendingEpithet;
                    }
                    s.pendingEpithet = null;
                }
                if (s.filed) {
                    continue;
                }
                if (!s.standing) {
                    if (!StandingRules.withinGrace(s.fallenDay, today, StoneRecords.graceDays())) {
                        s.filed = true; // the File's line comes with 0.11.0; the residents stay
                        data.signersChanged();
                        mirror = true;
                    }
                    continue;
                }
                if (today - s.lastSignerDay > StandingRules.ABSENT_DAYS) {
                    continue;
                }
                ServerLevel level = StoneRecords.level(server, s.dim);
                if (level == null || !level.isPositionEntityTicking(s.pos)) {
                    s.pendingCheck = true;
                    continue;
                }
                check(level, data, s, today);
            } catch (Throwable t) {
                StoryCore.logOnce("freguesia.dawn", t);
            }
        }
        for (FreguesiaData.Bed b : data.beds()) {
            b.today.clear();
            b.todayHalves = 0;
        }
        data.setDirty();
        if (mirror) {
            StoneRecords.mirror(server);
        }
    }

    /** One stone's check. Public for the GameTests, which step days by calling it. */
    static void check(ServerLevel level, FreguesiaData data, FreguesiaData.Stone s, long today) {
        MinecraftServer server = level.getServer();
        s.pendingCheck = false;
        BlockState there = level.getBlockState(s.pos);
        if (!FreguesiaBlocks.isMarco(there.getBlock()) || there.getValue(MarcoBlock.HALF) != DoubleBlockHalf.LOWER) {
            StoneRecords.fell(level, data, s); // gone without our hook seeing it: the record pauses all the same
            return;
        }
        if (StoneRecords.marker(level, s) == null) {
            StoneRecords.summonMarker(level, s); // an orphan or a missing marker: exactly one stands at the stone
        }
        StandingRules.Numbers n = StoneRecords.numbers();
        boolean deed = s.deeds > 0 || signerDeedSince(server, s);
        StandingRules.Missing missing = StandingRules.missing(s.stage, s.standing(), s.stoneDays, deed, s.bossKill,
                s.knight, n);
        if (missing == StandingRules.Missing.NONE) {
            s.stage++;
            reached(server, s);
        } else if (missing == StandingRules.Missing.KEY && s.keyNoted < s.stage + 1) {
            s.keyNoted = s.stage + 1;
            for (ServerPlayer p : StoneRecords.onlineSigners(server, s)) {
                p.sendSystemMessage(Component.translatable(K + "key_missing_dawn"));
            }
        }
        Arrivals.topUp(level, data, s);
        if (s.stage >= StandingRules.TOWN && !s.plaque) {
            s.plaque = TownSigns.plaque(level, s);
            if (s.plaque) {
                TownSigns.railStop(level, s); // looked for once, with the plaque
            }
        }
        data.setDirty();
    }

    /** A stage reached: its node to every signer online (the rest at their login) and its deeds to the founder. */
    private static void reached(MinecraftServer server, FreguesiaData.Stone s) {
        for (ServerPlayer p : StoneRecords.onlineSigners(server, s)) {
            awardStages(p, s.stage);
        }
        String founder = s.founderName();
        if (s.stage == StandingRules.HOMESTEAD) {
            StoneRecords.deed(server, s, s.founder(), K + "deed.first_building", founder);
        }
        StoneRecords.deed(server, s, s.founder(), K + STAGE_DEEDS[s.stage], founder);
    }

    /** Grants the stage nodes up to {@code stage}; a node already held is skipped. */
    static void awardStages(ServerPlayer player, int stage) {
        for (int i = 1; i <= Math.min(stage, StandingRules.TOWN); i++) {
            FirstHourUtil.award(player, STAGE_NODES[i]);
        }
    }

    /** True when the deed log holds a deed by one of the stone's signers since its founding. */
    private static boolean signerDeedSince(MinecraftServer server, FreguesiaData.Stone s) {
        List<DeedLog.Deed> week = DeedLog.week(server);
        for (DeedLog.Deed d : week) {
            if (s.isSigner(d.player())) {
                return true;
            }
        }
        return false;
    }

    /** The Town's bell at noon: once a game day, three strokes at every loaded Town stone. */
    static void noonBell(MinecraftServer server) {
        long time = server.overworld().getDayTime();
        long day = time / 24000L;
        long hour = time % 24000L;
        if (hour < 6000L || hour >= 6100L || day == lastBellDay || !StoneRecords.enabled()) {
            return;
        }
        lastBellDay = day;
        for (FreguesiaData.Stone s : FreguesiaData.get(server).stones()) {
            if (!s.standing || s.filed || s.stage < StandingRules.TOWN) {
                continue;
            }
            ServerLevel level = StoneRecords.level(server, s.dim);
            if (level == null || !level.isPositionEntityTicking(s.pos)) {
                continue;
            }
            BlockPos at = s.pos.above();
            for (int i = 0; i < 3; i++) {
                danger.orespawn.integrations.story.StoryScheduler.schedule(server, i * 30, "freguesia.bell",
                        () -> level.playSound(null, at, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0F, 1.0F));
            }
        }
    }
}
