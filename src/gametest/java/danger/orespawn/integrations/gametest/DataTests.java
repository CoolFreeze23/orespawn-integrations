package danger.orespawn.integrations.gametest;

import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The data group's card test lines that a server can check alone: the twin's log tables, the canopy's rate and cap,
 * the tab roots and nodes, the night's spawn lists, the decor tag and the predators' prey. Drops are rolled through
 * {@link Block#getDrops}, the same path a player's break takes, so the global loot modifiers run as they do in play.
 */
@GameTestHolder("orespawn_integrations_tests")
@PrefixGameTestTemplate(false)
public final class DataTests {

    private static final String EGG = "brasilcoisas:ovinho_vivo_block";
    private static final Set<String> COPA_ROWS = Set.of("arca", "chapeu", "gaveta", "retalho", "presente", "parte");

    private DataTests() {
    }

    private static ResourceLocation rl(String id) {
        return ResourceLocation.parse(id);
    }

    private static void setScore(ServerLevel level, ServerPlayer player, String name, int value) {
        Scoreboard board = level.getScoreboard();
        Objective objective = board.getObjective(name);
        if (objective == null) {
            objective = board.addObjective(name, ObjectiveCriteria.DUMMY, Component.literal(name),
                    ObjectiveCriteria.RenderType.INTEGER, false, null);
        }
        board.getOrCreatePlayerScore(player, objective).set(value);
    }

    private static List<ItemStack> breakLog(GameTestHelper helper, BlockState log, ServerPlayer breaker) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        return Block.getDrops(log, helper.getLevel(), pos, null, breaker, ItemStack.EMPTY);
    }

    private static List<String> tableAlone(GameTestHelper helper, BlockState state, ServerPlayer breaker, String table) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,
                ResourceLocation.parse(table));
        var loot = helper.getLevel().getServer().reloadableRegistries().getLootTable(key);
        var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(helper.getLevel())
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, net.minecraft.world.phys.Vec3.atCenterOf(pos))
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.TOOL, ItemStack.EMPTY)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_STATE, state)
                .withOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.THIS_ENTITY, breaker)
                .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.BLOCK);
        return loot.getRandomItems(params).stream().map(s -> BuiltInRegistries.ITEM.getKey(s.getItem()).toString()).toList();
    }

    private static boolean isEgg(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(EGG);
    }

    private static String lote(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? null : data.copyTag().getCompound("orespawn_integrations:lote").getString("log");
    }

    private static String copaRow(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.copyTag().contains("orespawn_integrations:copa")) {
            return null;
        }
        return data.copyTag().getCompound("orespawn_integrations:copa").getString("row");
    }

    /** wonder-gemeo-da-arvore: the first log drops the log and the egg with the lote key; after pickup none does. */
    @GameTest(template = "empty8")
    public static void firstLogDropsTheTwinUntilPickup(GameTestHelper helper) {
        if (!ModList.get().isLoaded("brasilcoisas")) {
            helper.succeed();
            return;
        }
        ServerPlayer player = MockPlayers.inLevel(helper);
        for (Block log : List.of(Blocks.OAK_LOG, Blocks.BIRCH_LOG)) {
            List<ItemStack> drops = breakLog(helper, log.defaultBlockState(), player);
            String logId = BuiltInRegistries.BLOCK.getKey(log).toString();
            helper.assertTrue(drops.stream().anyMatch(s -> s.is(log.asItem())), "the log itself drops");
            helper.assertTrue(drops.stream().anyMatch(s -> isEgg(s) && logId.equals(lote(s))),
                    "an unset cc_gemeo drops the egg carrying " + logId + " (drops: " + drops.stream()
                            .map(s -> BuiltInRegistries.ITEM.getKey(s.getItem()) + "/" + lote(s)).toList() + "; table alone: "
                            + tableAlone(helper, log.defaultBlockState(), player, "orespawn_integrations:arvore/" + logId.replace(':', '/')) + ")");
            helper.assertTrue(drops.stream().filter(DataTests::isEgg).allMatch(s -> s.get(DataComponents.LORE) != null),
                    "the egg carries its two lore lines");
        }
        setScore(helper.getLevel(), player, "cc_gemeo", 1);
        setScore(helper.getLevel(), player, "cc_copa", 3);
        for (int i = 0; i < 50; i++) {
            List<ItemStack> drops = breakLog(helper, Blocks.OAK_LOG.defaultBlockState(), player);
            helper.assertTrue(drops.stream().noneMatch(DataTests::isEgg), "no second twin after pickup");
            helper.assertTrue(drops.stream().allMatch(s -> copaRow(s) == null), "a capped player gets no canopy find");
        }
        helper.succeed();
    }

    /** A break with no player in the context (a piston, an explosion, a mob) never rolls either pool. */
    @GameTest(template = "empty8")
    public static void noPlayerNoRoll(GameTestHelper helper) {
        for (int i = 0; i < 100; i++) {
            List<ItemStack> drops = breakLog(helper, Blocks.SPRUCE_LOG.defaultBlockState(), null);
            helper.assertTrue(drops.stream().noneMatch(s -> isEgg(s) || copaRow(s) != null), "nobody broke it");
        }
        helper.succeed();
    }

    /**
     * system-extravio, roadmap 7.9 line 4(a): with the cap lifted, 480 rank-0 breaks give about 1 in 24 (15-25 expected;
     * 6-40 is the bound a fair coin keeps well inside), every find one of the six 0.10.0 rows and never a Mimic or a
     * Leaf Monster.
     */
    @GameTest(template = "empty8")
    public static void canopyRateWithTheCapLifted(GameTestHelper helper) {
        if (!ModList.get().isLoaded("brasilcoisas")) {
            helper.succeed();
            return;
        }
        ServerPlayer player = MockPlayers.inLevel(helper);
        setScore(helper.getLevel(), player, "cc_gemeo", 1);
        setScore(helper.getLevel(), player, "cc_copa", -100000);
        int finds = 0;
        for (int i = 0; i < 480; i++) {
            for (ItemStack s : breakLog(helper, Blocks.JUNGLE_LOG.defaultBlockState(), player)) {
                String row = copaRow(s);
                if (row != null) {
                    helper.assertTrue(COPA_ROWS.contains(row), "unknown canopy row " + row);
                    finds++;
                }
                helper.assertTrue(!isEgg(s), "no twin once cc_gemeo is 1");
            }
        }
        helper.assertTrue(finds >= 6 && finds <= 40, "480 breaks gave " + finds + " canopy finds");
        helper.succeed();
    }

    /** system-advancement-tree: the eight tab roots and the 0.10.0 nodes other groups grant are loaded. */
    @GameTest(template = "empty8")
    public static void tabRootsAndNodesLoad(GameTestHelper helper) {
        if (!Needs.mods(helper, "tabRootsAndNodesLoad", "braziliandelight", "brazil_legends", "monster_hunter_villager")) {
            return;
        }
        MinecraftServer server = helper.getLevel().getServer();
        for (String id : List.of("wonder/2", "wonder/1", "week/root", "hunt/root", "parish/root", "property/root",
                "causos/root", "secrets/root", "livro/r1", "livro/r2", "livro/r4", "livro/r5", "livro/r6", "livro/r21",
                "livro/keep_the_change", "livro/lote_pickup", "wonder/5", "wonder/17", "property/perda_total",
                "gambiarra/luz_de_bruxa", "gambiarra/enxoval_da_rainha", "guia/p01", "guia/p03", "guia/p07",
                "guia/p08", "guia/dusk", "guia/close", "court/tab_full", "secrets/tab_full")) {
            helper.assertTrue(server.getAdvancements().get(rl("orespawn_integrations:" + id)) != null,
                    "advancement " + id + " is loaded");
        }
        helper.succeed();
    }

    private static boolean spawns(ServerLevel level, String biome, MobCategory category, String namespaceOrId) {
        Biome b = level.registryAccess().registryOrThrow(Registries.BIOME).get(rl(biome));
        if (b == null) {
            return false;
        }
        for (MobSpawnSettings.SpawnerData data : b.getMobSettings().getMobs(category).unwrap()) {
            String id = BuiltInRegistries.ENTITY_TYPE.getKey(data.type).toString();
            if (id.equals(namespaceOrId) || id.startsWith(namespaceOrId + ":")) {
                return true;
            }
        }
        return false;
    }

    /**
     * The Royal Mines keep the port's harmless ambient visitors, but not its three ambient hostiles (Alvin's call,
     * REPORT #15): their spawn rule always passes, so as ambient spawns they would ignore light, even in a lit base.
     */
    @GameTest(template = "empty8")
    public static void minesKeepNoAmbientHostiles(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (String hostile : new String[]{"orespawn:ender_knight", "orespawn:ender_reaper", "orespawn:mothra"}) {
            for (MobCategory category : MobCategory.values()) {
                helper.assertFalse(spawns(level, "orespawn:mining_biome", category, hostile),
                        hostile + " still spawns in the Royal Mines (" + category + ")");
            }
        }
        helper.assertTrue(spawns(level, "orespawn:mining_biome", MobCategory.AMBIENT, "orespawn:luna_moth"),
                "the luna moths stay in the Royal Mines");
        helper.succeed();
    }

    /**
     * system-night-rebalance: Born in Chaos is gone from the Nether, Twilight and OreSpawn's worlds but keeps Chaos;
     * Utopia spawns no monster; no island spawns in the Islands; the Mestre is out of Twilight.
     */
    @GameTest(template = "empty8")
    public static void nightSpawnLists(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        boolean bic = ModList.get().isLoaded("born_in_chaos_v1");
        if (bic) {
            helper.assertTrue(!spawns(level, "minecraft:nether_wastes", MobCategory.MONSTER, "born_in_chaos_v1"),
                    "no Born in Chaos in the Nether");
            helper.assertTrue(!spawns(level, "orespawn:mining_biome", MobCategory.MONSTER, "born_in_chaos_v1"),
                    "no Born in Chaos in the Mining dimension");
            helper.assertTrue(spawns(level, "orespawn:chaos_biome", MobCategory.MONSTER, "born_in_chaos_v1"),
                    "Chaos keeps its Born in Chaos entries for the Nursery");
            if (ModList.get().isLoaded("twilightforest")) {
                helper.assertTrue(!spawns(level, "twilightforest:forest", MobCategory.MONSTER, "born_in_chaos_v1"),
                        "no Born in Chaos in Twilight");
            }
        }
        for (String vanilla : List.of("minecraft:zombie", "minecraft:skeleton", "minecraft:creeper", "minecraft:spider",
                "minecraft:enderman", "minecraft:witch", "minecraft:slime")) {
            helper.assertTrue(!spawns(level, "orespawn:utopia_plains", MobCategory.MONSTER, vanilla),
                    vanilla + " does not spawn in Utopia");
        }
        helper.assertTrue(!spawns(level, "orespawn:island_biome", MobCategory.CREATURE, "orespawn:island"),
                "no orespawn:island spawn in the Islands");
        helper.assertTrue(!spawns(level, "orespawn:island_biome", MobCategory.CREATURE, "orespawn:island_too"),
                "no orespawn:island_too spawn in the Islands");
        if (ModList.get().isLoaded("twilightforest")) {
            helper.assertTrue(!spawns(level, "twilightforest:forest", MobCategory.CREATURE, "brasilcoisas:mestre_ensinador"),
                    "the Mestre stays out of Twilight");
        }
        helper.succeed();
    }

    /** item-builders-yard: the Base decor tag holds the starter set and leaves plain building blocks out. */
    @GameTest(template = "empty8")
    public static void manorDecorStarterSet(GameTestHelper helper) {
        TagKey<Block> decor = TagKey.create(Registries.BLOCK, rl("orespawn_integrations:manor_decor"));
        for (String in : List.of("minecraft:white_carpet", "minecraft:lantern", "minecraft:oak_sign",
                "bibliocraft:typewriter")) {
            if (BuiltInRegistries.BLOCK.containsKey(rl(in))) {
                helper.assertTrue(BuiltInRegistries.BLOCK.get(rl(in)).defaultBlockState().is(decor), in + " counts as decor");
            }
        }
        for (String out : List.of("minecraft:glass", "minecraft:oak_planks", "minecraft:stone_bricks",
                "minecraft:white_wool", "minecraft:white_concrete")) {
            helper.assertTrue(!BuiltInRegistries.BLOCK.get(rl(out)).defaultBlockState().is(decor), out + " is not decor");
        }
        helper.succeed();
    }

    /** item-lands-wildlife-goods: the lion hunts the scorpion, not the gazelle; tiger, alligator and bear their new prey. */
    @GameTest(template = "empty8")
    public static void predatorsPrey(GameTestHelper helper) {
        if (!ModList.get().isLoaded("naturalist") || !ModList.get().isLoaded("orespawn")) {
            helper.succeed();
            return;
        }
        String[][] rows = {{"lion_hostiles", "orespawn:scorpion"}, {"tiger_hostiles", "orespawn:stink_bug"},
                {"tiger_hostiles", "orespawn:mantis"}, {"alligator_hostiles", "orespawn:frog"},
                {"bear_hostiles", "orespawn:beaver"}};
        for (String[] row : rows) {
            TagKey<EntityType<?>> tag = TagKey.create(Registries.ENTITY_TYPE, rl("naturalist:" + row[0]));
            helper.assertTrue(BuiltInRegistries.ENTITY_TYPE.get(rl(row[1])).is(tag), row[1] + " in " + row[0]);
        }
        TagKey<EntityType<?>> lion = TagKey.create(Registries.ENTITY_TYPE, rl("naturalist:lion_hostiles"));
        helper.assertTrue(!BuiltInRegistries.ENTITY_TYPE.get(rl("orespawn:gazelle")).is(lion), "the gazelle is off the lion's menu");
        helper.succeed();
    }
}
