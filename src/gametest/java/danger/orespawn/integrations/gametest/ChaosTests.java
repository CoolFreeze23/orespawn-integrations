package danger.orespawn.integrations.gametest;

import com.hbm.hazard.HazardSystem;

import danger.orespawn.entity.EntityCage;
import danger.orespawn.integrations.compat.hbm.HazardExemptions;
import danger.orespawn.integrations.story.chaos.BossCageRefusal;
import danger.orespawn.integrations.story.chaos.BossSpawnerGuard;
import danger.orespawn.integrations.story.chaos.CasketCore;
import danger.orespawn.integrations.story.chaos.LoanerWeapons;
import danger.orespawn.integrations.story.chaos.RoyalSeal;
import dev.shadowsoffire.apothic_spawners.ASObjects;
import dev.shadowsoffire.apothic_spawners.modifiers.SpawnerModifier;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The chaos group's card tests (system-hoard-caskets, item-the-larder, item-royal-diet, npc-mobzilla and the
 * boss-spawner part of system-lancamentos-em-duplicidade). The casket censuses run the real root function deep under
 * the world, at Y -120 as the card's load-order line says, where nothing a roll summons or places touches the test
 * area; every entity they leave there is counted in the same tick and then removed.
 */
@GameTestHolder("orespawn_integrations_tests")
@PrefixGameTestTemplate(false)
public final class ChaosTests {

    private static final Set<ResourceLocation> BOSSES = Set.of(
            ResourceLocation.withDefaultNamespace("ender_dragon"), ResourceLocation.withDefaultNamespace("wither"),
            ResourceLocation.withDefaultNamespace("warden"), ResourceLocation.withDefaultNamespace("elder_guardian"),
            ResourceLocation.withDefaultNamespace("giant"), os("the_king"), os("the_queen"), os("godzilla"),
            os("kraken"), os("mothra"));
    private static final Set<ResourceLocation> WHOLE_EGGS = Set.of(
            os("the_king_spawn_egg"), os("the_queen_spawn_egg"), os("godzilla_spawn_egg"),
            ResourceLocation.withDefaultNamespace("wither_spawn_egg"),
            ResourceLocation.withDefaultNamespace("ender_dragon_spawn_egg"),
            ResourceLocation.withDefaultNamespace("warden_spawn_egg"),
            ResourceLocation.withDefaultNamespace("elder_guardian_spawn_egg"));

    private ChaosTests() {
    }

    // ---- helpers

    private static ResourceLocation os(String path) {
        return ResourceLocation.fromNamespaceAndPath("orespawn", path);
    }

    private static Item item(ResourceLocation id) {
        return BuiltInRegistries.ITEM.get(id);
    }

    private static int score(MinecraftServer server, String holder, String objective) {
        Scoreboard board = server.getScoreboard();
        Objective obj = board.getObjective(objective);
        if (obj == null) {
            return Integer.MIN_VALUE;
        }
        ReadOnlyScoreInfo info = board.getPlayerScoreInfo(ScoreHolder.forNameOnly(holder), obj);
        return info == null ? Integer.MIN_VALUE : info.value();
    }

    private static int score(Entity holder, String objective) {
        Scoreboard board = holder.getServer().getScoreboard();
        Objective obj = board.getObjective(objective);
        ReadOnlyScoreInfo info = obj == null ? null : board.getPlayerScoreInfo(holder, obj);
        return info == null ? Integer.MIN_VALUE : info.value();
    }

    private static void setScore(MinecraftServer server, ScoreHolder holder, String objective, int value) {
        Scoreboard board = server.getScoreboard();
        Objective obj = board.getObjective(objective);
        board.getOrCreatePlayerScore(holder, obj).set(value);
    }

    private static void run(CommandSourceStack source, String command) {
        source.getServer().getCommands().performPrefixedCommand(source, command);
    }

    /** A place deep under the test, where a roll's summons fall into the void and its blocks can't be placed. */
    private static Vec3 deep(GameTestHelper helper) {
        BlockPos base = helper.absolutePos(BlockPos.ZERO);
        return new Vec3(base.getX() + 0.5, -120.0, base.getZ() + 0.5);
    }

    private static Marker marker(ServerLevel level, Vec3 at) {
        Marker marker = EntityType.MARKER.create(level);
        marker.moveTo(at.x, at.y, at.z);
        level.addFreshEntity(marker);
        return marker;
    }

    private static CommandSourceStack as(Entity entity) {
        return entity.createCommandSourceStack().withPermission(4).withSuppressedOutput();
    }

    private static List<Entity> around(ServerLevel level, Vec3 at, double r) {
        return level.getEntities((Entity) null, new AABB(at, at).inflate(r), e -> !(e instanceof Player));
    }

    private static void clear(ServerLevel level, Vec3 at) {
        for (Entity e : around(level, at, 6)) {
            e.discard();
        }
    }

    private static void resetCensus(MinecraftServer server) {
        CasketCore.prepare(server);
        for (String h : new String[] {"#oi_ours", "#oi_parts", "#oi_loaners", "#oi_whole", "#oi_refusals"}) {
            setScore(server, ScoreHolder.forNameOnly(h), CasketCore.OBJ_WORK, 0);
        }
    }

    /** Problems seen among what the rolls left at {@code at}: a boss, or a whole boss egg. */
    private static List<String> bossesAndWholeEggs(ServerLevel level, Vec3 at) {
        List<String> found = new ArrayList<>();
        for (Entity e : around(level, at, 6)) {
            ResourceLocation type = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
            if (BOSSES.contains(type)) {
                found.add("boss " + type);
            }
            if (e instanceof ItemEntity drop) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(drop.getItem().getItem());
                if (WHOLE_EGGS.contains(id)) {
                    found.add("whole egg " + id);
                }
            }
        }
        return found;
    }

    private static void award(ServerPlayer player, String id) {
        AdvancementHolder holder = player.getServer().getAdvancements().get(ResourceLocation.parse(id));
        if (holder == null) {
            return;
        }
        for (String criterion : holder.value().criteria().keySet()) {
            player.getAdvancements().award(holder, criterion);
        }
    }

    private static ServerPlayer deepPlayer(GameTestHelper helper, Vec3 at) {
        ServerPlayer player = MockPlayers.inLevel(helper);
        player.setGameMode(GameType.CREATIVE);
        player.getInventory().clearContent();
        // A fresh joiner's first casket rolls the rank-0 table by design (the Eggshell's casket mark); these tests
        // are about the rank a casket reads, so the mark comes off first.
        run(player.createCommandSourceStack().withPermission(4).withSuppressedOutput(),
                "scoreboard players reset @s cc_shell_casket");
        player.teleportTo(at.x, at.y, at.z);
        return player;
    }

    private static int today(ServerLevel level) {
        return (int) (level.getDayTime() / 24000L % 2147483647L);
    }

    // ---- system-hoard-caskets: script line 8, the load order

    /**
     * Card test (2), script line 8: forty root rolls from a marker at Y -120 leave lucky_blocks.random at 10-12 at
     * least once. ly's own root rolls 1..9, so a hit proves our root won the load order (a miss with ours is
     * 0.75^40, about 1 in 100,000).
     */
    @GameTest(template = "empty8")
    public static void rootWinsLoadOrder(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 at = deep(helper);
        resetCensus(level.getServer());
        Marker probe = marker(level, at);
        int hits = 0;
        for (int i = 0; i < 40; i++) {
            run(as(probe), "function lucky_blocks:random/random");
            int roll = score(probe, "lucky_blocks.random");
            helper.assertTrue(roll >= 1 && roll <= 12, "root left " + roll + ", outside 1..12");
            if (roll >= 10) {
                hits++;
            }
        }
        clear(level, at);
        helper.assertTrue(hits > 0, "no roll of 10-12 in 40: ly's own root won the load order");
        helper.succeed();
    }

    /**
     * Card test (3), script line 8's second half: 200 rank-0 breaks give about 50 of our own outcomes (3 in 12), and
     * no boss and no whole boss egg.
     */
    @GameTest(template = "empty8", timeoutTicks = 200)
    public static void rankZeroCensus200(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        Vec3 at = deep(helper);
        resetCensus(server);
        Marker probe = marker(level, at);
        List<String> seen = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            run(as(probe), "function lucky_blocks:random/random");
            seen.addAll(bossesAndWholeEggs(level, at));
            clear(level, at);
            probe = marker(level, at);
        }
        clear(level, at);
        int ours = score(server, "#oi_ours", CasketCore.OBJ_WORK);
        helper.assertTrue(seen.isEmpty(), "rank-0 casket rolled " + seen);
        helper.assertTrue(score(server, "#oi_whole", CasketCore.OBJ_WORK) == 0, "a whole egg was rolled at rank 0");
        helper.assertTrue(ours >= 25 && ours <= 80, "our outcomes in 200 rank-0 breaks: " + ours + " (about 50)");
        helper.succeed();
    }

    /**
     * Script line 9, the chaos floor: 400 rolls of the rank-0 casket give no boss, no whole egg, about 5 egg parts
     * (1 in 80) and about 10 Loaner Berthas (1 in 40).
     */
    @GameTest(template = "empty8", timeoutTicks = 200)
    public static void chaosFloor400(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        Vec3 at = deep(helper);
        resetCensus(server);
        List<String> seen = new ArrayList<>();
        for (int i = 0; i < 400; i++) {
            Marker probe = marker(level, at);
            run(as(probe), "function lucky_blocks:random/random");
            List<String> found = bossesAndWholeEggs(level, at);
            if (!found.isEmpty()) {
                // which root roll (and so which branch) let it through
                found.add("root " + score(server, "#oi_root", "cc_lucky"));
            }
            seen.addAll(found);
            clear(level, at);
        }
        int parts = score(server, "#oi_parts", CasketCore.OBJ_WORK);
        int loaners = score(server, "#oi_loaners", CasketCore.OBJ_WORK);
        helper.assertTrue(seen.isEmpty(), "rank-0 casket rolled " + seen);
        helper.assertTrue(score(server, "#oi_whole", CasketCore.OBJ_WORK) == 0, "a whole egg was rolled at rank 0");
        helper.assertTrue(score(server, "#oi_refusals", CasketCore.OBJ_WORK) == 0, "a Great Refusal at rank 0");
        helper.assertTrue(parts <= 16, "egg parts in 400 rolls: " + parts + " (about 5)");
        helper.assertTrue(loaners >= 2 && loaners <= 25, "Loaner Berthas in 400 rolls: " + loaners + " (about 10)");
        helper.succeed();
    }

    // ---- system-hoard-caskets: the rank read

    /** On a 0.10.0 world a Summit holder reads as Knight and up; past three game days the grace is over. */
    @GameTest(template = "empty8")
    public static void summitHolderReadsKnight(GameTestHelper helper) {
        if (!Needs.mods(helper, "summitHolderReadsKnight", "apotheosis")) {
            return;
        }
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        Vec3 at = deep(helper);
        CasketCore.prepare(server);
        ServerPlayer player = deepPlayer(helper, at);
        award(player, "apotheosis:progression/summit");
        setScore(server, player, CasketCore.OBJ_JOIN_DAY, today(level) - 10);
        Marker probe = marker(level, at);
        run(as(probe), "function orespawn_integrations:lucky/rank/read");
        int rank = score(server, "#oi_rank", CasketCore.OBJ_WORK);
        int grace = score(server, "#oi_grace", CasketCore.OBJ_WORK);
        run(as(probe), "execute store result score #diag_any cc_lucky if entity @p[distance=..6]");
        run(as(probe), "execute store result score #diag_adv cc_lucky if entity @p[distance=..6,advancements={apotheosis:progression/summit=true}]");
        int diagAny = score(server, "#diag_any", CasketCore.OBJ_WORK);
        int diagAdv = score(server, "#diag_adv", CasketCore.OBJ_WORK);
        clear(level, at);
        AdvancementHolder summit = server.getAdvancements().get(ResourceLocation.parse("apotheosis:progression/summit"));
        helper.assertTrue(rank == 3, "a Summit holder read as rank " + rank + " (player at " + player.position()
                + ", alive " + player.isAlive() + ", summit done " + (summit != null
                && player.getAdvancements().getOrStartProgress(summit).isDone()) + ", probe at " + at + ", @p any " + diagAny + ", @p summit " + diagAdv + ")");
        helper.assertTrue(grace == 0, "a veteran Summit holder is still in grace");
        helper.succeed();
    }

    /** Card test (4): a Knight who joined this game day rolls the Squire row (grace on): a part, never a whole egg. */
    @GameTest(template = "empty8")
    public static void newKnightInGrace(GameTestHelper helper) {
        if (!Needs.mods(helper, "newKnightInGrace", "apotheosis")) {
            return;
        }
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        Vec3 at = deep(helper);
        resetCensus(server);
        ServerPlayer player = deepPlayer(helper, at);
        award(player, "apotheosis:progression/summit");
        setScore(server, player, CasketCore.OBJ_JOIN_DAY, today(level));
        for (int i = 0; i < 120; i++) {
            run(as(marker(level, at)), "function lucky_blocks:random/random");
            clear(level, at);
        }
        int grace = score(server, "#oi_grace", CasketCore.OBJ_WORK);
        helper.assertTrue(grace == 1, "a Knight who joined today is not in grace");
        helper.assertTrue(score(server, "#oi_whole", CasketCore.OBJ_WORK) == 0, "a whole egg during grace");
        helper.assertTrue(score(server, "#oi_refusals", CasketCore.OBJ_WORK) == 0, "a Great Refusal during grace");
        helper.succeed();
    }

    /** Without Summit, everyone falls through to the Hatchling table (rank 0 is the fall-through, never matches 0). */
    @GameTest(template = "empty8")
    public static void noSummitFallsThrough(GameTestHelper helper) {
        if (!Needs.mods(helper, "noSummitFallsThrough", "apotheosis")) {
            return;
        }
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        Vec3 at = deep(helper);
        CasketCore.prepare(server);
        deepPlayer(helper, at);
        run(as(marker(level, at)), "function orespawn_integrations:lucky/rank/read");
        int rank = score(server, "#oi_rank", CasketCore.OBJ_WORK);
        clear(level, at);
        helper.assertTrue(rank == Integer.MIN_VALUE, "a player without Summit read as rank " + rank);
        helper.succeed();
    }

    /** The Eggshell's casket: the first casket after a shell rolls rank 0 whatever the rank, and the score is spent. */
    @GameTest(template = "empty8")
    public static void shellCasketRollsRankZero(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        Vec3 at = deep(helper);
        CasketCore.prepare(server);
        run(server.createCommandSourceStack().withSuppressedOutput(), "scoreboard objectives add cc_shell_casket dummy");
        ServerPlayer player = deepPlayer(helper, at);
        award(player, "apotheosis:progression/summit");
        setScore(server, player, "cc_shell_casket", 1);
        run(as(marker(level, at)), "function orespawn_integrations:lucky/rank/read");
        int rank = score(server, "#oi_rank", CasketCore.OBJ_WORK);
        int shell = score(player, "cc_shell_casket");
        clear(level, at);
        helper.assertTrue(rank == Integer.MIN_VALUE, "the shell's casket read rank " + rank);
        helper.assertTrue(shell == Integer.MIN_VALUE, "cc_shell_casket was not cleared");
        helper.succeed();
    }

    /** Card test (4): at Knight the whole eggs come back, and a second one in the same Land's Week rerolls to a part. */
    @GameTest(template = "empty8", timeoutTicks = 200)
    public static void wholeEggOncePerWeek(GameTestHelper helper) {
        if (!Needs.mods(helper, "wholeEggOncePerWeek", "apotheosis")) {
            return;
        }
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        Vec3 at = deep(helper);
        resetCensus(server);
        ServerPlayer player = deepPlayer(helper, at);
        award(player, "apotheosis:progression/summit");
        for (int i = 0; i < 60; i++) {
            run(as(marker(level, at)), "function orespawn_integrations:lucky/knight/jackpot");
            clear(level, at);
        }
        int whole = score(server, "#oi_whole", CasketCore.OBJ_WORK);
        int parts = score(server, "#oi_parts", CasketCore.OBJ_WORK);
        int week = today(level) / 7 + 1;
        helper.assertTrue(whole == 1, "whole eggs in one week: " + whole + " (exactly 1)");
        helper.assertTrue(parts >= 20, "second whole eggs did not reroll to parts: " + parts);
        helper.assertTrue(score(player, CasketCore.OBJ_EGG_WEEK) == week, "the week of the egg was not kept");
        helper.succeed();
    }

    /** The day-one recipe gives a Royal Casket stack that stacks with the Eggshell's (the ly loot table). */
    @GameTest(template = "empty8")
    public static void arcaRealIsTheCasket(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        RecipeHolder<?> recipe = server.getRecipeManager()
                .byKey(ResourceLocation.fromNamespaceAndPath("orespawn_integrations", "arca_real")).orElse(null);
        helper.assertTrue(recipe != null, "orespawn_integrations:arca_real is not loaded");
        ItemStack crafted = ((Recipe<?>) recipe.value()).getResultItem(level.registryAccess());
        LootTable table = server.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE,
                ResourceLocation.fromNamespaceAndPath("lucky_blocks", "lucky_block")));
        List<ItemStack> rolled = table.getRandomItems(new LootParams.Builder(level).create(LootContextParamSets.EMPTY));
        helper.assertTrue(rolled.size() == 1, "the casket loot table gave " + rolled.size() + " stacks");
        helper.assertTrue(ItemStack.isSameItemSameComponents(crafted, rolled.get(0)),
                "the crafted casket doesn't stack with the loot-table casket");
        Component name = crafted.get(DataComponents.CUSTOM_NAME);
        helper.assertTrue(name != null && name.getContents() instanceof TranslatableContents t
                && t.getKey().equals("orespawn_integrations.lucky.royal_casket"), "the casket's name is not our key");
        CustomData entity = crafted.get(DataComponents.ENTITY_DATA);
        helper.assertTrue(entity != null && entity.copyTag().toString().contains("lucky_blocks.armor_stand"),
                "the casket stack doesn't place a ly casket");
        helper.succeed();
    }

    /** The Refused Gifts' chance-0 holders load (they never roll at random; /pandora fires them). */
    @GameTest(template = "empty8")
    public static void refusedGiftHoldersLoad(GameTestHelper helper) {
        if (!Needs.mods(helper, "refusedGiftHoldersLoad", "pandorasbox")) {
            return;
        }
        var key = ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("pandorasbox", "pandora_effect_holders"));
        var registry = helper.getLevel().registryAccess().registry(key).orElse(null);
        helper.assertTrue(registry != null, "no Pandora's Box holder registry");
        for (String id : new String[] {"grande_recusa/void_30", "grande_recusa/wither_army", "mobzilla_apocalypse"}) {
            helper.assertTrue(registry.containsKey(ResourceLocation.fromNamespaceAndPath("orespawn_integrations", id)),
                    "holder " + id + " missing");
        }
        helper.succeed();
    }

    /**
     * Card test (5) and script line 9: in a barn with 6 chickens, 4 sheep and a dog, every forced roll runs once at
     * the operator: no animal dies, nothing is alight, and the lava cauldron is left in the casket's hole.
     */
    @GameTest(template = "empty8", timeoutTicks = 200)
    public static void forcedRollsSpareTheBarn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CasketCore.prepare(level.getServer());
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        ServerPlayer op = MockPlayers.inLevel(helper);
        op.setGameMode(GameType.CREATIVE);
        Vec3 stand = helper.absoluteVec(new Vec3(4.5, 1.0, 1.5));
        op.teleportTo(level, stand.x, stand.y, stand.z, 0.0F, 0.0F);
        List<Mob> barn = new ArrayList<>();
        int[][] spots = {{1, 6}, {2, 6}, {3, 6}, {5, 6}, {6, 6}, {7, 6}, {1, 4}, {7, 4}, {1, 2}, {7, 2}, {1, 7}};
        for (int i = 0; i < spots.length; i++) {
            EntityType<? extends Mob> type = i < 6 ? EntityType.CHICKEN : i < 10 ? EntityType.SHEEP : EntityType.WOLF;
            Mob mob = helper.spawn(type, new BlockPos(spots[i][0], 1, spots[i][1]));
            if (mob instanceof Wolf wolf) {
                wolf.tame(op);
            }
            barn.add(mob);
        }
        String[] rolls = {"effect", "gear", "loaner", "jackpot", "arrow", "spectral_arrow", "anvil", "lightning",
                "fireball", "small_fireball", "wither_skull", "tnt", "chicken_tower", "capybara_tower", "breeze",
                "water_drop", "cell", "water", "lava"};
        for (String roll : rolls) {
            run(as(op), "function orespawn_integrations:lucky/test/" + roll);
        }
        helper.runAfterDelay(120, () -> {
            for (Mob mob : barn) {
                helper.assertTrue(mob.isAlive(), mob.getType().getDescriptionId() + " died in the barn");
            }
            BlockPos lo = helper.absolutePos(new BlockPos(-4, -1, -4));
            BlockPos hi = helper.absolutePos(new BlockPos(12, 20, 12));
            boolean cauldron = false;
            for (BlockPos p : BlockPos.betweenClosed(lo, hi)) {
                var state = level.getBlockState(p);
                helper.assertTrue(!state.is(Blocks.FIRE) && !state.is(Blocks.SOUL_FIRE) && !state.is(Blocks.LAVA),
                        "something is alight at " + p);
                cauldron |= state.is(Blocks.LAVA_CAULDRON);
            }
            helper.assertTrue(cauldron, "no lava cauldron was left in the casket's hole");
            for (Entity e : level.getEntities((Entity) null, new AABB(lo.getCenter(), hi.getCenter()).inflate(4),
                    e -> !(e instanceof Player))) {
                e.discard();
            }
            helper.succeed();
        });
    }

    // ---- item-hoard-joke-armoury (the Loaner, on system-hoard-caskets)

    /** The casket's Loaner is a Big Bertha with 24 swings; it breaks after its 24th attack, whatever its durability. */
    @GameTest(template = "empty8", timeoutTicks = 60)
    public static void loanerBreaksAtSwing24(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CasketCore.prepare(level.getServer());
        ServerPlayer player = MockPlayers.inLevel(helper);
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        Vec3 at = helper.absoluteVec(new Vec3(4.5, 1.0, 2.5));
        player.teleportTo(level, at.x, at.y, at.z, 0.0F, 0.0F);
        run(as(player), "function orespawn_integrations:lucky/test/loaner");
        ItemStack loaner = ItemStack.EMPTY;
        for (Entity e : level.getEntities((Entity) null, player.getBoundingBox().inflate(6), e -> e instanceof ItemEntity)) {
            ItemStack stack = ((ItemEntity) e).getItem();
            if (LoanerWeapons.isLoaner(stack)) {
                loaner = stack.copy();
                e.discard();
            }
        }
        helper.assertTrue(!loaner.isEmpty() && loaner.is(item(os("big_bertha"))), "the forced roll gave no Loaner Bertha");
        helper.assertTrue(LoanerWeapons.swingsLeft(loaner) == 24, "a fresh Loaner has " + LoanerWeapons.swingsLeft(loaner));
        player.setItemInHand(InteractionHand.MAIN_HAND, loaner);
        ItemStack held = player.getMainHandItem();
        Zombie target = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 1, 4));
        for (int i = 0; i < 23; i++) {
            CommonHooks.onPlayerAttackTarget(player, target);
        }
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(!held.isEmpty() && LoanerWeapons.swingsLeft(held) == 1, "the Loaner broke before swing 24");
            CommonHooks.onPlayerAttackTarget(player, target);
            helper.runAfterDelay(3, () -> {
                helper.assertTrue(held.isEmpty(), "the Loaner outlived its 24th swing");
                target.discard();
                helper.succeed();
            });
        });
    }

    /** An anvil with a Loaner in it gives nothing (no combining, no renaming). */
    @GameTest(template = "empty8")
    public static void anvilRefusesLoaner(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper);
        ItemStack loaner = new ItemStack(item(os("big_bertha")));
        CustomData.update(DataComponents.CUSTOM_DATA, loaner, tag -> tag.putInt(LoanerWeapons.KEY, 24));
        AnvilUpdateEvent event = new AnvilUpdateEvent(loaner, loaner.copy(), "mine", 1, player);
        NeoForge.EVENT_BUS.post(event);
        helper.assertTrue(event.isCanceled(), "the anvil took two Loaners");
        helper.succeed();
    }

    // ---- item-the-larder: the royal seal

    private static BlockHitResult floorHit(GameTestHelper helper, BlockPos rel) {
        BlockPos abs = helper.absolutePos(rel);
        return new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0.5, 0), Direction.UP, abs, false);
    }

    private static ServerPlayer holding(GameTestHelper helper, ResourceLocation egg) {
        ServerPlayer player = MockPlayers.inLevel(helper);
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item(egg)));
        return player;
    }

    /** A Mobzilla egg used at rank 0 in the open is refused with the seal line and kept; nothing hatches. */
    @GameTest(template = "empty8")
    public static void sealRefusesRankZero(GameTestHelper helper) {
        if (!Needs.mods(helper, "sealRefusesRankZero", "apotheosis")) {
            return;
        }
        helper.setBlock(new BlockPos(3, 0, 3), Blocks.STONE);
        ServerPlayer player = holding(helper, os("godzilla_spawn_egg"));
        InteractionResult result = player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(),
                InteractionHand.MAIN_HAND, floorHit(helper, new BlockPos(3, 0, 3)));
        helper.assertTrue(result == InteractionResult.FAIL, "a rank-0 Mobzilla egg was not refused: " + result);
        helper.assertTrue(player.getMainHandItem().getCount() == 1, "the refused egg was used up");
        helper.assertTrue(helper.getLevel().getEntities((Entity) null,
                new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(16),
                e -> BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).equals(os("godzilla"))).isEmpty(),
                "a Mobzilla hatched at rank 0");
        helper.succeed();
    }

    /** The seal's rules: a stage opens most eggs at any rank, the King's and Queen's from Squire, never the strict two. */
    @GameTest(template = "empty8")
    public static void sealRules(GameTestHelper helper) {
        ResourceLocation mobzilla = os("godzilla_spawn_egg");
        ResourceLocation king = os("the_king_spawn_egg");
        ResourceLocation wither = ResourceLocation.withDefaultNamespace("wither_spawn_egg");
        helper.assertTrue(RoyalSeal.rule(mobzilla, false, true, false, false) == RoyalSeal.Verdict.OPEN, "arena pad, rank 0");
        helper.assertTrue(RoyalSeal.rule(mobzilla, false, false, false, false) == RoyalSeal.Verdict.RANK, "open, rank 0");
        helper.assertTrue(RoyalSeal.rule(mobzilla, false, false, true, true) == RoyalSeal.Verdict.OPEN, "open, Knight");
        helper.assertTrue(RoyalSeal.rule(king, false, true, false, false) == RoyalSeal.Verdict.STAGE_SQUIRE, "altar, rank 0");
        helper.assertTrue(RoyalSeal.rule(king, false, true, false, true) == RoyalSeal.Verdict.OPEN, "altar, Squire");
        helper.assertTrue(RoyalSeal.rule(wither, false, true, false, true) == RoyalSeal.Verdict.RANK, "Wither egg on an altar");
        helper.assertTrue(RoyalSeal.rule(wither, false, false, true, true) == RoyalSeal.Verdict.OPEN, "Wither egg, Knight");
        helper.assertTrue(RoyalSeal.rule(mobzilla, true, true, true, true) == RoyalSeal.Verdict.SPAWNER, "on a spawner");
        helper.succeed();
    }

    /** In 0.10.0 an Apotheosis Ascent holder counts as Knight: a sealed egg hatches in the open, tagged cc_ovo. */
    @GameTest(template = "empty8")
    public static void ascentHatchesTaggedOvo(GameTestHelper helper) {
        if (!Needs.mods(helper, "ascentHatchesTaggedOvo", "apotheosis")) {
            return;
        }
        helper.setBlock(new BlockPos(3, 0, 3), Blocks.STONE);
        ServerPlayer player = holding(helper, os("water_dragon_spawn_egg"));
        award(player, "apotheosis:progression/ascent");
        InteractionResult result = player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(),
                InteractionHand.MAIN_HAND, floorHit(helper, new BlockPos(3, 0, 3)));
        List<Entity> hatched = helper.getLevel().getEntities((Entity) null,
                new AABB(helper.absolutePos(new BlockPos(3, 1, 3))).inflate(4),
                e -> BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).equals(os("water_dragon")));
        boolean tagged = !hatched.isEmpty() && hatched.get(0).getTags().contains(RoyalSeal.HATCHED_TAG);
        hatched.forEach(Entity::discard);
        helper.assertTrue(result != InteractionResult.FAIL, "a Knight's sealed egg was refused");
        helper.assertTrue(tagged, "the hatched egg was not tagged cc_ovo");
        helper.succeed();
    }

    /** A sealed egg on a spawner is refused, even for a Knight, and the spawner is left as it was. */
    @GameTest(template = "empty8")
    public static void sealRefusesSpawner(GameTestHelper helper) {
        BlockPos rel = new BlockPos(3, 1, 3);
        helper.setBlock(rel, Blocks.SPAWNER);
        ServerPlayer player = holding(helper, os("basilisk_spawn_egg"));
        award(player, "apotheosis:progression/ascent");
        BlockPos abs = helper.absolutePos(rel);
        InteractionResult result = player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(),
                InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
        helper.assertTrue(result == InteractionResult.FAIL, "a sealed egg went on a spawner: " + result);
        helper.assertTrue(!BossSpawnerGuard.guardsBoss(helper.getLevel(), abs), "the spawner took the basilisk");
        helper.succeed();
    }

    /** A dispenser has no rank: a sealed egg stays in it off a stage. */
    @GameTest(template = "empty8", timeoutTicks = 40)
    public static void dispenserKeepsSealedEgg(GameTestHelper helper) {
        BlockPos rel = new BlockPos(3, 1, 3);
        helper.setBlock(rel, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.UP));
        DispenserBlockEntity box = (DispenserBlockEntity) helper.getBlockEntity(rel);
        box.setItem(0, new ItemStack(item(os("water_dragon_spawn_egg"))));
        helper.setBlock(new BlockPos(4, 1, 3), Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(12, () -> {
            helper.assertTrue(box.getItem(0).getCount() == 1, "the dispenser hatched a sealed egg off a stage");
            helper.succeed();
        });
    }

    /** The tags the Larder's other systems read: 105 larder outputs, 17 sealed eggs, 18 royal egg blocks. */
    @GameTest(template = "empty8")
    public static void larderTags(GameTestHelper helper) {
        helper.assertTrue(size(Registries.ITEM, "ovos_da_despensa") == 105, "#ovos_da_despensa holds " + size(Registries.ITEM, "ovos_da_despensa"));
        helper.assertTrue(size(Registries.ITEM, "royal_seal_eggs") == 17, "#royal_seal_eggs holds " + size(Registries.ITEM, "royal_seal_eggs"));
        helper.assertTrue(size(Registries.ITEM, "royal_eggs") == 18, "#royal_eggs holds " + size(Registries.ITEM, "royal_eggs"));
        helper.succeed();
    }

    private static int size(ResourceKey<? extends net.minecraft.core.Registry<?>> registry, String path) {
        if (registry == Registries.ITEM) {
            TagKey<Item> tag = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("orespawn_integrations", path));
            return BuiltInRegistries.ITEM.getTag(tag).map(t -> t.size()).orElse(0);
        }
        TagKey<EntityType<?>> tag = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.parse(path));
        return BuiltInRegistries.ENTITY_TYPE.getTag(tag).map(t -> t.size()).orElse(0);
    }

    // ---- item-royal-diet

    /** HBM skips the eight vanilla items (no hazard, so no effect and no tooltip); its own asbestos keeps its hazard. */
    @GameTest(template = "empty8")
    public static void hbmExemptions(GameTestHelper helper) {
        helper.assertTrue(HazardExemptions.installed(), "the exemption tag is not in HBM's blacklist");
        for (Item vanilla : new Item[] {Items.GREEN_WOOL, Items.WHITE_WOOL, Items.GOLD_INGOT, Items.BLAZE_POWDER,
                Items.GUNPOWDER, Items.PUMPKIN_PIE, Items.TNT, Items.COAL}) {
            helper.assertTrue(HazardSystem.getHazardsFromStack(new ItemStack(vanilla)).isEmpty(),
                    BuiltInRegistries.ITEM.getKey(vanilla) + " still has an HBM hazard");
        }
        Item asbestos = item(ResourceLocation.fromNamespaceAndPath("hbmsntm", "powder_asbestos"));
        Item uranium = item(ResourceLocation.fromNamespaceAndPath("hbmsntm", "ingot_uranium"));
        if (HazardSystem.getHazardsFromStack(new ItemStack(uranium)).isEmpty()) {
            // HBM fills its hazard registry late; where even uranium shows none (the dev run), there is nothing to compare
            org.slf4j.LoggerFactory.getLogger("OreSpawnIntegrationsTests").warn("GameTest skipped: hbmExemptions needs HBM's hazard registry");
            helper.succeed();
            return;
        }
        helper.assertTrue(!HazardSystem.getHazardsFromStack(new ItemStack(asbestos)).isEmpty(), "HBM's asbestos lost its hazard ("
                + BuiltInRegistries.ITEM.getKey(asbestos) + ", in exempt tag " + new ItemStack(asbestos).is(HazardExemptions.EXEMPT)
                + ", uranium ingot hazards " + HazardSystem.getHazardsFromStack(new ItemStack(item(ResourceLocation.fromNamespaceAndPath("hbmsntm", "ingot_uranium")))) + ")");
        helper.succeed();
    }

    /** The crusher turns one HBM uranium ingot into royal nuggets; OreSpawn titanium leaves #c:ingots/titanium. */
    @GameTest(template = "empty8")
    public static void royalDietData(GameTestHelper helper) {
        if (!Needs.mods(helper, "royalDietData", "railcraft")) {
            return;
        }
        MinecraftServer server = helper.getLevel().getServer();
        helper.assertTrue(server.getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("orespawn_integrations",
                "railcraft_crushing_hbm_uranium")).isPresent(), "the HBM uranium crusher recipe is not loaded");
        TagKey<Item> common = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "ingots/titanium"));
        TagKey<Item> royal = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("orespawn_integrations", "royal_titanium"));
        ItemStack titanium = new ItemStack(item(os("ingot_titanium")));
        helper.assertTrue(!titanium.is(common), "OreSpawn titanium is still in #c:ingots/titanium");
        helper.assertTrue(titanium.is(royal), "OreSpawn titanium is not in #royal_titanium");
        helper.succeed();
    }

    // ---- npc-mobzilla: the boss taps

    /** A cage pressed on a Wither or a Mobzilla is refused and stays in hand; a zombie is still caged. */
    @GameTest(template = "empty8")
    public static void cagePressRefusesBosses(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = holding(helper, os("cage_empty"));
        for (ResourceLocation boss : new ResourceLocation[] {ResourceLocation.withDefaultNamespace("wither"), os("godzilla")}) {
            Entity target = BuiltInRegistries.ENTITY_TYPE.get(boss).create(level);
            target.moveTo(helper.absoluteVec(new Vec3(4.5, 1, 4.5)));
            InteractionResult result = CommonHooks.onInteractEntity(player, target, InteractionHand.MAIN_HAND);
            helper.assertTrue(result == InteractionResult.FAIL, "a pressed cage took " + boss);
            helper.assertTrue(player.getMainHandItem().is(item(os("cage_empty"))), "the cage left the hand on " + boss);
        }
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 1, 4));
        zombie.setNoAi(true);
        helper.assertTrue(!BossCageRefusal.refuses(zombie), "the refusal covers a zombie");
        player.interactOn(zombie, InteractionHand.MAIN_HAND);
        helper.assertTrue(!zombie.isAlive() || zombie.isRemoved(), "a pressed cage no longer takes a zombie");
        helper.succeed();
    }

    /** A thrown cage hitting a Kraken drops as an empty cage. */
    @GameTest(template = "empty8")
    public static void thrownCageDropsOnKraken(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = MockPlayers.inLevel(helper);
        Entity kraken = BuiltInRegistries.ENTITY_TYPE.get(os("kraken")).create(level);
        kraken.moveTo(helper.absoluteVec(new Vec3(4.5, 2, 4.5)));
        EntityCage cage = new EntityCage(level, player, 0);
        cage.moveTo(helper.absoluteVec(new Vec3(4.5, 2, 3.5)));
        level.addFreshEntity(cage);
        boolean cancelled = EventHooks.onProjectileImpact(cage, new EntityHitResult(kraken));
        helper.assertTrue(cancelled, "the thrown cage's hit on a Kraken went through");
        helper.assertTrue(cage.isRemoved(), "the refused cage kept flying");
        boolean dropped = !level.getEntities((Entity) null, cage.getBoundingBox().inflate(3),
                e -> e instanceof ItemEntity drop && drop.getItem().is(item(os("cage_empty")))).isEmpty();
        helper.assertTrue(dropped, "no empty cage dropped");
        helper.succeed();
    }

    /** A boss's spawner refuses Silk Touch and stays; a zombie spawner still comes up with Silk Touch. */
    @GameTest(template = "empty8")
    public static void bossSpawnerRefusesSilkTouch(GameTestHelper helper) {
        if (!Needs.mods(helper, "bossSpawnerRefusesSilkTouch", "apotheosis", "apothic_spawners")) {
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos boss = new BlockPos(2, 1, 2);
        BlockPos farm = new BlockPos(5, 1, 5);
        helper.setBlock(boss, Blocks.SPAWNER);
        helper.setBlock(farm, Blocks.SPAWNER);
        ((SpawnerBlockEntity) helper.getBlockEntity(boss)).setEntityId(BuiltInRegistries.ENTITY_TYPE.get(os("basilisk")), level.random);
        ((SpawnerBlockEntity) helper.getBlockEntity(farm)).setEntityId(EntityType.ZOMBIE, level.random);
        helper.assertTrue(BossSpawnerGuard.guardsBoss(level, helper.absolutePos(boss)), "a Basilisk spawner is not guarded");
        helper.assertTrue(!BossSpawnerGuard.guardsBoss(level, helper.absolutePos(farm)), "a zombie spawner is guarded");
        ServerPlayer player = MockPlayers.inLevel(helper);
        player.setGameMode(GameType.SURVIVAL);
        ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
        pick.enchant(level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.SILK_TOUCH), 1);
        player.setItemInHand(InteractionHand.MAIN_HAND, pick);
        player.gameMode.destroyBlock(helper.absolutePos(boss));
        player.gameMode.destroyBlock(helper.absolutePos(farm));
        helper.assertBlockPresent(Blocks.SPAWNER, boss);
        helper.assertBlockNotPresent(Blocks.SPAWNER, farm);
        helper.succeed();
    }

    /** An Apothic spawner modifier on a boss's spawner is refused and kept in hand. */
    @GameTest(template = "empty8")
    public static void bossSpawnerRefusesModifier(GameTestHelper helper) {
        if (!Needs.mods(helper, "bossSpawnerRefusesModifier", "apotheosis", "apothic_spawners")) {
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(3, 1, 3);
        helper.setBlock(rel, Blocks.SPAWNER);
        ((SpawnerBlockEntity) helper.getBlockEntity(rel)).setEntityId(BuiltInRegistries.ENTITY_TYPE.get(os("alien_boss")), level.random);
        ItemStack modifier = ItemStack.EMPTY;
        for (RecipeHolder<SpawnerModifier> holder : level.getRecipeManager().getAllRecipesFor(ASObjects.SPAWNER_MODIFIER.get())) {
            SpawnerModifier mod = holder.value();
            if (mod.getOffhandInput().isEmpty() && mod.getMainhandInput().getItems().length > 0) {
                modifier = mod.getMainhandInput().getItems()[0].copy();
                break;
            }
        }
        helper.assertTrue(!modifier.isEmpty(), "no Apothic spawner modifier recipe without an off-hand item");
        ServerPlayer player = MockPlayers.inLevel(helper);
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, modifier);
        int before = modifier.getCount();
        BlockPos abs = helper.absolutePos(rel);
        InteractionResult result = player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
        helper.assertTrue(result == InteractionResult.FAIL, "the modifier was applied to a boss spawner: " + result);
        helper.assertTrue(player.getMainHandItem().getCount() == before, "the refused modifier was used up");
        helper.succeed();
    }

    /** The boss tags: 46 heavyweights, merged into Apothic's spawner blacklist (so Capturing drops no Emperor
     * Scorpion egg), the dragon and the Wither on the Ars jar blacklist, and the statue table's no-spawner list. */
    @GameTest(template = "empty8")
    public static void bossTags(GameTestHelper helper) {
        if (!Needs.mods(helper, "bossTags", "cataclysm", "mowziesmobs", "twilightforest")) {
            return;
        }
        helper.assertTrue(size(Registries.ENTITY_TYPE, "orespawn_integrations:heavyweights") == 46, "heavyweights: " + size(Registries.ENTITY_TYPE, "orespawn_integrations:heavyweights"));
        EntityType<?> scorpion = BuiltInRegistries.ENTITY_TYPE.get(os("emperor_scorpion"));
        helper.assertTrue(scorpion.is(TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.parse("apothic_spawners:blacklisted_from_spawners"))),
                "the Emperor Scorpion is not on Apothic's blacklist");
        TagKey<EntityType<?>> jar = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.parse("ars_nouveau:jar_blacklist"));
        helper.assertTrue(EntityType.WITHER.is(jar) && EntityType.ENDER_DRAGON.is(jar), "the jar blacklist misses the dragon or the Wither");
        helper.assertTrue(EntityType.WITHER.is(BossCageRefusal.NO_COPIES) && BuiltInRegistries.ENTITY_TYPE.get(os("godzilla")).is(BossCageRefusal.NO_COPIES)
                && BuiltInRegistries.ENTITY_TYPE.get(os("kraken")).is(BossCageRefusal.NO_COPIES), "#no_copies misses a boss");
        helper.assertTrue(!EntityType.ZOMBIE.is(BossCageRefusal.NO_COPIES), "#no_copies holds the zombie");
        helper.succeed();
    }

    /** The statue table refuses the Mobzilla, Warden and Elder Guardian statues the spawner upgrade, and takes a zombie's. */
    @GameTest(template = "empty8")
    public static void statueSpawnerUpgrade(GameTestHelper helper) {
        RecipeHolder<?> holder = helper.getLevel().getServer().getRecipeManager()
                .byKey(ResourceLocation.parse("statues:upgrade/spawner")).orElse(null);
        helper.assertTrue(holder != null && holder.value() instanceof com.shynieke.statues.recipe.UpgradeRecipe, "no statues:upgrade/spawner");
        var center = ((com.shynieke.statues.recipe.UpgradeRecipe) holder.value()).getCenter();
        for (String heavy : new String[] {"orespawn_integrations:mobzilla_statue", "statues:warden_statue", "statues:elder_guardian_statue"}) {
            helper.assertTrue(!center.test(new ItemStack(item(ResourceLocation.parse(heavy)))), heavy + " still takes the spawner upgrade");
        }
        helper.assertTrue(center.test(new ItemStack(item(ResourceLocation.parse("statues:zombie_statue")))), "the zombie statue lost the spawner upgrade");
        helper.succeed();
    }
}
