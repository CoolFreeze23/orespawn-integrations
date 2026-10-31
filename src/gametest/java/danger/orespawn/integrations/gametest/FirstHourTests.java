package danger.orespawn.integrations.gametest;

import danger.orespawn.integrations.compat.alive.FallSafety;
import danger.orespawn.integrations.story.firsthour.EggLabel;
import danger.orespawn.integrations.story.firsthour.FallRecord;
import danger.orespawn.integrations.story.firsthour.FieldOdete;
import danger.orespawn.integrations.story.firsthour.FirstHourData;
import danger.orespawn.integrations.story.firsthour.FirstHourUtil;
import danger.orespawn.integrations.story.firsthour.GoldPin;
import danger.orespawn.integrations.story.firsthour.PriorityTicket;
import danger.orespawn.integrations.story.firsthour.RoyalSummons;
import danger.orespawn.integrations.story.firsthour.TicketState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * The first hour's card test lines a server can check without a client: the ticket at a new join, the Summons'
 * pages and its addressed drop, the label after an Orb re-pick, the Fall's safety radius and the opened-crate rule,
 * the consignee guard, the Field Odete's clock greetings and pin order, and the one gold pin. The timing rules
 * themselves are JUnit ({@code TicketRulesTest}); the Fall's landing cone and the flyover's altitude need a full
 * pack and a client, and run in the milestone's script.
 */
@GameTestHolder("orespawn_integrations_tests")
@PrefixGameTestTemplate(false)
public final class FirstHourTests {

    private FirstHourTests() {
    }

    /** A player with no play time joins: they get a fresh ticket at P00, nothing handed yet. */
    @GameTest(template = "empty8")
    public static void newJoinStartsAtP00(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper);
        player.removeData(FirstHourData.FIRST_HOUR.get());
        PriorityTicket.start(player);
        TicketState s = FirstHourData.ticket(player);
        helper.assertTrue(s.step == TicketState.P00, "a new join starts at P00");
        helper.assertTrue(!s.used(TicketState.P00), "the origin screen is still open");
        helper.assertTrue(!s.flag(TicketState.FLAG_VETERAN), "a new join is not a veteran");
        helper.succeed();
    }

    /** The Summons has three pages, the letter custom data and its addressee; page 2 reads the label's P.S. */
    @GameTest(template = "empty8")
    public static void summonsHasThreePagesAndTheOriginPostscript(GameTestHelper helper) {
        ServerPlayer saci = MockPlayers.inLevel(helper);
        saci.addTag(EggLabel.ORIGIN_TAG + "saci");
        ItemStack book = RoyalSummons.build(saci);
        WrittenBookContent content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
        helper.assertTrue(content != null && content.pages().size() == 3, "three pages");
        helper.assertTrue(RoyalSummons.isSummonsFor(book, saci), "addressed to its reader");
        String page2 = content.pages().get(1).raw().toString();
        helper.assertTrue(page2.contains("princes_letter.ps.origin.saci"), "the Saci's own P.S.");
        helper.assertTrue(page2.contains("princes_letter.pps_first_fall"), "the First Fall's P.P.S.");
        helper.assertTrue(content.pages().get(2).raw().toString().contains("page3_form_7b"), "page 3 is Form 7-B");
        ServerPlayer other = MockPlayers.inLevel(helper);
        helper.assertTrue(!RoyalSummons.isSummonsFor(book, other), "not addressed to a friend");
        helper.succeed();
    }

    /** The dropped Summons can only be picked up by its addressee: a friend standing on it cannot. */
    @GameTest(template = "empty8")
    public static void summonsDropIsAddressed(GameTestHelper helper) {
        ServerPlayer to = MockPlayers.inLevel(helper);
        ServerPlayer friend = MockPlayers.inLevel(helper);
        BlockPos at = helper.absolutePos(new BlockPos(3, 1, 3));
        ItemEntity item = new ItemEntity(helper.getLevel(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5,
                RoyalSummons.build(to));
        item.setTarget(to.getUUID());
        item.setNoPickUpDelay();
        helper.getLevel().addFreshEntity(item);
        friend.moveTo(item.getX(), item.getY(), item.getZ());
        item.playerTouch(friend);
        helper.assertTrue(!item.isRemoved() && !RoyalSummons.holdsOwn(to), "the friend could not take it");
        helper.assertTrue(friend.getInventory().items.stream().noneMatch(RoyalSummons::isSummons),
                "nothing in the friend's pockets");
        helper.succeed();
    }

    /** An Orb re-pick from sylvan to golem removes cc_origin.sylvan and sets cc_ogrupo 5; the class is scored. */
    @GameTest(template = "empty8")
    public static void labelFollowsAnOrbRepick(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper);
        EggLabel.apply(player, ResourceLocation.parse("neoorigins:sylvan"),
                ResourceLocation.parse("neoorigins:class_scout"));
        helper.assertTrue(player.getTags().contains("cc_origin.sylvan"), "sylvan tag");
        helper.assertTrue(FirstHourUtil.score(player, EggLabel.GROUP_SCORE, 0) == 4, "sylvan is group 4");
        helper.assertTrue(player.getTags().contains("cc_class.class_scout"), "class tag");
        helper.assertTrue(FirstHourUtil.score(player, EggLabel.CLASS_SCORE, 0) == 8, "scout is class 8");
        EggLabel.apply(player, ResourceLocation.parse("neoorigins:golem"),
                ResourceLocation.parse("neoorigins:class_scout"));
        helper.assertTrue(!player.getTags().contains("cc_origin.sylvan"), "the old origin tag is gone");
        helper.assertTrue(player.getTags().contains("cc_origin.golem"), "golem tag");
        helper.assertTrue(FirstHourUtil.score(player, EggLabel.GROUP_SCORE, 0) == 5, "golem is group 5");
        helper.succeed();
    }

    /** #homely holds beds, chests and furnaces; a Fall never lands within 64 blocks of one. */
    @GameTest(template = "empty8")
    public static void homelyBlocksKeepTheFallAway(GameTestHelper helper) {
        helper.assertTrue(Blocks.RED_BED.defaultBlockState().is(FallSafety.HOMELY), "a bed is homely");
        helper.assertTrue(Blocks.CHEST.defaultBlockState().is(FallSafety.HOMELY), "a chest is homely");
        helper.assertTrue(Blocks.FURNACE.defaultBlockState().is(FallSafety.HOMELY), "a furnace is homely");
        BlockPos chest = new BlockPos(2, 1, 2);
        helper.setBlock(chest, Blocks.CHEST);
        BlockPos abs = helper.absolutePos(chest);
        ServerLevel level = helper.getLevel();
        List<BlockPos> homes = FallSafety.homes(level, abs.getX(), abs.getZ(), 100);
        helper.assertTrue(homes.contains(abs), "the placed chest is found in one pass");
        helper.assertTrue(!FallSafety.farFromHomes(abs.offset(40, 0, 0), homes), "40 blocks off is too close");
        helper.assertTrue(FallSafety.farFromHomes(abs.offset(70, 0, 0), homes), "70 blocks off is wild");
        helper.succeed();
    }

    /** An opened, emptied First Fall crate keeps no Fall away; the same crate with one item in it does. */
    @GameTest(template = "empty8")
    public static void emptiedCrateIsNotAHome(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper);
        BlockPos rel = new BlockPos(4, 1, 4);
        helper.setBlock(rel, Blocks.CHEST);
        BlockPos abs = helper.absolutePos(rel);
        FallRecord record = FallRecord.get(helper.getLevel().getServer());
        FallRecord.Entry entry = record.entry(player.getUUID());
        entry.crate = abs;
        entry.opened = true;
        try {
            List<BlockPos> homes = FallSafety.homes(helper.getLevel(), abs.getX(), abs.getZ(), 16);
            helper.assertTrue(!homes.contains(abs), "an emptied crate is skipped");
            if (helper.getLevel().getBlockEntity(abs) instanceof net.minecraft.world.Container box) {
                box.setItem(0, new ItemStack(net.minecraft.world.item.Items.DIRT));
            }
            homes = FallSafety.homes(helper.getLevel(), abs.getX(), abs.getZ(), 16);
            helper.assertTrue(homes.contains(abs), "a crate with something stored counts as a home");
        } finally {
            record.all().remove(player.getUUID());
        }
        helper.succeed();
    }

    /** A second player's click on an unopened crate is refused; the recipient's opens it. */
    @GameTest(template = "empty8")
    public static void crateRefusesAnyoneButItsConsignee(GameTestHelper helper) {
        ServerPlayer owner = MockPlayers.inLevel(helper);
        ServerPlayer other = MockPlayers.inLevel(helper);
        BlockPos rel = new BlockPos(5, 1, 2);
        helper.setBlock(rel, Blocks.CHEST);
        BlockPos abs = helper.absolutePos(rel);
        FallRecord record = FallRecord.get(helper.getLevel().getServer());
        FallRecord.Entry entry = record.entry(owner.getUUID());
        entry.crate = abs;
        entry.landed = true;
        try {
            if (helper.getLevel().dimension() != net.minecraft.world.level.Level.OVERWORLD) {
                helper.succeed();
                return;
            }
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
            PlayerInteractEvent.RightClickBlock byOther =
                    new PlayerInteractEvent.RightClickBlock(other, InteractionHand.MAIN_HAND, abs, hit);
            NeoForge.EVENT_BUS.post(byOther);
            helper.assertTrue(byOther.isCanceled(), "a friend's click is refused");
            helper.assertTrue(!entry.opened, "and opens nothing");
            PlayerInteractEvent.RightClickBlock byOwner =
                    new PlayerInteractEvent.RightClickBlock(owner, InteractionHand.MAIN_HAND, abs, hit);
            NeoForge.EVENT_BUS.post(byOwner);
            helper.assertTrue(!byOwner.isCanceled() && entry.opened, "the consignee opens it");
        } finally {
            record.all().remove(owner.getUUID());
        }
        helper.succeed();
    }

    /** Spawned at 07:00, 14:00 and 21:00 she greets with morning, afternoon and evening (cc_turno 0, 1, 2). */
    @GameTest(template = "empty8")
    public static void fieldOdeteGreetsByTheClock(GameTestHelper helper) {
        helper.assertTrue(FieldOdete.turn(1000) == 0, "07:00 is morning");
        helper.assertTrue(FieldOdete.turn(8000) == 1, "14:00 is afternoon");
        helper.assertTrue(FieldOdete.turn(15000) == 2, "21:00 is evening");
        helper.succeed();
    }

    /** Her pin: the nearest listed desk within 2,000 blocks, none beyond it. */
    @GameTest(template = "empty8")
    public static void fieldOdetePinsTheNearestListedDesk(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ResourceLocation id = FirstHourUtil.oi("registro");
        CompoundTag saved = level.getServer().getCommandStorage().get(id).copy();
        BlockPos from = helper.absolutePos(BlockPos.ZERO);
        try {
            CompoundTag tag = new CompoundTag();
            ListTag desks = new ListTag();
            desks.add(desk(from.offset(1500, 0, 0), level));
            desks.add(desk(from.offset(300, 0, 0), level));
            tag.put("desks", desks);
            level.getServer().getCommandStorage().set(id, tag);
            BlockPos pin = FieldOdete.registryPin(level, from);
            helper.assertTrue(pin != null && pin.getX() == from.getX() + 300, "the nearest desk wins");
            desks.clear();
            desks.add(desk(from.offset(2500, 0, 0), level));
            level.getServer().getCommandStorage().set(id, tag);
            helper.assertTrue(FieldOdete.registryPin(level, from) == null, "a desk past 2,000 blocks is not pinned");
        } finally {
            level.getServer().getCommandStorage().set(id, saved);
        }
        helper.succeed();
    }

    private static CompoundTag desk(BlockPos pos, ServerLevel level) {
        CompoundTag d = new CompoundTag();
        d.putIntArray("pos", new int[]{pos.getX(), pos.getY(), pos.getZ()});
        d.putString("dim", level.dimension().location().toString());
        return d;
    }

    /** Only one gold pin is ever live: the Registry pin replaces the Fall's. */
    @GameTest(template = "empty8")
    public static void oneGoldPinAtATime(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper);
        player.setData(FirstHourData.FIRST_HOUR.get(), TicketState.fresh());
        GoldPin.set(player, TicketState.PIN_FALL, 10, 64, 10);
        helper.assertTrue(FirstHourData.ticket(player).pin == TicketState.PIN_FALL, "the Fall pin is up");
        GoldPin.set(player, TicketState.PIN_REGISTRY, 500, 64, 500);
        TicketState s = FirstHourData.ticket(player);
        helper.assertTrue(s.pin == TicketState.PIN_REGISTRY && s.pinX == 500, "the Registry pin replaced it");
        GoldPin.clearIf(player, TicketState.PIN_FALL);
        helper.assertTrue(FirstHourData.ticket(player).pin == TicketState.PIN_REGISTRY, "a stale Fall clear does nothing");
        helper.succeed();
    }
}
