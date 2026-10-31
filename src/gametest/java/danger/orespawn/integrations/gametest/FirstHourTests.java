package danger.orespawn.integrations.gametest;

import danger.orespawn.integrations.compat.alive.FallSafety;
import danger.orespawn.integrations.compat.alive.FirstFall;
import danger.orespawn.integrations.compat.alive.PrincesLetter;
import danger.orespawn.integrations.story.firsthour.EggLabel;
import danger.orespawn.integrations.story.firsthour.FallRecord;
import danger.orespawn.integrations.story.firsthour.FieldOdete;
import danger.orespawn.integrations.story.firsthour.FirstHourData;
import danger.orespawn.integrations.story.firsthour.FirstHourUtil;
import danger.orespawn.integrations.story.firsthour.GoldPin;
import danger.orespawn.integrations.story.firsthour.PriorityTicket;
import danger.orespawn.integrations.story.firsthour.RoyalSummons;
import danger.orespawn.integrations.story.firsthour.TicketState;
import danger.orespawn.integrations.story.shelf9.ShelfNine;
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
import net.minecraft.world.entity.player.Player;
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

import java.util.ArrayList;
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

    /**
     * A player with the shipped join Guide's flag but no play time (their stats file is missing) is a veteran to the
     * ticket, as they are to the Register: a closed ticket, never P00.
     */
    @GameTest(template = "empty8")
    public static void guideFlagWithoutPlayTimeIsAVeteran(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper);
        player.removeData(FirstHourData.FIRST_HOUR.get());
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        persisted.putBoolean("orespawn_integrations:guide_given", true);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        helper.assertTrue(ShelfNine.playedBefore0100(player), "the Guide flag alone makes a veteran");
        PriorityTicket.start(player);
        TicketState s = FirstHourData.ticket(player);
        helper.assertTrue(s.flag(TicketState.FLAG_VETERAN), "the ticket sees the veteran");
        helper.assertTrue(s.step != TicketState.P00, "a veteran never starts at P00");
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
        // The distances are measured from this test's own chest: the scan also finds neighbouring tests' beds and
        // chests, which move as tests are added.
        List<BlockPos> own = List.of(abs);
        helper.assertTrue(!FallSafety.farFromHomes(abs.offset(40, 0, 0), own), "40 blocks off is too close");
        helper.assertTrue(FallSafety.farFromHomes(abs.offset(70, 0, 0), own), "70 blocks off is wild");
        helper.succeed();
    }

    /**
     * The crate fills as the card lists it for a newcomer with no twin yet (ITEM 3's glowstone, ITEM 5's slip), and for
     * nobody in particular: the twin's table once threw for want of an entity and stopped the landing (and the Odete).
     */
    @GameTest(template = "empty8")
    public static void theFirstFallCrateFills(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper);
        BlockPos rel = new BlockPos(3, 1, 3);
        helper.setBlock(rel, Blocks.CHEST);
        BlockPos abs = helper.absolutePos(rel);
        FirstFall.fill(helper.getLevel(), abs, player, false);
        helper.assertTrue(helper.getLevel().getBlockEntity(abs) instanceof net.minecraft.world.Container box
                && box.countItem(net.minecraft.world.item.Items.GLOWSTONE) == 4, "4 glowstone (ITEM 3)");
        helper.assertTrue(((net.minecraft.world.Container) helper.getLevel().getBlockEntity(abs))
                .countItem(net.minecraft.world.item.Items.SPYGLASS) == 1, "the spyglass");
        helper.assertTrue(((net.minecraft.world.Container) helper.getLevel().getBlockEntity(abs))
                .countItem(net.minecraft.world.item.Items.PAPER) >= 1, "ITEM 5's slip for a player with no twin");
        BlockPos other = helper.absolutePos(new BlockPos(5, 1, 3));
        helper.setBlock(new BlockPos(5, 1, 3), Blocks.CHEST);
        FirstFall.fill(helper.getLevel(), other, null, false);
        helper.assertTrue(((net.minecraft.world.Container) helper.getLevel().getBlockEntity(other))
                .countItem(net.minecraft.world.item.Items.GLOWSTONE) == 4, "a crate for nobody in particular fills too");
        helper.succeed();
    }

    /**
     * "dropped a letter at my feet": a player under a roof still gets the Summons, which starts its fall just under the
     * roof instead of landing on it; in the open it starts high above them.
     */
    @GameTest(template = "empty8")
    public static void theLetterFallsUnderARoof(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper);
        BlockPos feet = helper.absolutePos(new BlockPos(3, 1, 3));
        player.teleportTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
        ServerLevel level = helper.getLevel();
        helper.setBlock(new BlockPos(3, 5, 3), Blocks.STONE);
        PrincesLetter.drop(level, player, feet.getY() + 40.0);
        List<ItemEntity> letters = level.getEntitiesOfClass(ItemEntity.class,
                new net.minecraft.world.phys.AABB(feet).inflate(1.0, 60.0, 1.0),
                e -> e.getItem().is(net.minecraft.world.item.Items.WRITTEN_BOOK));
        helper.assertTrue(letters.size() == 1, "one Summons dropped");
        helper.assertTrue(letters.get(0).getY() < feet.getY() + 4.0, "it starts under the roof, not on it");
        letters.get(0).discard();
        helper.setBlock(new BlockPos(3, 5, 3), Blocks.AIR);
        PrincesLetter.drop(level, player, feet.getY() + 40.0);
        List<ItemEntity> open = level.getEntitiesOfClass(ItemEntity.class,
                new net.minecraft.world.phys.AABB(feet).inflate(1.0, 60.0, 1.0),
                e -> e.getItem().is(net.minecraft.world.item.Items.WRITTEN_BOOK));
        // The test arena has its own ceiling above the template, so "the open" here means: above the removed roof.
        helper.assertTrue(open.size() == 1 && open.get(0).getY() > feet.getY() + 4.0,
                "without the roof it starts above where the roof was (at " + (open.isEmpty() ? "none"
                        : String.format("%.1f", open.get(0).getY() - feet.getY())) + " over the feet)");
        open.forEach(ItemEntity::discard);
        helper.succeed();
    }

    /**
     * "a new account standing still on Shelf 9 at night gets its Fall by the second try, 24-48 blocks out and in view"
     * (event-first-fall, a newcomer near spawn): the second try's ring finds open ground the player can see.
     */
    @GameTest(template = "empty8", timeoutTicks = 200)
    public static void aNewcomerGetsAFallInViewOnTheSecondTry(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = MockPlayers.inLevel(helper);
        // Outside the test's own barrier box (it would block every line of sight), on the flat ground beside it.
        BlockPos feet = helper.absolutePos(new BlockPos(-12, 0, -12));
        player.teleportTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
        List<Long> forced = forceAround(level, feet);
        try {
            // Other tests' chests and beds stand within reach and are homes too; the clearances themselves are the
            // next test's, so this one reads the ring with no homes at all.
            List<FallSafety.Home> homes = List.of();
            BlockPos spot = null;
            FallSafety.Tally tally = new FallSafety.Tally();
            for (int i = 0; i < 5 && spot == null; i++) {
                spot = FallSafety.ringInView(level, player, homes, level.random, tally);
            }
            helper.assertTrue(spot != null, "the ring found a spot (" + tally + ")");
            double d = Math.sqrt(Math.pow(spot.getX() + 0.5 - player.getX(), 2) + Math.pow(spot.getZ() + 0.5 - player.getZ(), 2));
            helper.assertTrue(d >= 23.0 && d <= 49.0, "24-48 blocks out, got " + (int) d);
        } finally {
            release(level, forced);
        }
        helper.succeed();
    }

    /**
     * "a second account's Fall that night still keeps 64 from the first player's bed": a newcomer's own respawn point and
     * the homely blocks they just placed need 24 blocks for their own Fall; for anyone else they keep the full 64.
     */
    @GameTest(template = "empty8", timeoutTicks = 200)
    public static void aNewcomersOwnHomeNeeds24AndAFriendsKeeps64(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer first = MockPlayers.inLevel(helper);
        ServerPlayer second = MockPlayers.inLevel(helper);
        BlockPos bed = helper.absolutePos(new BlockPos(2, 1, 2));
        first.setRespawnPosition(level.dimension(), bed, 0.0F, true, false);
        BlockPos chest = helper.absolutePos(new BlockPos(5, 1, 5));
        helper.setBlock(new BlockPos(5, 1, 5), Blocks.CHEST);
        FallRecord record = FallRecord.get(level.getServer());
        record.entry(first.getUUID()).placed(chest, level.getGameTime());
        try {
            List<FallSafety.Home> mine = FallSafety.homesFor(level, first, bed.getX(), bed.getZ(), 120);
            List<FallSafety.Home> theirs = FallSafety.homesFor(level, second, bed.getX(), bed.getZ(), 120);
            helper.assertTrue(mine.contains(new FallSafety.Home(bed, FallSafety.NEWCOMER_RADIUS)),
                    "the newcomer's own bed needs 24 for their own Fall");
            helper.assertTrue(mine.contains(new FallSafety.Home(chest, FallSafety.NEWCOMER_RADIUS)),
                    "a chest they placed today needs 24 too");
            helper.assertTrue(theirs.contains(new FallSafety.Home(bed, FallSafety.HOME_RADIUS)),
                    "the first player's bed keeps 64 from a second player's Fall");
            helper.assertTrue(theirs.contains(new FallSafety.Home(chest, FallSafety.HOME_RADIUS)),
                    "and so does the first player's chest");
            BlockPos away = bed.offset(40, 0, 0);
            second.teleportTo(away.getX() + 0.5, away.getY(), away.getZ() + 0.5);
            List<Long> forced = forceAround(level, away);
            try {
                List<FallSafety.Home> near = FallSafety.homesFor(level, second, second.getX(), second.getZ(), 120);
                for (int i = 0; i < 5; i++) {
                    BlockPos spot = FallSafety.ringInView(level, second, near, level.random, new FallSafety.Tally());
                    if (spot != null) {
                        helper.assertTrue(spot.distSqr(new BlockPos(bed.getX(), spot.getY(), bed.getZ())) >= 64 * 64,
                                "a second account's Fall lands 64 or more from the first player's bed");
                    }
                }
            } finally {
                release(level, forced);
            }
        } finally {
            record.find(first.getUUID()).placedHomely.clear();
        }
        helper.succeed();
    }

    /** Forces the chunks around {@code centre} that are not forced yet (other tests' stay theirs); returns them. */
    private static List<Long> forceAround(ServerLevel level, BlockPos centre) {
        List<Long> mine = new ArrayList<>();
        int cx = centre.getX() >> 4;
        int cz = centre.getZ() >> 4;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                long key = net.minecraft.world.level.ChunkPos.asLong(cx + dx, cz + dz);
                if (!level.getForcedChunks().contains(key)) {
                    level.setChunkForced(cx + dx, cz + dz, true);
                    mine.add(key);
                }
            }
        }
        return mine;
    }

    private static void release(ServerLevel level, List<Long> forced) {
        for (long key : forced) {
            level.setChunkForced(net.minecraft.world.level.ChunkPos.getX(key), net.minecraft.world.level.ChunkPos.getZ(key),
                    false);
        }
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
