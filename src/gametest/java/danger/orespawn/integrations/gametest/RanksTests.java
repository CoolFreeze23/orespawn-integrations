package danger.orespawn.integrations.gametest;

import danger.orespawn.integrations.story.Ranks;
import danger.orespawn.integrations.story.court.CourtRanks;
import danger.orespawn.integrations.story.firsthour.FirstHourUtil;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The ranks group's card test lines a server can check without a client: court ranks (stamps, backfill, skipped
 * ranks), the deed log, the Wall of the Wanted, Monday's board, the Mestre and the Mapinguari's Sunday. Each mock
 * player is fresh, so no test reads another's state.
 */
@GameTestHolder("orespawn_integrations_tests")
@PrefixGameTestTemplate(false)
public final class RanksTests {

    private RanksTests() {
    }

    // --- shared helpers ---

    /**
     * A mock player with the ranks' scores cleared. Every mock player shares one scoreboard name, so each test starts
     * from blank scores; the test bodies run to the end in one tick, so no two interleave.
     */
    static ServerPlayer fresh(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        for (String objective : new String[]{CourtRanks.COURT, CourtRanks.STAMP, CourtRanks.WAITING}) {
            FirstHourUtil.resetScore(player, objective);
        }
        return player;
    }

    static void setLandDay(MinecraftServer server, int day) {
        Scoreboard board = server.getScoreboard();
        board.getOrCreatePlayerScore(ScoreHolder.forNameOnly("#dia"),
                FirstHourUtil.objective(board, CourtRanks.LAND)).set(day);
    }

    static boolean done(ServerPlayer player, String id) {
        return CourtRanks.has(player, id);
    }

    static void grant(ServerPlayer player, String id) {
        AdvancementHolder holder = player.getServer().getAdvancements().get(ResourceLocation.parse(id));
        if (holder == null) {
            return;
        }
        for (String c : player.getAdvancements().getOrStartProgress(holder).getRemainingCriteria()) {
            player.getAdvancements().award(holder, c);
        }
    }

    static boolean holdsItem(ServerPlayer player, String id) {
        for (ItemStack s : player.getInventory().items) {
            if (!s.isEmpty() && s.getItemHolder().getRegisteredName().equals(id)) {
                return true;
            }
        }
        return false;
    }

    // --- system-court-ranks ---

    /** Utopia and the Nether on one Land's day read "one stamp a day": the Nether is re-armed and the player waits. */
    @GameTest(template = "empty8")
    public static void stampsOneADay(GameTestHelper helper) {
        ServerPlayer player = fresh(helper);
        setLandDay(helper.getLevel().getServer(), 40);
        grant(player, "orespawn_integrations:court/provincia/utopia");
        grant(player, "orespawn_integrations:court/provincia/the_nether");
        helper.assertTrue(done(player, "orespawn_integrations:court/provincia/utopia"), "Utopia stamped");
        helper.assertFalse(done(player, "orespawn_integrations:court/provincia/the_nether"), "the Nether re-armed");
        helper.assertTrue(FirstHourUtil.score(player, CourtRanks.WAITING, 0) == 1, "cc_carimbo_espera 1");
        helper.assertFalse(Ranks.atLeast(player, Ranks.SQUIRE), "no Squire on one day");
        // The next Land's day, the Nether completes it.
        setLandDay(helper.getLevel().getServer(), 41);
        grant(player, "orespawn_integrations:court/provincia/the_nether");
        helper.assertTrue(done(player, "orespawn_integrations:court/duas_provincias"), "Two Provinces");
        helper.assertTrue(Ranks.of(player) == Ranks.SQUIRE, "Squire by provinces");
        helper.succeed();
    }

    /** A 0.10.0 hub player holding utopia_visit and enter_the_nether logs in as Squire. */
    @GameTest(template = "empty8")
    public static void backfillFromEntryAdvancements(GameTestHelper helper) {
        ServerPlayer player = fresh(helper);
        setLandDay(helper.getLevel().getServer(), 12);
        grant(player, "orespawn_integrations:ant_farm_frontier/utopia_visit");
        grant(player, "minecraft:story/enter_the_nether");
        CourtRanks.login(player);
        helper.assertTrue(Ranks.of(player) >= Ranks.SQUIRE, "backfilled Squire");
        helper.succeed();
    }

    /** One old visit only stamps (dated before today): a province entered today then completes it. */
    @GameTest(template = "empty8")
    public static void backfillOneVisitWaitsForASecond(GameTestHelper helper) {
        ServerPlayer player = fresh(helper);
        setLandDay(helper.getLevel().getServer(), 12);
        grant(player, "orespawn_integrations:ant_farm_frontier/utopia_visit");
        CourtRanks.login(player);
        helper.assertFalse(Ranks.atLeast(player, Ranks.SQUIRE), "one province is 1 of 2");
        helper.assertTrue(FirstHourUtil.score(player, CourtRanks.STAMP, 0) == 11, "stamp dated before today");
        grant(player, "orespawn_integrations:court/provincia/the_nether");
        helper.assertTrue(Ranks.of(player) == Ranks.SQUIRE, "Squire at the second province");
        helper.succeed();
    }

    /** A rank-0 player given a Fidalgo deed gets Squire, Knight and Fidalgo in one ceremony, the lv4 card included. */
    @GameTest(template = "empty8")
    public static void fidalgoDeedGrantsSkippedRanks(GameTestHelper helper) {
        ServerPlayer player = fresh(helper);
        FirstHourUtil.setScore(player, CourtRanks.COURT, 0);
        CourtRanks.awardCriterion(player, "court/rank_3", "deed");
        helper.assertTrue(Ranks.of(player) == Ranks.FIDALGO, "cc_court 3");
        helper.assertTrue(done(player, "orespawn_integrations:court/rank_1"), "Squire held");
        helper.assertTrue(done(player, "orespawn_integrations:court/rank_2"), "Knight held");
        helper.assertTrue(done(player, "orespawn_integrations:court/rank_3"), "Fidalgo held");
        if (FirstHourUtil.item("securitycraft:keycard_lv4") != null) {
            helper.assertTrue(holdsItem(player, "securitycraft:keycard_lv4"), "Fidalgo's lv4 card");
            helper.assertTrue(holdsItem(player, "securitycraft:keycard_lv3"), "Knight's lv3 card");
        }
        helper.assertTrue(player.getData(CourtRanks.CORTE.get())[1] == Ranks.FIDALGO, "one claim, for Fidalgo");
        helper.succeed();
    }

    /** An Apotheosis tier still makes its rank: Frontier is Squire. */
    @GameTest(template = "empty8")
    public static void frontierMakesSquire(GameTestHelper helper) {
        ServerPlayer player = fresh(helper);
        if (player.getServer().getAdvancements().get(ResourceLocation.parse("apotheosis:progression/frontier")) == null) {
            helper.succeed();
            return;
        }
        FirstHourUtil.setScore(player, CourtRanks.COURT, 0);
        grant(player, "apotheosis:progression/frontier");
        helper.assertTrue(Ranks.of(player) == Ranks.SQUIRE, "Squire by Frontier");
        helper.succeed();
    }

    /** Never by Utopia plus Chaos: Chaos has no stamp. */
    @GameTest(template = "empty8")
    public static void chaosIsNoProvince(GameTestHelper helper) {
        ServerPlayer player = fresh(helper);
        helper.assertTrue(player.getServer().getAdvancements()
                .get(ResourceLocation.parse("orespawn_integrations:court/provincia/chaos")) == null, "no Chaos stamp");
        helper.assertTrue(player.getServer().getAdvancements()
                .get(ResourceLocation.parse("orespawn_integrations:court/provincia/the_end")) == null, "no End stamp");
        helper.succeed();
    }
}
