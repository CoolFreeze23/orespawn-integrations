package danger.orespawn.integrations.gametest;

import danger.orespawn.integrations.story.Ranks;
import danger.orespawn.integrations.story.court.CourtRanks;
import danger.orespawn.integrations.story.deed.DeedLog;
import danger.orespawn.integrations.story.hunt.MondayBoard;
import danger.orespawn.integrations.story.hunt.WallOfTheWanted;
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
        ServerPlayer player = MockPlayers.inLevel(helper);
        for (String objective : new String[]{CourtRanks.COURT, CourtRanks.STAMP, CourtRanks.WAITING}) {
            FirstHourUtil.resetScore(player, objective);
        }
        return player;
    }

    /**
     * Makes {@code player} a save from before 0.10.1, which carries no cc_corte: the mock player's own join already ran
     * the once-only backfill on an empty player, before the test granted the advancements it is meant to read.
     */
    static void beforeTheUpdate(ServerPlayer player) {
        player.setData(CourtRanks.CORTE.get(), new int[3]);
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
        beforeTheUpdate(player);
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
        beforeTheUpdate(player);
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

    // --- system-deed-log ---

    /** Ten deeds of mixed kinds for two players: Saturday tells each listener at most four of their own. */
    @GameTest(template = "empty8")
    public static void saturdayTellsAtMostFour(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer ana = fresh(helper);
        DeedLog.clear(server, ana.getUUID());
        for (int i = 0; i < 7; i++) {
            DeedLog.record(ana, "orespawn_integrations.deed.market", ana.getGameProfile().getName());
        }
        for (int i = 0; i < 3; i++) {
            DeedLog.record(ana, danger.orespawn.integrations.story.deed.DeedRules.Kind.WEAPON, false,
                    "orespawn_integrations.deed.boss_kill", "Ana", "#entity.minecraft.wither", "#item.minecraft.stick");
        }
        helper.assertTrue(DeedLog.of(server, ana.getUUID()).size() == 10, "ten lines kept");
        helper.assertTrue(DeedLog.saturdayLines(server, ana).size() <= 4, "at most four on Saturday");
        DeedLog.clear(server, ana.getUUID());
        helper.succeed();
    }

    /** A printed line keeps its bit through a save and a load (a restart). */
    @GameTest(template = "empty8")
    public static void printedBitSurvivesASave(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer ana = fresh(helper);
        DeedLog.clear(server, ana.getUUID());
        DeedLog.record(ana, "orespawn_integrations.deed.homestead", "Ana");
        DeedLog.markPrinted(server, ana.getUUID(), Long.MAX_VALUE);
        net.minecraft.nbt.CompoundTag saved = DeedLog.Store.get(server)
                .save(new net.minecraft.nbt.CompoundTag(), server.registryAccess());
        DeedLog.Store loaded = DeedLog.Store.load(saved, server.registryAccess());
        helper.assertTrue(loaded != null, "loaded");
        helper.assertTrue(DeedLog.of(server, ana.getUUID()).get(0).printed(), "printed bit set");
        helper.assertTrue(saved.toString().contains("bits:8b"), "printed bit written");
        DeedLog.clear(server, ana.getUUID());
        helper.succeed();
    }

    /** The Saturday chat budget: eight lines, then the ninth is refused for the Gazette. */
    @GameTest(template = "empty8")
    public static void saturdayBudgetIsEight(GameTestHelper helper) {
        ServerPlayer ana = fresh(helper);
        ana.setData(DeedLog.HEARD.get(), new int[2]);
        for (int i = 0; i < 8; i++) {
            helper.assertTrue(DeedLog.saturdayBudget(ana), "line " + (i + 1) + " allowed");
        }
        helper.assertFalse(DeedLog.saturdayBudget(ana), "ninth line refused");
        helper.succeed();
    }

    // --- system-boss-ladder ---

    /** A D&T proctor is recognised by its entity tag prefix, not its type. */
    @GameTest(template = "empty8")
    public static void proctorByTagPrefix(GameTestHelper helper) {
        net.minecraft.world.entity.monster.CaveSpider spider = helper.spawn(net.minecraft.world.entity.EntityType.CAVE_SPIDER, 2, 2, 2);
        spider.addTag("cave_spider_boss_1");
        WallOfTheWanted.Hit hit = WallOfTheWanted.identify(spider);
        helper.assertTrue(hit != null && hit.row() == 2, "the Biology Proctor is row 2");
        net.minecraft.world.entity.monster.CaveSpider plain = helper.spawn(net.minecraft.world.entity.EntityType.CAVE_SPIDER, 4, 2, 4);
        helper.assertTrue(WallOfTheWanted.identify(plain) == null, "a plain cave spider is on no poster");
        helper.succeed();
    }

    /** A boss kill grants the row's advancement to the killer and a friend within 32 blocks, and names both. */
    @GameTest(template = "empty8")
    public static void bossKillCreditsTheParty(GameTestHelper helper) {
        ServerPlayer ana = fresh(helper);
        ServerPlayer bia = MockPlayers.inLevel(helper);
        // Both stand at the kill, in this test's own box: a mock player joins at world spawn, among every other test's.
        net.minecraft.world.phys.Vec3 at = helper.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, 1.0, 2.5));
        ana.teleportTo(at.x, at.y, at.z);
        bia.teleportTo(at.x + 3.0, at.y, at.z + 3.0);
        net.minecraft.world.entity.LivingEntity wither = helper.spawn(net.minecraft.world.entity.EntityType.WITHER, 3, 2, 3);
        WallOfTheWanted.Hit hit = WallOfTheWanted.identify(wither);
        helper.assertTrue(hit != null && hit.row() == 5, "the Wither is row 5");
        DeedLog.clear(helper.getLevel().getServer(), ana.getUUID());
        WallOfTheWanted.killed(ana, wither, hit);
        helper.assertTrue(done(ana, "orespawn_integrations:hunt/row_5"), "killer's row advancement");
        helper.assertTrue(done(bia, "orespawn_integrations:hunt/row_5"), "the friend's row advancement");
        helper.assertTrue(!DeedLog.of(helper.getLevel().getServer(), ana.getUUID()).isEmpty(), "the deed is written");
        wither.discard();
        DeedLog.clear(helper.getLevel().getServer(), ana.getUUID());
        helper.succeed();
    }

    /** A row nobody has killed reads "WANTED: ROW n / Felled by: / nobody. Yet." in translate keys. */
    @GameTest(template = "empty8")
    public static void emptyRowSign(GameTestHelper helper) {
        net.minecraft.world.level.block.entity.SignText text = WallOfTheWanted.signText(null, 6);
        String line = text.getMessage(0, false).getContents().toString();
        helper.assertTrue(line.contains("orespawn_integrations.hunt.sign.row.6"), "row 6's first line");
        helper.succeed();
    }

    /** Monday's board: three bounties, one from the player's row, one below, one above. */
    @GameTest(template = "empty8")
    public static void boardDrawsThreeRows(GameTestHelper helper) {
        ServerPlayer ana = fresh(helper);
        ana.setData(MondayBoard.STATE.get(), new net.minecraft.nbt.CompoundTag());
        net.minecraft.nbt.CompoundTag state = MondayBoard.state(ana);
        int[] rows = state.getIntArray("rows");
        helper.assertTrue(rows.length == state.getList("targets", 8).size(), "a row per target");
        if (rows.length == 3) {
            helper.assertTrue(rows[0] == 1 && rows[1] == 1 && rows[2] == 2, "a new hunter: rows 1, 1 and 2");
        }
        helper.succeed();
    }

    // --- npc-mestre ---

    /**
     * The Mestre's queue: with P09 used, the first fresh Mestre gives the Worn Notebook (and P10 is marked), the next the
     * scrap, the next "except the Land"; a diamond to a player never in the Twilight plays the pond line; no second
     * notebook. R18 and Wonder 6 come with the first gift.
     */
    @GameTest(template = "empty8")
    public static void mestreQueue(GameTestHelper helper) {
        ServerPlayer ana = fresh(helper);
        ana.setData(danger.orespawn.integrations.story.land.MestreGiftHook.MESTRE_BITS.get(), 0);
        danger.orespawn.integrations.story.firsthour.TicketState s = danger.orespawn.integrations.story.firsthour.TicketState.fresh();
        for (int p = 1; p <= 9; p++) {
            s.markHanded(p);
        }
        s.step = 9;
        ana.setData(danger.orespawn.integrations.story.firsthour.FirstHourData.FIRST_HOUR.get(), s);
        boolean ars = FirstHourUtil.item("ars_nouveau:worn_notebook") != null;
        var thing = danger.orespawn.integrations.story.land.MestreGiftHook.Gift.THING;
        if (ars) {
            helper.assertTrue("notebook".equals(danger.orespawn.integrations.story.land.MestreGiftHook.gifted(ana, thing)), "notebook first");
            helper.assertTrue(s.handed(10), "P10 marked handed");
        }
        helper.assertTrue("scrap".equals(danger.orespawn.integrations.story.land.MestreGiftHook.gifted(ana, thing)), "then the scrap");
        helper.assertTrue("except_land".equals(danger.orespawn.integrations.story.land.MestreGiftHook.gifted(ana, thing)), "then the Land");
        String next = danger.orespawn.integrations.story.land.MestreGiftHook.gifted(ana,
                danger.orespawn.integrations.story.land.MestreGiftHook.Gift.DIAMOND);
        helper.assertTrue(next.equals("diamond_door") || next.startsWith("origin."), "the pond line (or a group line)");
        helper.assertTrue(done(ana, "orespawn_integrations:livro/r18"), "R18 at the first gift");
        helper.succeed();
    }

    // --- npc-mapinguari ---

    /** A rank-0 player's third Sunday kill plays the grace line and calls no Mapinguari; the second warned. */
    @GameTest(template = "empty8")
    public static void hatchlingGrace(GameTestHelper helper) {
        ServerPlayer ana = fresh(helper);
        FirstHourUtil.setScore(ana, CourtRanks.COURT, 0);
        ana.setData(danger.orespawn.integrations.story.land.MapinguariSunday.STATE.get(), new int[]{-1, 0, 0, -1, 0});
        for (int i = 0; i < 3; i++) {
            danger.orespawn.integrations.story.land.MapinguariSunday.count(ana);
        }
        int[] s = ana.getData(danger.orespawn.integrations.story.land.MapinguariSunday.STATE.get());
        helper.assertTrue(s[1] == 3, "three kills counted");
        helper.assertTrue((s[4] & 2) != 0, "the warning came at the second");
        helper.assertTrue((s[4] & 1) == 0, "no Mapinguari waits for dusk");
        helper.succeed();
    }

    /** A Squire past the first 3 game days: the third kill queues one Mapinguari for dusk. */
    @GameTest(template = "empty8")
    public static void thirdKillQueuesTheGiant(GameTestHelper helper) {
        ServerPlayer ana = fresh(helper);
        FirstHourUtil.setScore(ana, CourtRanks.COURT, 1);
        ana.getStats().setValue(ana, net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.PLAY_TIME), 200000);
        ana.setData(danger.orespawn.integrations.story.land.MapinguariSunday.STATE.get(), new int[]{-1, 0, 0, -1, 0});
        for (int i = 0; i < 3; i++) {
            danger.orespawn.integrations.story.land.MapinguariSunday.count(ana);
        }
        helper.assertTrue((ana.getData(danger.orespawn.integrations.story.land.MapinguariSunday.STATE.get())[4] & 1) != 0,
                "the giant waits for dusk");
        helper.succeed();
    }

    /** Three clean Sundays give the fang and The Sunday Fang; a Sunday with a kill resets the streak. */
    @GameTest(template = "empty8")
    public static void threeCleanSundays(GameTestHelper helper) {
        ServerPlayer ana = fresh(helper);
        ana.setData(danger.orespawn.integrations.story.land.MapinguariSunday.STATE.get(), new int[]{13, 1, 2, 6, 0});
        danger.orespawn.integrations.story.land.MapinguariSunday.settle(ana, 13);
        helper.assertTrue(ana.getData(danger.orespawn.integrations.story.land.MapinguariSunday.STATE.get())[2] == 0,
                "a Sunday with a kill resets the streak");
        ana.setData(danger.orespawn.integrations.story.land.MapinguariSunday.STATE.get(), new int[]{-1, 0, 0, -1, 0});
        for (int sunday : new int[]{6, 13, 20}) {
            danger.orespawn.integrations.story.land.MapinguariSunday.settle(ana, sunday);
        }
        if (FirstHourUtil.item("brazil_legends:mapinguari_fang") != null) {
            helper.assertTrue(done(ana, "orespawn_integrations:lands_week/dente_de_domingo"), "The Sunday Fang");
        }
        helper.succeed();
    }

    /** The yard: a kill within 64 blocks of the killer's respawn point is home, never hunting. */
    @GameTest(template = "empty8")
    public static void yardKillsAreHome(GameTestHelper helper) {
        ServerPlayer ana = fresh(helper);
        net.minecraft.core.BlockPos bed = ana.blockPosition();
        ana.setRespawnPosition(ana.level().dimension(), bed, 0, true, false);
        helper.assertTrue(danger.orespawn.integrations.story.land.MapinguariSunday.atHome(ana, bed.offset(30, 0, 30)), "the yard");
        helper.assertFalse(danger.orespawn.integrations.story.land.MapinguariSunday.atHome(ana, bed.offset(80, 0, 0)), "the wild");
        helper.succeed();
    }
}
