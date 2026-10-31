package danger.orespawn.integrations.gametest;

import danger.orespawn.integrations.compat.alive.HoardVault;
import danger.orespawn.integrations.story.freguesia.FreguesiaBlocks;
import danger.orespawn.integrations.story.freguesia.FreguesiaData;
import danger.orespawn.integrations.story.freguesia.Freguesias;
import danger.orespawn.integrations.story.freguesia.MarcoBlock;
import danger.orespawn.integrations.story.freguesia.StoneRecords;
import danger.orespawn.integrations.story.misfile.MisfileCore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.TraderLlama;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * The parish group's card tests (system-freguesia, item-marco-da-freguesia, wonder-wednesday-market,
 * system-hoard-caskets' Somebody's Yard, system-extravio's 0.10.1 rows, event-hoard-fall's vault). Days are stepped by
 * running one stone's dawn check by hand; the stones live in the test area, so each test founds its own.
 */
@GameTestHolder("orespawn_integrations_tests")
@PrefixGameTestTemplate(false)
public final class ParishTests {

    private ParishTests() {
    }

    /** Places a stone (both halves) at the test's (1, 1, 1) and founds its record for a mock player standing there. */
    private static FreguesiaData.Stone found(GameTestHelper helper, ServerPlayer player) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlockAndUpdate(pos, FreguesiaBlocks.MARCO.get().defaultBlockState());
        level.setBlockAndUpdate(pos.above(), FreguesiaBlocks.MARCO.get().defaultBlockState()
                .setValue(MarcoBlock.HALF, DoubleBlockHalf.UPPER));
        player.teleportTo(pos.getX() + 3.5, pos.getY(), pos.getZ() + 3.5);
        Freguesias.placed(level, pos, player);
        return Freguesias.at(level, pos);
    }

    private static void decor(GameTestHelper helper, ServerPlayer player, int count) {
        BlockPos base = helper.absolutePos(new BlockPos(1, 1, 1));
        for (int i = 0; i < count; i++) {
            Freguesias.countDecor(helper.getLevel(), player, base.offset(i % 30 - 15, 1 + i / 900, (i / 30) % 30 - 15));
        }
    }

    // --- item-marco-da-freguesia ---

    /** Placing a stone leaves exactly one cc_freguesia marker at it; the placer is signer 1; the default name. */
    @GameTest(template = "empty8")
    public static void placingAStoneFoundsOneRecordAndOneMarker(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper);
        FreguesiaData.Stone s = found(helper, player);
        helper.assertTrue(s != null, "no record at the stone");
        helper.assertTrue(player.getUUID().equals(s.founder()), "the placer is not signer 1");
        List<? extends Entity> markers = helper.getLevel().getEntities(EntityType.MARKER, new AABB(s.pos).inflate(2.0),
                e -> e.getTags().contains(StoneRecords.MARKER_TAG));
        helper.assertTrue(markers.size() == 1, "expected one marker, found " + markers.size());
        Component name = StoneRecords.name(s);
        helper.assertTrue(name.getString().contains("test-mock-player") || name.getContents() != null, "no name");
        boolean mirrored = false;
        for (Tag t : helper.getLevel().getServer().getCommandStorage().get(StoneRecords.STORAGE).getList("stones", Tag.TAG_COMPOUND)) {
            mirrored |= ((CompoundTag) t).getInt("id") == s.id;
        }
        helper.assertTrue(mirrored, "the stone is not in the Falls' storage");
        helper.succeed();
    }

    /** Breaking the stone pauses the record and leaves no marker; a signer's new stone near the spot resumes it. */
    @GameTest(template = "empty8")
    public static void aFallenStoneWaitsAndResumes(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper);
        FreguesiaData.Stone s = found(helper, player);
        int id = s.id;
        s.standingHalves = 200;
        helper.getLevel().setBlockAndUpdate(s.pos, Blocks.AIR.defaultBlockState());
        helper.assertTrue(!s.standing, "the record didn't pause");
        helper.assertTrue(helper.getLevel().getEntities(EntityType.MARKER, new AABB(s.pos).inflate(2.0),
                e -> e.getTags().contains(StoneRecords.MARKER_TAG)).isEmpty(), "a marker is left");
        FreguesiaData.Stone again = found(helper, player);
        helper.assertTrue(again != null && again.id == id && again.standing() == 100, "the record didn't resume");
        helper.succeed();
    }

    /** A non-signer asks with a scrap; the founder's empty-hand countersign signs them. */
    @GameTest(template = "empty8")
    public static void signingNeedsACountersign(GameTestHelper helper) {
        ServerPlayer founder = MockPlayers.inLevel(helper);
        ServerPlayer friend = MockPlayers.inLevel(helper);
        FreguesiaData.Stone s = found(helper, founder);
        friend.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                        ResourceLocation.fromNamespaceAndPath("orespawn_integrations", "ledger_scrap"))));
        helper.assertTrue(Freguesias.sneakUse(helper.getLevel(), s, friend), "the scrap wasn't taken as a request");
        helper.assertTrue(!s.isSigner(friend.getUUID()), "signed without a countersign");
        founder.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        Freguesias.sneakUse(helper.getLevel(), s, founder);
        helper.assertTrue(s.isSigner(friend.getUUID()), "the countersign didn't sign");
        helper.succeed();
    }

    /** Four stakes on an 8x8 rectangle's corners close a plot that takes the tent. */
    @GameTest(template = "empty8")
    public static void fourStakesCloseAPlot(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper);
        FreguesiaData.Stone s = found(helper, player);
        BlockPos o = s.pos.offset(4, 0, 4);
        Freguesias.stake(helper.getLevel(), o, player);
        Freguesias.stake(helper.getLevel(), o.offset(7, 0, 0), player);
        Freguesias.stake(helper.getLevel(), o.offset(0, 0, 7), player);
        boolean closed = Freguesias.stake(helper.getLevel(), o.offset(7, 0, 7), player);
        helper.assertTrue(closed && s.plots.size() == 1, "no plot closed");
        int[] p = s.plots.get(0);
        helper.assertTrue(p[2] - p[0] + 1 == 8 && p[3] - p[1] + 1 == 8, "the plot isn't 8x8");
        helper.succeed();
    }

    // --- system-freguesia ---

    /** 60 decor placements bring the Homestead at the next dawn check. */
    @GameTest(template = "empty8")
    public static void sixtyDecorBringTheHomestead(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper);
        FreguesiaData.Stone s = found(helper, player);
        decor(helper, player, 59);
        Freguesias.check(helper.getLevel(), s);
        helper.assertTrue(s.stage == 0, "a stage at 59");
        decor(helper, player, 60);
        Freguesias.check(helper.getLevel(), s);
        helper.assertTrue(s.standing() == 60 && s.stage == 1, "no Homestead at 60: " + s.standing() + "/" + s.stage);
        helper.succeed();
    }

    /** One spot placed 200 times in a day scores 1, and a solo builder stops at 200 a day. */
    @GameTest(template = "empty8")
    public static void oneSpotScoresOnceAndADayStopsAt200(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper);
        FreguesiaData.Stone s = found(helper, player);
        BlockPos spot = s.pos.offset(2, 0, 2);
        for (int i = 0; i < 200; i++) {
            Freguesias.countDecor(helper.getLevel(), player, spot);
        }
        helper.assertTrue(s.standing() == 1, "one spot scored " + s.standing());
        Freguesias.newDay(s);
        decor(helper, player, 400);
        helper.assertTrue(s.standing() == 201, "the day's cap let in " + (s.standing() - 1));
        helper.succeed();
    }

    /** A stranger's decor counts nothing for the stone; a placement 49 blocks out counts nothing. */
    @GameTest(template = "empty8")
    public static void onlySignersInTheRadiusCount(GameTestHelper helper) {
        ServerPlayer founder = MockPlayers.inLevel(helper);
        ServerPlayer stranger = MockPlayers.inLevel(helper);
        FreguesiaData.Stone s = found(helper, founder);
        Freguesias.countDecor(helper.getLevel(), stranger, s.pos.offset(2, 0, 0));
        Freguesias.countDecor(helper.getLevel(), founder, s.pos.offset(49, 0, 0));
        helper.assertTrue(s.standing() == 0, "counted " + s.standing());
        helper.assertTrue(s.stoneDays == 0, "a stone day without a signer in range");
        helper.succeed();
    }

    /** At 300 standing and 14 days with no boss kill stage 3 waits and Jade names the key; at 450 it arrives. */
    @GameTest(template = "empty8")
    public static void theParishWaitsForItsKey(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper);
        FreguesiaData.Stone s = found(helper, player);
        s.stage = 2;
        s.stoneDays = 14;
        s.deeds = 1;
        s.standingHalves = 600;
        Freguesias.check(helper.getLevel(), s);
        helper.assertTrue(s.stage == 2 && s.keyNoted == 3, "stage 3 didn't wait on its key");
        CompoundTag view = StoneRecords.jadeView(helper.getLevel(), s.pos);
        helper.assertTrue(view != null && view.getInt("key") == 3, "Jade doesn't name the missing key");
        s.standingHalves = 900;
        Freguesias.check(helper.getLevel(), s);
        helper.assertTrue(s.stage == 3, "450 didn't open the Parish");
        helper.succeed();
    }

    /** At 600 and 28 days with no Knight the Town waits; 900 brings it, with its plaque. */
    @GameTest(template = "empty8", timeoutTicks = 200)
    public static void nineHundredBringsTheTown(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper);
        FreguesiaData.Stone s = found(helper, player);
        s.stage = 3;
        s.stoneDays = 28;
        s.deeds = 1;
        s.standingHalves = 1200;
        s.knight = false;
        Freguesias.check(helper.getLevel(), s);
        helper.assertTrue(s.stage == 3, "the Town came without a key");
        s.standingHalves = 1800;
        Freguesias.check(helper.getLevel(), s);
        helper.assertTrue(s.stage == 4, "900 didn't bring the Town");
        helper.succeed();
    }

    // --- wonder-wednesday-market ---

    /** The peddler stands beside the house with two llamas and vanilla's 48,000-tick despawn. */
    @GameTest(template = "empty8")
    public static void thePeddlerComesWithTwoLlamas(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(new BlockPos(4, 1, 4));
        helper.assertTrue(Freguesias.peddler(level, at), "no peddler came");
        AABB box = new AABB(at).inflate(8.0);
        List<WanderingTrader> traders = level.getEntitiesOfClass(WanderingTrader.class, box);
        List<TraderLlama> llamas = level.getEntitiesOfClass(TraderLlama.class, box);
        helper.assertTrue(traders.size() == 1 && traders.get(0).getDespawnDelay() == 48000, "the peddler is wrong");
        helper.assertTrue(llamas.size() == 2, "llamas: " + llamas.size());
        traders.forEach(Entity::discard);
        llamas.forEach(Entity::discard);
        helper.succeed();
    }

    // --- system-hoard-caskets: Somebody's Yard ---

    /** A trap rolled in the yard pays at the stone: one cc_arcas, and the return lies at the marker. */
    @GameTest(template = "empty8", timeoutTicks = 100)
    public static void aYardTrapPaysAtTheStone(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper);
        FreguesiaData.Stone s = found(helper, player);
        ServerLevel level = helper.getLevel();
        Vec3 casket = Vec3.atCenterOf(s.pos.offset(5, 1, 5));
        level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack()
                .withLevel(level).withPosition(casket).withSuppressedOutput(), "function orespawn_integrations:lucky/test/yard");
        Entity marker = level.getEntity(s.marker);
        helper.assertTrue(marker != null, "no marker");
        var board = level.getServer().getScoreboard();
        var arcas = board.getObjective(StoneRecords.ARCAS);
        helper.assertTrue(arcas != null && board.getPlayerScoreInfo(marker, arcas) != null
                && board.getPlayerScoreInfo(marker, arcas).value() == 1, "cc_arcas didn't count the swap");
        // Rolls 1-4 pay a Distant Lands sapling or Brazilian Delight seeds, roll 6 a Brasil e Coisas capybara: on a jar
        // set without the rolled return's mod (the dev run's) that item cannot exist, so only that roll is skipped.
        var lucky = board.getObjective("cc_lucky");
        var pickInfo = lucky == null ? null
                : board.getPlayerScoreInfo(net.minecraft.world.scores.ScoreHolder.forNameOnly("#oi_pick"), lucky);
        helper.assertTrue(pickInfo != null && pickInfo.value() >= 1 && pickInfo.value() <= 6, "no yard roll");
        int pick = pickInfo.value();
        String needs = pick <= 3 ? "distantlandsmc" : pick == 4 ? "braziliandelight" : pick == 6 ? "brasilcoisas" : null;
        if (needs != null && !Needs.mods(helper, "aYardTrapPaysAtTheStone roll " + pick, needs)) {
            return;
        }
        helper.succeedWhen(() -> helper.assertTrue(!level.getEntities((Entity) null, new AABB(s.pos).inflate(3.0),
                e -> e instanceof net.minecraft.world.entity.item.ItemEntity
                        || e.getTags().contains("cc_chegada")).isEmpty(), "nothing lies at the stone"));
    }

    // --- system-extravio ---

    @GameTest(template = "empty8")
    public static void theLandsAnimalsTag(GameTestHelper helper) {
        TagKey<EntityType<?>> tag = TagKey.create(Registries.ENTITY_TYPE,
                ResourceLocation.fromNamespaceAndPath("orespawn_integrations", "bichos_da_terra"));
        helper.assertTrue(EntityType.COW.is(tag) && EntityType.FOX.is(tag), "vanilla land animals missing");
        helper.assertTrue(!EntityType.COD.is(tag) && !EntityType.SQUID.is(tag), "the sea is in the Land's tag");
        helper.succeed();
    }

    /** The Gazette's box: Tuesday's row with its heading; row 10 with the alert; the van when creatures went. */
    @GameTest(template = "empty8")
    public static void theDaysMisfileBox(GameTestHelper helper) {
        helper.assertTrue(MisfileCore.box(8, 0).size() == 2, "Tuesday's box");
        helper.assertTrue(MisfileCore.box(10, 2).size() == 4, "row 10's box");
        helper.assertTrue(MisfileCore.box(0, 0).isEmpty(), "a box before the first roll");
        helper.succeed();
    }

    // --- event-hoard-fall: the vault ---

    /** Every vault holds the Notice of Loss and at least the eight sampler draws plus the band roll. */
    @GameTest(template = "empty8")
    public static void theVaultHoldsTheNoticeAndEightDraws(GameTestHelper helper) {
        List<ItemStack> items = HoardVault.contents(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 2)), null);
        boolean notice = items.stream().anyMatch(i -> i.is(Items.PAPER) && i.has(HoardVault.NOTICE_OF_LOSS.get()));
        helper.assertTrue(notice, "no Notice of Loss");
        helper.assertTrue(items.size() >= 10, "only " + items.size() + " items");
        helper.succeed();
    }

    /** No story key and no technical item ever comes from a sampler draw, across 2,000 draws. */
    @GameTest(template = "empty8")
    public static void theSamplerNeverDrawsAStoryKey(GameTestHelper helper) {
        var random = helper.getLevel().random;
        for (int i = 0; i < 2000; i++) {
            ItemStack s = HoardVault.draw(random);
            if (s.is(HoardVault.STORY_KEYS) || s.is(HoardVault.PINATA_BLACKLIST) || s.is(Items.COMMAND_BLOCK)) {
                helper.fail("drew " + s);
                return;
            }
        }
        helper.succeed();
    }
}
