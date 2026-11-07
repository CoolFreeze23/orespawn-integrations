package danger.orespawn.integrations.gametest;

import danger.orespawn.integrations.command.CcCommand;
import danger.orespawn.integrations.compat.alive.FallSafety;
import danger.orespawn.integrations.compat.alive.MeteorEvent;
import danger.orespawn.integrations.story.firsthour.FieldOdete;
import danger.orespawn.integrations.story.StoryCore;
import danger.orespawn.integrations.story.firsthour.FirstHourData;
import danger.orespawn.integrations.story.firsthour.TicketState;
import danger.orespawn.integrations.story.shelf9.Eggshell;
import danger.orespawn.integrations.story.shelf9.FourWays;
import danger.orespawn.integrations.story.shelf9.HatchRegister;
import danger.orespawn.integrations.story.shelf9.HatchingGroundData;
import danger.orespawn.integrations.story.shelf9.HatchingGroundPlacer;
import danger.orespawn.integrations.story.shelf9.SiteSearch;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.block.Block;
import net.minecraft.util.RandomSource;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * structure-hatching-ground and item-hatchling-shell: the card test lines a server can check alone. Run on a scratch
 * copy of the full pack. Tests that place the Shelf 9 template do so 40 blocks above their own test area, so they never
 * touch a neighbour's; none of them calls the placer's world-level path (no SavedData flag, no spawn move).
 */
@GameTestHolder("orespawn_integrations_tests")
@PrefixGameTestTemplate(false)
public final class ShelfNineTests {

    private ShelfNineTests() {
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
    }

    private static int count(ServerPlayer player, Item item) {
        int n = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                n += stack.getCount();
            }
        }
        return n;
    }

    private static List<String> inventory(ServerPlayer player) {
        List<String> out = new ArrayList<>();
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty()) {
                out.add(BuiltInRegistries.ITEM.getKey(stack.getItem()) + " x" + stack.getCount());
            }
        }
        return out;
    }

    private static int stacks(ServerPlayer player) {
        int n = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty()) {
                n++;
            }
        }
        return n;
    }

    /** "after join, the inventory holds exactly one item, the Eggshell; a second join gives none". */
    @GameTest(template = "empty8")
    public static void eggshellGivenOnce(GameTestHelper helper) {
        // The mock player really logs in, so the join handler has already given the shell, as it does for a player.
        ServerPlayer player = MockPlayers.inLevel(helper);
        helper.assertTrue(Eggshell.isGiven(player), "the join gave the Eggshell");
        helper.assertTrue(count(player, item("orespawn_integrations:hatchling_shell")) == 1, "exactly one Eggshell");
        helper.assertTrue(stacks(player) == 1, "the join's only item (inventory: " + inventory(player) + ")");
        helper.assertTrue(!Eggshell.giveOnce(player), "a second give is refused");
        helper.assertTrue(count(player, item("orespawn_integrations:hatchling_shell")) == 1, "still one Eggshell");
        helper.succeed();
    }

    /**
     * "opening it gives the Field Guide, the casket, a red bed, 6 torches and 3 pães de queijo; opening it adds no
     * Ledger Scrap; the casket rolls the rank-0 table" (the cc_shell_casket score the casket root reads).
     */
    @GameTest(template = "empty8")
    public static void eggshellOpensIntoTheKit(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper);
        player.getInventory().clearContent();
        ItemStack shell = new ItemStack(item("orespawn_integrations:hatchling_shell"));
        Eggshell.open(player, shell);
        helper.assertTrue(shell.isEmpty(), "the shell crumbles");
        helper.assertTrue(count(player, Items.RED_BED) == 1, "a red bed");
        helper.assertTrue(count(player, Items.TORCH) == 6, "6 torches");
        helper.assertTrue(count(player, item("orespawn_integrations:ledger_scrap")) == 0, "no Ledger Scrap");
        helper.assertTrue(count(player, item("orespawn_integrations:hatchling_shell")) == 0, "no second shell");
        if (ModList.get().isLoaded("braziliandelight")) {
            helper.assertTrue(count(player, item("braziliandelight:cheese_bread")) == 3, "3 pães de queijo");
        }
        if (ModList.get().isLoaded("patchouli")) {
            helper.assertTrue(count(player, item("patchouli:guide_book")) == 1, "the Field Guide");
        }
        if (ModList.get().isLoaded("mr_ly_luckyblocks")) {
            helper.assertTrue(count(player, Items.ARMOR_STAND) == 1, "the Royal Casket (ly's lucky block item)");
        }
        if (ModList.get().isLoaded("ars_nouveau")) {
            helper.assertTrue(count(player, item("ars_nouveau:worn_notebook")) == 0, "no notebook for a non-mage");
        }
        var objective = player.getServer().getScoreboard().getObjective(Eggshell.CASKET_SCORE);
        helper.assertTrue(objective != null, "cc_shell_casket exists");
        var score = player.getServer().getScoreboard().getPlayerScoreInfo(player, objective);
        helper.assertTrue(score != null && score.value() == 1, "cc_shell_casket set to 1");
        helper.succeed();
    }

    /**
     * The same kit opened the way a player opens it, a right click in the main hand, which is where vanilla settles the
     * used stack: every item survives, the Field Guide included (it once landed in the emptied hand and was cleared).
     */
    @GameTest(template = "empty8")
    public static void eggshellOpenedByHandKeepsTheWholeKit(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper);
        player.getInventory().clearContent();
        player.getInventory().selected = 0;
        ItemStack shell = new ItemStack(item("orespawn_integrations:hatchling_shell"));
        player.setItemInHand(InteractionHand.MAIN_HAND, shell);
        player.gameMode.useItem(player, helper.getLevel(), shell, InteractionHand.MAIN_HAND);
        helper.assertTrue(count(player, item("orespawn_integrations:hatchling_shell")) == 0, "the shell is used up");
        helper.assertTrue(count(player, Items.RED_BED) == 1, "a red bed");
        helper.assertTrue(count(player, Items.TORCH) == 6, "6 torches");
        if (ModList.get().isLoaded("patchouli")) {
            helper.assertTrue(count(player, item("patchouli:guide_book")) == 1, "the Field Guide survives the hand");
        }
        if (ModList.get().isLoaded("braziliandelight")) {
            helper.assertTrue(count(player, item("braziliandelight:cheese_bread")) == 3, "3 pães de queijo");
        }
        helper.succeed();
    }

    /** "a mage-class player also finds the Worn Notebook" (through the Egg's Label tag). */
    @GameTest(template = "empty8")
    public static void eggshellGivesAMageTheNotebook(GameTestHelper helper) {
        if (!ModList.get().isLoaded("ars_nouveau")) {
            helper.succeed();
            return;
        }
        ServerPlayer player = MockPlayers.inLevel(helper);
        player.getInventory().clearContent();
        player.addTag("cc_origin.fire_mage");
        Eggshell.open(player, new ItemStack(item("orespawn_integrations:hatchling_shell")));
        helper.assertTrue(count(player, item("ars_nouveau:worn_notebook")) == 1, "the Worn Notebook");
        helper.succeed();
    }

    /** "Register lines for 3 joins, each player's stored line number matching their line"; a late line is a line too. */
    @GameTest(template = "empty8")
    public static void registerLinesInJoinOrder(GameTestHelper helper) {
        List<Integer> lines = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            // The join handler enters each new player; a second entry is refused.
            ServerPlayer player = MockPlayers.inLevel(helper);
            helper.assertTrue(HatchRegister.lineOf(player) > 0, "the join entered the Register");
            helper.assertTrue(!HatchRegister.enter(player, false), "one line per player");
            lines.add(HatchRegister.lineOf(player));
        }
        helper.assertTrue(lines.get(0) > 0, "line numbers start at 1");
        helper.assertTrue(lines.get(1) == lines.get(0) + 1 && lines.get(2) == lines.get(1) + 1, "consecutive lines");
        int next = HatchingGroundData.get(helper.getLevel().getServer()).nextLine;
        helper.assertTrue(next == lines.get(2) + 1, "the next free line follows the last");
        helper.succeed();
    }

    /** "three players join in turn after the first took all three larder blocks: each finds three in the straw". */
    @GameTest(template = "empty8")
    public static void strawToppedUpForEachNewPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<BlockPos> spots = List.of(helper.absolutePos(new BlockPos(1, 1, 1)),
                helper.absolutePos(new BlockPos(3, 1, 1)), helper.absolutePos(new BlockPos(5, 1, 1)));
        List<String> ids = List.of("orespawn:cow_spawn_block", "orespawn:sheep_spawn_block", "orespawn:chicken_spawn_block");
        if (!BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse(ids.get(0)))) {
            helper.succeed();
            return;
        }
        helper.assertTrue(HatchingGroundPlacer.topUpStraw(level, spots, ids) == 3, "an empty straw gets three");
        for (int join = 0; join < 3; join++) {
            for (BlockPos spot : spots) {
                level.setBlock(spot, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);  // the last player took them
            }
            helper.assertTrue(HatchingGroundPlacer.topUpStraw(level, spots, ids) == 3, "three back for join " + join);
        }
        helper.assertTrue(HatchingGroundPlacer.topUpStraw(level, spots, ids) == 0, "a full straw stays as it is");
        helper.succeed();
    }

    /** The last step: with the 48-block search and the beach turned down, a patch just past 48 blocks is the site. */
    @GameTest(template = "empty8")
    public static void theLastStepFindsASiteBeyondTheFirstSearch(GameTestHelper helper) {
        BlockPos spawn = helper.absolutePos(BlockPos.ZERO);
        SiteSearch.Rule ring = (x, z, heights, fluid) -> distance2(spawn, x, z) > 48 * 48;
        HatchingGroundPlacer.Search search = HatchingGroundPlacer.findSite(helper.getLevel(), spawn, ring, 56, 60_000L);
        helper.assertTrue(search.ground() != null, "no site past 48 blocks");
        int d2 = distance2(spawn, search.ground().getX(), search.ground().getZ());
        helper.assertTrue(d2 > 48 * 48 && d2 <= 56 * 56, "the site is outside the ring, " + d2);
        helper.assertTrue(!search.timedOut(), "a found site never reads as timed out");
        helper.succeed();
    }

    /** A read in a chunk nobody has loaded yet gets the real ground, not the bottom of the world. */
    @GameTest(template = "empty8")
    public static void aSiteReadSeesTheGroundInAnUnloadedChunk(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos here = helper.absolutePos(BlockPos.ZERO);
        int x = here.getX() + 4096;
        int z = here.getZ() + 4096;
        int offset = 0;
        while (level.hasChunk((x + offset) >> 4, z >> 4) && offset < 4096) {
            offset += 16;
        }
        x += offset;
        helper.assertTrue(!level.hasChunk(x >> 4, z >> 4), "the test chunk was already loaded");
        int top = HatchingGroundPlacer.topAt(level, x, z);
        helper.assertTrue(top >= level.getMinBuildHeight(), "the read fell through to " + top);
        helper.succeed();
    }

    /**
     * The whole search around ground nobody has loaded: without a mod that loads the chunk behind a block read, reading
     * the heightmap straight off the level gives all 25 reads the bottom of the world, a flat dry patch that fits. Run
     * in a dev environment, which has no such mod.
     */
    @GameTest(template = "empty8")
    public static void theSearchNeverSettlesOnTheBottomOfTheWorld(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos here = helper.absolutePos(BlockPos.ZERO);
        BlockPos far = new BlockPos(here.getX() + 8192, 64, here.getZ() + 8192);
        helper.assertTrue(!level.hasChunk(far.getX() >> 4, far.getZ() >> 4), "the far chunk was already loaded");
        HatchingGroundPlacer.Search search = HatchingGroundPlacer.findSite(level, far, SiteSearch.LAND, 52, 60_000L);
        helper.assertTrue(search.ground() == null || search.ground().getY() >= level.getMinBuildHeight(),
                "settled on the bottom of the world at " + search.ground());
        // An empty column, had the world one: nothing under the read but void.
        helper.assertTrue(HatchingGroundPlacer.unusableAt(level, far.getX(), level.getMinBuildHeight() - 1, far.getZ()),
                "an empty column counts as ground");
        helper.succeed();
    }

    /** A column in a chunk nobody has loaded, checked to read the bottom of the world by the plain heightmap read. */
    private static BlockPos unloadedColumn(GameTestHelper helper, int out) {
        ServerLevel level = helper.getLevel();
        BlockPos here = helper.absolutePos(BlockPos.ZERO);
        BlockPos far = new BlockPos(here.getX() + out, 64, here.getZ() + out);
        helper.assertTrue(!level.hasChunk(far.getX() >> 4, far.getZ() >> 4), "the column's chunk was already loaded");
        helper.assertTrue(level.getHeight(Heightmap.Types.MOTION_BLOCKING, far.getX(), far.getZ()) == level.getMinBuildHeight(),
                "the plain read no longer answers the bottom of the world on an unloaded chunk");
        return far;
    }

    /** A coin or Alien over an unloaded column doesn't land at the bottom of the world, and a landing's own column reads true. */
    @GameTest(template = "empty8")
    public static void aLandingNeverScattersToTheBottomOfTheWorld(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos coin = unloadedColumn(helper, 16384);
        helper.assertTrue(FallSafety.heightOr(level, coin.getX(), coin.getZ(), 77) == 77, "a coin's height without its chunk");

        BlockPos alien = unloadedColumn(helper, 32768);
        BlockPos landing = helper.absolutePos(new BlockPos(3, 1, 3));
        BlockPos stands = MeteorEvent.alienSpot(level, alien.getX(), alien.getZ(), landing.getX(), landing.getZ());
        helper.assertTrue(stands.getX() == landing.getX() && stands.getZ() == landing.getZ(), "the Alien stayed on its column");
        helper.assertTrue(stands.getY() > level.getMinBuildHeight(), "the Alien stands at " + stands);

        BlockPos crater = unloadedColumn(helper, 49152);
        int top = MeteorEvent.landingTop(level, crater.getX(), crater.getZ());
        helper.assertTrue(top > level.getMinBuildHeight(), "the crater's top read " + top);
        helper.succeed();
    }

    /** The Registry pin on a village nobody has visited stands at sea level, not at the bottom of the world. */
    @GameTest(template = "empty8")
    public static void aPinOnAFarVillageIsNotAtTheBottomOfTheWorld(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos village = unloadedColumn(helper, 65536);
        helper.assertTrue(FieldOdete.pinHeight(level, village.getX(), village.getZ()) == level.getSeaLevel(), "the pin's height");
        helper.succeed();
    }

    /** Open ground with no structure is not in a structure's piece. Full-pack seeds prove the other side, a real windmill. */
    @GameTest(template = "empty8")
    public static void openGroundIsNotAStructurePiece(GameTestHelper helper) {
        helper.assertTrue(!HatchingGroundPlacer.inAStructurePiece(helper.getLevel(), helper.absolutePos(new BlockPos(3, 1, 3))),
                "an empty test box read as a structure");
        helper.succeed();
    }

    /** A sky-ship's deck is not land: air right under a read rules it out, as fluid does; solid ground under it doesn't. */
    @GameTest(template = "empty8")
    public static void aDeckWithAirUnderItIsNoSite(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(new BlockPos(2, 6, 2), Blocks.DARK_PRISMARINE);
        BlockPos deck = helper.absolutePos(new BlockPos(2, 6, 2));
        helper.assertTrue(HatchingGroundPlacer.unusableAt(level, deck.getX(), deck.getY(), deck.getZ()), "a deck over air");
        helper.setBlock(new BlockPos(2, 3, 2), Blocks.STONE);
        helper.assertTrue(!HatchingGroundPlacer.unusableAt(level, deck.getX(), deck.getY(), deck.getZ()),
                "a block within reach below makes it ground");
        helper.setBlock(new BlockPos(5, 2, 5), Blocks.STONE);
        helper.setBlock(new BlockPos(5, 3, 5), Blocks.WATER);
        BlockPos water = helper.absolutePos(new BlockPos(5, 3, 5));
        helper.assertTrue(HatchingGroundPlacer.unusableAt(level, water.getX(), water.getY(), water.getZ()), "water");
        helper.succeed();
    }

    /** The search's two ways of finding nothing are told apart, for the warning. */
    @GameTest(template = "empty8")
    public static void theLastStepEndsInNoSiteOrItsLimit(GameTestHelper helper) {
        BlockPos spawn = helper.absolutePos(BlockPos.ZERO);
        SiteSearch.Rule never = (x, z, heights, fluid) -> false;
        HatchingGroundPlacer.Search none = HatchingGroundPlacer.findSite(helper.getLevel(), spawn, never, 52, 60_000L);
        helper.assertTrue(none.ground() == null && !none.timedOut(), "every candidate tried: no site, no timeout");
        HatchingGroundPlacer.Search out = HatchingGroundPlacer.findSite(helper.getLevel(), spawn, never, 52, 0L);
        helper.assertTrue(out.ground() == null && out.timedOut(), "a spent limit reads as timed out");
        helper.succeed();
    }

    /**
     * A world with no site plays on: the search leaves the first-start flag set, Shelf 9 unplaced, spawn and its radius
     * where they were, and a newborn joining that world gets a Register line, an Eggshell and a first-hour ticket at P00
     * with no Shelf 9 failure logged. The flags go back before the method returns, so no other test sees them.
     */
    @GameTest(template = "empty8", batch = "shelf9_skip")
    public static void aWorldWithNoSitePlaysOn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerLevel overworld = level.getServer().overworld();
        HatchingGroundData data = HatchingGroundData.get(level.getServer());
        boolean placed = data.placed;
        boolean firstStartDone = data.firstStartDone;
        BlockPos spawn = overworld.getSharedSpawnPos();
        int radius = overworld.getGameRules().getInt(GameRules.RULE_SPAWN_RADIUS);
        boolean failedBefore = StoryCore.hasFailed("shelf9.");
        try {
            data.placed = false;
            data.firstStartDone = true;
            SiteSearch.Rule never = (x, z, heights, fluid) -> false;
            helper.assertTrue(HatchingGroundPlacer.settleFirstStart(overworld, never, 52, 60_000L) == null, "no site");
            helper.assertTrue(HatchingGroundPlacer.settleFirstStart(overworld, never, 52, 0L) == null, "limit spent");
            helper.assertTrue(data.firstStartDone, "the first-start flag stays set");
            helper.assertTrue(!data.placed, "nothing was placed");
            helper.assertTrue(overworld.getSharedSpawnPos().equals(spawn), "spawn stayed");
            helper.assertTrue(overworld.getGameRules().getInt(GameRules.RULE_SPAWN_RADIUS) == radius, "radius stayed");

            ServerPlayer newborn = MockPlayers.inLevel(helper);
            newborn.removeData(FirstHourData.FIRST_HOUR.get());
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(newborn));
            helper.assertTrue(HatchRegister.lineOf(newborn) > 0, "the Register still takes their name");
            helper.assertTrue(Eggshell.isGiven(newborn), "the Eggshell still comes");
            helper.assertTrue(FirstHourData.ticket(newborn).step == TicketState.P00, "the ticket starts at P00");
            helper.assertTrue(failedBefore || !StoryCore.hasFailed("shelf9."), "a Shelf 9 failure was logged");
        } finally {
            data.placed = placed;
            data.firstStartDone = firstStartDone;
        }
        helper.succeed();
    }

    private static int distance2(BlockPos from, int x, int z) {
        int dx = x - from.getX();
        int dz = z - from.getZ();
        return dx * dx + dz * dz;
    }

    private static BlockPos placeTemplate(GameTestHelper helper, List<StructureTemplate.StructureBlockInfo> signs) {
        ServerLevel level = helper.getLevel();
        StructureTemplate template = level.getStructureManager().get(HatchingGroundPlacer.TEMPLATE).orElseThrow();
        BlockPos origin = helper.absolutePos(new BlockPos(0, 40, 0));
        StructurePlaceSettings settings = new StructurePlaceSettings();
        template.placeInWorld(level, origin, origin, settings, RandomSource.create(9), Block.UPDATE_CLIENTS);
        signs.addAll(template.filterBlocks(origin, settings, Blocks.SPRUCE_WALL_SIGN));
        signs.addAll(template.filterBlocks(origin, settings, Blocks.SPRUCE_SIGN));
        return origin;
    }

    private static String role(StructureTemplate.StructureBlockInfo info) {
        return info.nbt() == null ? "" : info.nbt().getString("cc_role");
    }

    /**
     * A live placement never locates on a tick: the Ways signs read "surveying" and the survey waits in the saved data;
     * the next start runs it and writes the signs.
     */
    @GameTest(template = "empty8", timeoutTicks = 400)
    public static void aLivePlacementSurveysAtTheNextStart(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<StructureTemplate.StructureBlockInfo> signs = new ArrayList<>();
        BlockPos origin = placeTemplate(helper, signs);
        HatchingGroundData scratch = new HatchingGroundData();
        for (StructureTemplate.StructureBlockInfo info : signs) {
            String role = role(info);
            if (role.startsWith("ways_")) {
                scratch.waysSigns["nesw".indexOf(role.charAt(5))] = info.pos();
            }
        }
        scratch.placed = true;
        scratch.dimension = level.dimension().location().toString();
        FourWays.markPending(level, origin.offset(10, 4, 10), scratch);
        helper.assertTrue(scratch.waysPending, "the survey waits");
        for (int side = 0; side < 4; side++) {
            helper.assertTrue(waysKey(level, scratch.waysSigns[side]).endsWith("ways.surveying.thing"),
                    "Ways sign " + side + " reads surveying");
        }
        helper.assertTrue(FourWays.runPending(level.getServer(), scratch), "the next start runs it");
        helper.assertTrue(!scratch.waysPending, "and clears it");
        helper.assertTrue(FourWays.VERSION.equals(scratch.waysVersion), "the signs carry this version's survey");
        for (int side = 0; side < 4; side++) {
            helper.assertTrue(!waysKey(level, scratch.waysSigns[side]).endsWith("ways.surveying.thing"),
                    "Ways sign " + side + " no longer reads surveying");
        }
        helper.assertTrue(!FourWays.runPending(level.getServer(), scratch), "a second start has nothing to run");
        helper.succeed();
    }

    /** Signs a 0.10.0 start wrote are surveyed once more at the first 0.10.1 start (the boteco's way), then never. */
    @GameTest(template = "empty8", timeoutTicks = 400)
    public static void olderSignsAreResurveyedOnce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<StructureTemplate.StructureBlockInfo> signs = new ArrayList<>();
        BlockPos origin = placeTemplate(helper, signs);
        HatchingGroundData scratch = new HatchingGroundData();
        for (StructureTemplate.StructureBlockInfo info : signs) {
            String role = role(info);
            if (role.startsWith("ways_")) {
                scratch.waysSigns["nesw".indexOf(role.charAt(5))] = info.pos();
            }
        }
        scratch.placed = true;
        scratch.origin = origin;
        scratch.dimension = level.dimension().location().toString();
        scratch.waysVersion = "0.10.0";
        helper.assertTrue(FourWays.runPending(level.getServer(), scratch), "0.10.0 signs are surveyed again");
        helper.assertTrue(FourWays.VERSION.equals(scratch.waysVersion), "now at " + FourWays.VERSION);
        helper.assertTrue(!FourWays.runPending(level.getServer(), scratch), "and only once");
        FourWays.queueResurvey(scratch);
        helper.assertTrue(scratch.waysPending, "a new boteco or Lodge queues one more");
        helper.assertTrue(FourWays.runPending(level.getServer(), scratch), "run at the next start");
        helper.succeed();
    }

    private static String waysKey(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof SignBlockEntity sign)) {
            return "";
        }
        return sign.getFrontText().getMessage(1, false).getContents() instanceof TranslatableContents t ? t.getKey() : "";
    }

    /**
     * "four signs filled; four locates during first start, log the startup delay": the Four Ways run against a
     * placed template and every Ways sign carries four rows.
     */
    @GameTest(template = "empty8", timeoutTicks = 400)
    public static void fourWaysSignsFilled(GameTestHelper helper) {
        if (!Needs.mods(helper, "fourWaysSignsFilled", "ctov", "t_and_t")) {
            return;
        }
        ServerLevel level = helper.getLevel();
        List<StructureTemplate.StructureBlockInfo> signs = new ArrayList<>();
        BlockPos origin = placeTemplate(helper, signs);
        HatchingGroundData scratch = new HatchingGroundData();
        for (StructureTemplate.StructureBlockInfo info : signs) {
            String role = role(info);
            if (role.startsWith("ways_")) {
                scratch.waysSigns["nesw".indexOf(role.charAt(5))] = info.pos();
            }
        }
        FourWays.locateAndWrite(level, origin.offset(10, 4, 10), scratch);
        for (int side = 0; side < 4; side++) {
            BlockPos pos = scratch.waysSigns[side];
            helper.assertTrue(pos != null, "Ways sign " + side + " found by its role");
            BlockEntity be = level.getBlockEntity(pos);
            helper.assertTrue(be instanceof SignBlockEntity, "a sign at " + pos);
            Component heading = ((SignBlockEntity) be).getFrontText().getMessage(0, false);
            Component comment = ((SignBlockEntity) be).getFrontText().getMessage(3, false);
            helper.assertTrue(!heading.getString().isEmpty() && !comment.getString().isEmpty(), "Ways sign " + side + " filled");
        }
        helper.assertTrue(FourWays.VERSION.equals(scratch.waysVersion), "the re-locate flag is written");
        helper.succeed();
    }

    /**
     * "Shelf 10's crawlspace opens and its label reads": the ghost panel stands over a 2-high crawlspace and the label
     * sign carries its translate keys (both languages are the client's to check). Also: the straw is lit everywhere
     * (no spawns inside), and the Register's eight signs are where the code looks for them.
     */
    @GameTest(template = "empty8", timeoutTicks = 200)
    public static void shelfTenAndTheInsideOfTheShell(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<StructureTemplate.StructureBlockInfo> signs = new ArrayList<>();
        BlockPos origin = placeTemplate(helper, signs);
        BlockPos panel = origin.offset(6, 3, 14);
        ResourceLocation ghost = BuiltInRegistries.BLOCK.getKey(level.getBlockState(panel).getBlock());
        if (ModList.get().isLoaded("secretroomsmod")) {
            helper.assertTrue("secretroomsmod:ghost_block".equals(ghost.toString()), "the ghost panel, found " + ghost);
        }
        helper.assertTrue(level.getBlockState(panel.below()).isAir() && level.getBlockState(panel.below(2)).isAir(),
                "the crawlspace under the panel");
        int register = 0;
        boolean label = false;
        for (StructureTemplate.StructureBlockInfo info : signs) {
            String role = role(info);
            if (role.startsWith("register_")) {
                register++;
            }
            // filterBlocks(origin, ...) gives world positions already (the origin is added), as the placer reads them
            if ("shelf10".equals(role) && level.getBlockEntity(info.pos()) instanceof SignBlockEntity sign) {
                label = sign.getFrontText().getMessage(0, false).getContents()
                        instanceof net.minecraft.network.chat.contents.TranslatableContents t
                        && t.getKey().equals("orespawn_integrations.shelf9.shelf10.1");
            }
        }
        helper.assertTrue(register == 8, "eight Register signs");
        helper.assertTrue(label, "the Shelf 10 label is a translate key (roles: " + signs.stream().map(ShelfNineTests::role)
                .filter(r -> r.startsWith("shelf")).toList() + ", " + signs.stream().filter(i -> "shelf10".equals(role(i)))
                .map(i -> level.getBlockState(i.pos()) + " BE " + level.getBlockEntity(i.pos())).toList() + ")");
        helper.succeedWhen(() -> {
            for (int x = 6; x <= 13; x++) {
                for (int z = 6; z <= 13; z++) {
                    BlockPos above = origin.offset(x, 5, z);
                    if (level.getBlockState(above).isAir()) {
                        helper.assertTrue(level.getBrightness(LightLayer.BLOCK, above) > 0, "dark straw at " + above);
                    }
                }
            }
        });
    }

    /** The "too close" refusal's reader: a chest 5 blocks away is found at 5, and nothing past the radius. */
    @GameTest(template = "empty8")
    public static void tooCloseFindsAHome(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos centre = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.setBlock(new BlockPos(6, 1, 1), Blocks.CHEST);
        int near = CcCommand.nearestHome(level, centre, 64);
        helper.assertTrue(near >= 0 && near <= 5, "chest found at " + near);
        helper.succeed();
    }

    /** "A /cc subcommand with no function says so and does nothing": the command returns 0. */
    @GameTest(template = "empty8")
    public static void ccWithoutAFunctionDoesNothing(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var source = server.createCommandSourceStack().withSuppressedOutput().withPermission(2);
        int result;
        try {
            result = server.getCommands().getDispatcher().execute("cc no_such_thing place", source);
        } catch (Exception e) {
            result = -1;
        }
        helper.assertTrue(result == 0, "refused, result " + result);
        helper.succeed();
    }
}
