package danger.orespawn.integrations.story.freguesia;

import danger.orespawn.integrations.story.chaos.CasketCore;
import danger.orespawn.integrations.story.firsthour.FirstHourUtil;
import danger.orespawn.integrations.story.week.LandsWeek;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.horse.TraderLlama;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Wednesday's peddler (system-freguesia, "Wednesday's peddler"; wonder-wednesday-market). At the Land's Wednesday dawn
 * one vanilla {@code minecraft:wandering_trader} with two trader llamas comes to one house a server, with vanilla's own
 * despawn: {@code DespawnDelay} 48,000 ticks (two game days, about 40 real minutes), a countdown that pauses while
 * someone trades. Only stones whose chunk is entity-ticking at that dawn are read, and no chunk is loaded for them.
 * <ul>
 *   <li>First, a newcomer: a player in their first week (or one who kept their turn) who never had the market and whose
 *       stone or bed is loaded takes the visit once, the earliest join first; that Wednesday has no best-kept pick;</li>
 *   <li>otherwise the week's best-kept: the most standing gained since last Wednesday ({@link StandingRules#pick}),
 *       stones and the bed counters of players with no stone alike. Everyone within 48 blocks of it gets Wednesday
 *       Market, and each signer (or the bed's owner) who was away gets it on coming within 48 while the trader stands,
 *       checked once a second for those few players only.</li>
 * </ul>
 * The week's gains start again at every Wednesday dawn.
 */
final class WednesdayMarket {

    static final int WEDNESDAY = 3; // LandsWeek.today: Sunday = 0
    static final int DESPAWN_DELAY = 48000;
    private static final int FIRST_WEEK_DAYS = 7;
    private static final String K = StoneRecords.K;
    private static final String NODE = "wonder/10";

    private static UUID trader;
    private static ServerLevel traderLevel;
    private static BlockPos target;
    private static final Set<UUID> AWAY = new HashSet<>();
    private static long until;

    private WednesdayMarket() {
    }

    static void reset() {
        trader = null;
        traderLevel = null;
        target = null;
        AWAY.clear();
    }

    /** The Wednesday dawn. */
    static void onDawn(MinecraftServer server) {
        if (LandsWeek.today(server) != WEDNESDAY || !StoneRecords.enabled()) {
            return;
        }
        FreguesiaData data = FreguesiaData.get(server);
        try {
            if (!newcomer(server, data)) {
                bestKept(server, data);
            }
        } finally {
            for (FreguesiaData.Stone s : data.stones()) {
                s.weekGainHalves = 0;
            }
            for (FreguesiaData.Bed b : data.beds()) {
                b.weekGainHalves = 0;
            }
            data.setDirty();
        }
    }

    // --- a newcomer's first Wednesday: the market comes to see the baby ---

    private static boolean newcomer(MinecraftServer server, FreguesiaData data) {
        long today = StoneRecords.today(server);
        List<ServerPlayer> eligible = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (data.hadMarket.contains(p.getUUID())) {
                continue;
            }
            int joined = FirstHourUtil.score(p, CasketCore.OBJ_JOIN_DAY, Integer.MIN_VALUE);
            boolean firstWeek = joined != Integer.MIN_VALUE && today - joined < FIRST_WEEK_DAYS;
            if (firstWeek || data.newcomerTurn.contains(p.getUUID())) {
                eligible.add(p);
            }
        }
        eligible.sort(Comparator.comparingInt(p -> FirstHourUtil.score(p, CasketCore.OBJ_JOIN_DAY, Integer.MAX_VALUE)));
        ServerPlayer chosen = null;
        ServerLevel level = null;
        BlockPos at = null;
        for (ServerPlayer p : eligible) {
            Object[] home = home(server, data, p);
            if (home != null) {
                chosen = p;
                level = (ServerLevel) home[0];
                at = (BlockPos) home[1];
                break;
            }
        }
        if (chosen == null) {
            return false;
        }
        for (ServerPlayer p : eligible) {
            if (p != chosen) {
                data.newcomerTurn.add(p.getUUID()); // the rest keep their turn for next Wednesday
            }
        }
        if (!arrive(level, at)) {
            return false;
        }
        data.hadMarket.add(chosen.getUUID());
        data.newcomerTurn.remove(chosen.getUUID());
        say(level, at, Component.translatable(K + "peddler_newcomer"), null);
        StoneRecords.deed(server, null, chosen.getUUID(), K + "deed.newcomer", chosen.getGameProfile().getName());
        for (FreguesiaData.Stone s : data.stones()) {
            s.visits = StandingRules.shiftVisits(s.visits, false);
        }
        for (FreguesiaData.Bed b : data.beds()) {
            b.visits = StandingRules.shiftVisits(b.visits, false);
        }
        return true;
    }

    /** A newcomer's stone if they sign a loaded one, else their loaded bed: {level, pos}, or null. */
    private static Object[] home(MinecraftServer server, FreguesiaData data, ServerPlayer p) {
        for (FreguesiaData.Stone s : data.stones()) {
            if (s.standing && !s.filed && s.isSigner(p.getUUID())) {
                ServerLevel level = StoneRecords.level(server, s.dim);
                if (level != null && level.isPositionEntityTicking(s.pos)) {
                    return new Object[]{level, s.pos};
                }
            }
        }
        BlockPos bed = p.getRespawnPosition();
        ServerLevel level = bed == null ? null : server.getLevel(p.getRespawnDimension());
        if (level != null && level.isPositionEntityTicking(bed)) {
            return new Object[]{level, bed};
        }
        return null;
    }

    // --- the week's best-kept house ---

    private static void bestKept(MinecraftServer server, FreguesiaData data) {
        long today = StoneRecords.today(server);
        List<StandingRules.Candidate> candidates = new ArrayList<>();
        List<Object> houses = new ArrayList<>();
        for (FreguesiaData.Stone s : data.stones()) {
            if (!s.standing || s.filed || today - s.lastSignerDay > StandingRules.ABSENT_DAYS) {
                continue;
            }
            ServerLevel level = StoneRecords.level(server, s.dim);
            if (level == null || !level.isPositionEntityTicking(s.pos)) {
                continue; // a stone unloaded at dawn gets no visit that day
            }
            candidates.add(new StandingRules.Candidate(houses.size(), s.weekGainHalves, s.standingHalves, s.visits));
            houses.add(s);
        }
        for (FreguesiaData.Bed b : data.beds()) {
            if (b.bedPos == null || data.signsAny(b.player)) {
                continue;
            }
            ServerLevel level = StoneRecords.level(server, b.bedDim);
            if (level == null || !level.isPositionEntityTicking(b.bedPos)) {
                continue;
            }
            candidates.add(new StandingRules.Candidate(houses.size(), b.weekGainHalves, b.standingHalves, b.visits));
            houses.add(b);
        }
        int won = StandingRules.pick(candidates);
        Object winner = won < 0 ? null : houses.get(won);
        boolean arrived = false;
        if (winner instanceof FreguesiaData.Stone s) {
            ServerLevel level = StoneRecords.level(server, s.dim);
            arrived = arrive(level, s.pos);
            if (arrived) {
                say(level, s.pos, Component.translatable(K + "peddler"), s.signers);
                StoneRecords.deed(server, s, s.founder(), K + "deed.market", s.founderName());
            }
        } else if (winner instanceof FreguesiaData.Bed b) {
            ServerLevel level = StoneRecords.level(server, b.bedDim);
            arrived = arrive(level, b.bedPos);
            if (arrived) {
                say(level, b.bedPos, Component.translatable(K + "peddler"), List.of(b.player));
                StoneRecords.deed(server, null, b.player, K + "deed.market", b.name);
            }
        }
        for (FreguesiaData.Stone s : data.stones()) {
            s.visits = StandingRules.shiftVisits(s.visits, arrived && s == winner);
        }
        for (FreguesiaData.Bed b : data.beds()) {
            b.visits = StandingRules.shiftVisits(b.visits, arrived && b == winner);
        }
    }

    // --- the arrival ---

    /** The peddler and two llamas beside {@code at}; false when no loaded spot was found. */
    static boolean arrive(ServerLevel level, BlockPos at) {
        BlockPos spot = Arrivals.near(level, at, 3);
        if (spot == null) {
            return false;
        }
        WanderingTrader peddler = EntityType.WANDERING_TRADER.spawn(level, spot, MobSpawnType.EVENT);
        if (peddler == null) {
            return false;
        }
        peddler.setDespawnDelay(DESPAWN_DELAY);
        peddler.setWanderTarget(at);
        peddler.restrictTo(at, 16);
        for (int i = 0; i < 2; i++) {
            BlockPos llamaSpot = Arrivals.near(level, spot, 2);
            TraderLlama llama = llamaSpot == null ? null : EntityType.TRADER_LLAMA.spawn(level, llamaSpot, MobSpawnType.EVENT);
            if (llama != null) {
                llama.setLeashedTo(peddler, true);
            }
        }
        trader = peddler.getUUID();
        traderLevel = level;
        target = at.immutable();
        until = level.getServer().getTickCount() + DESPAWN_DELAY * 4L; // a long trade can hold him; the check stops by then
        return true;
    }

    /**
     * The line to everyone within 48 blocks of the target; for a best-kept visit also the node, and the house's own
     * people who were away are watched for while the trader stands.
     */
    private static void say(ServerLevel level, BlockPos at, Component line, List<UUID> hosts) {
        Vec3 c = Vec3.atCenterOf(at);
        double r2 = (double) StandingRules.RADIUS * StandingRules.RADIUS;
        AWAY.clear();
        Set<UUID> granted = new HashSet<>();
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(c) <= r2) {
                p.sendSystemMessage(line);
                if (hosts != null) {
                    FirstHourUtil.award(p, NODE);
                    granted.add(p.getUUID());
                }
            }
        }
        if (hosts != null) {
            FreguesiaData.get(level.getServer()).hadMarket.addAll(hosts); // the house has had its market
            for (UUID h : hosts) {
                if (!granted.contains(h)) {
                    AWAY.add(h);
                }
            }
        }
    }

    /** The away-host check: once a second while the trader stands, for the house's people not yet granted. */
    static void everySecond(MinecraftServer server) {
        if (trader == null) {
            return;
        }
        Entity e = traderLevel.getEntity(trader);
        boolean loaded = traderLevel.isPositionEntityTicking(target);
        if ((e == null && loaded) || (e != null && !e.isAlive()) || server.getTickCount() > until) {
            reset(); // he left: the check stops and the set is cleared
            return;
        }
        if (AWAY.isEmpty()) {
            return;
        }
        Vec3 c = Vec3.atCenterOf(target);
        double r2 = (double) StandingRules.RADIUS * StandingRules.RADIUS;
        AWAY.removeIf(id -> {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null && p.level() == traderLevel && p.distanceToSqr(c) <= r2) {
                FirstHourUtil.award(p, NODE);
                return true;
            }
            return false;
        });
    }

    /** True while a peddler sent by the market stands. For the GameTests. */
    static boolean standing() {
        return trader != null;
    }
}
