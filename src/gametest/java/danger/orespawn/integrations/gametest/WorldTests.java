package danger.orespawn.integrations.gametest;

import danger.orespawn.integrations.world.WorldUtil;
import danger.orespawn.integrations.world.antechamber.Antechamber;
import danger.orespawn.integrations.world.regions.RegionNames;
import danger.orespawn.integrations.world.regions.RegionState;
import danger.orespawn.integrations.world.regions.RegionTable;
import danger.orespawn.integrations.world.sightings.SightingFamilies;
import danger.orespawn.integrations.world.sightings.Sightings;
import danger.orespawn.integrations.world.sightings.SightingsData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.Optional;

/**
 * system-region-names, system-sightings and structure-chaos-antechamber: the card test lines a server can check
 * alone, run on a scratch copy of the full pack. Tests that build above their test area clear what they built.
 */
@GameTestHolder("orespawn_integrations_tests")
@PrefixGameTestTemplate(false)
public final class WorldTests {

    private WorldTests() {
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

    // ------------------------------------------------------------------ region names

    /**
     * "walk from a plains village into a BOP swamp, a jungle and a Distant Lands grove; each crossing shows the right
     * name", plus "a Selva-Old Ground border (the Old Ground wins the priority)": each biome resolves to its region
     * through the real tags, in the card's order.
     */
    @GameTest(template = "empty8")
    public static void biomesReadAsTheirRegions(GameTestHelper helper) {
        if (!Needs.mods(helper, "biomesReadAsTheirRegions", "biomesoplenty")) {
            return;
        }
        Registry<Biome> biomes = helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME);
        Map<String, Integer> expected = Map.ofEntries(
                Map.entry("minecraft:plains", 2), Map.entry("minecraft:meadow", 2),
                Map.entry("biomesoplenty:bayou", 5), Map.entry("biomesoplenty:bog", 5),
                Map.entry("minecraft:jungle", 6), Map.entry("biomesoplenty:tropics", 6),
                Map.entry("distantlandsmc:yellow_ipe_grove", 1), Map.entry("distantlandsmc:lavender_grove", 1),
                Map.entry("plantz:graveyard", 13), Map.entry("minecraft:desert", 8),
                Map.entry("minecraft:jagged_peaks", 9), Map.entry("minecraft:snowy_taiga", 10),
                Map.entry("minecraft:snowy_beach", 10), Map.entry("minecraft:dark_forest", 4),
                Map.entry("biomesoplenty:dead_forest", 4), Map.entry("minecraft:taiga", 11),
                Map.entry("minecraft:savanna", 7), Map.entry("minecraft:forest", 3),
                Map.entry("minecraft:cherry_grove", 3), Map.entry("minecraft:ocean", 12),
                Map.entry("minecraft:mushroom_fields", 12), Map.entry("minecraft:lush_caves", 14),
                Map.entry("minecraft:deep_dark", 14));
        expected.forEach((id, l) -> {
            Optional<Holder.Reference<Biome>> holder =
                    biomes.getHolder(ResourceKey.create(Registries.BIOME, ResourceLocation.parse(id)));
            helper.assertTrue(holder.isPresent(), "biome " + id + " exists");
            int region = RegionNames.regionOf(holder.get());
            helper.assertTrue(region == l, id + " reads as L" + l + ", got L" + region);
        });
        helper.succeed();
    }

    /**
     * "standing on a border does not flicker (the 10 s hold); each first visit pays one scrap and a second visit to the
     * same region pays none; a fresh join pays the spawn region's scrap at the 10 s hold".
     */
    @GameTest(template = "empty8")
    public static void holdThenOneScrapPerRegion(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper);
        player.getInventory().clearContent();
        RegionNames.setState(player, 0);
        Item scrap = BuiltInRegistries.ITEM.get(ResourceLocation.parse("orespawn_integrations:ledger_scrap"));
        for (int i = 1; i < RegionState.HOLD_STEPS; i++) {
            helper.assertTrue(!RegionNames.step(player, 2), "no crossing before the hold, step " + i);
        }
        helper.assertTrue(count(player, scrap) == 0, "nothing paid during the hold");
        helper.assertTrue(RegionNames.step(player, 2), "the tenth step crosses");
        helper.assertTrue(count(player, scrap) == 1, "the spawn region pays its scrap");
        // A border walked back and forth: never ten steps in a row, never a crossing.
        for (int i = 0; i < 30; i++) {
            helper.assertTrue(!RegionNames.step(player, i % 3 == 0 ? 2 : 5), "border flicker at " + i);
        }
        walk(player, 5);
        helper.assertTrue(count(player, scrap) == 2, "a new region pays");
        walk(player, 2);
        walk(player, 5);
        helper.assertTrue(count(player, scrap) == 2, "a second visit pays none");
        helper.assertTrue(RegionState.visited(RegionNames.getState(player), 2)
                && RegionState.visited(RegionNames.getState(player), 5), "both in the mask");
        walk(player, RegionTable.DEPTH_MEIO);
        helper.assertTrue(count(player, scrap) == 2, "depths pay no scrap");
        helper.succeed();
    }

    private static void walk(ServerPlayer player, int target) {
        for (int i = 0; i < RegionState.HOLD_STEPS; i++) {
            RegionNames.step(player, target);
        }
    }

    // ------------------------------------------------------------------ sightings

    /** "Player#isScoping() reads true server-side while the spyglass is raised". */
    @GameTest(template = "empty8")
    public static void scopingReadsServerSide(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SPYGLASS));
        helper.assertTrue(!player.isScoping(), "not raised yet");
        player.startUsingItem(InteractionHand.MAIN_HAND);
        helper.assertTrue(player.isScoping(), "raised");
        player.stopUsingItem();
        helper.succeed();
    }

    /** "a target whose start chunk is unloaded is skipped, never loaded". */
    @GameTest(template = "empty8")
    public static void unloadedChunksAreNeverLoaded(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos far = new BlockPos(29_000_000 - 4096, 70, 29_000_000 - 4096);
        helper.assertTrue(!level.getChunkSource().hasChunk(far.getX() >> 4, far.getZ() >> 4), "far chunk unloaded");
        helper.assertTrue(Sightings.structureAt(level, far) == null, "nothing found there");
        helper.assertTrue(!level.getChunkSource().hasChunk(far.getX() >> 4, far.getZ() >> 4), "still unloaded");
        helper.succeed();
    }

    /** "nothing under 32 blocks": a block 5 blocks ahead is no sighting. */
    @GameTest(template = "empty8")
    public static void nothingUnderThirtyTwo(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper);
        BlockPos stand = helper.absolutePos(new BlockPos(1, 1, 4));
        helper.getLevel().setBlockAndUpdate(stand.offset(5, 1, 0), Blocks.STONE.defaultBlockState());
        player.moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, -90.0F, 0.0F);
        helper.assertTrue(Sightings.cast(player) == null, "a block 5 blocks off is not a sighting");
        helper.succeed();
    }

    /**
     * "scope all 40 families": every family's structures and creatures are real ids in the running pack, and every
     * family has its hidden advancement loaded.
     */
    @GameTest(template = "empty8")
    public static void familiesResolveInThePack(GameTestHelper helper) {
        if (!Needs.mods(helper, "familiesResolveInThePack", "t_and_t", "ctov")) {
            return;
        }
        ServerLevel level = helper.getLevel();
        Registry<?> structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (SightingFamilies.Family f : SightingFamilies.get().all()) {
            for (String target : f.targets()) {
                ResourceLocation id = ResourceLocation.parse(target.contains(":") ? target : "minecraft:" + target);
                if (f.kind().equals("structure")) {
                    helper.assertTrue(structures.containsKey(id), "structure " + target);
                } else if (f.kind().equals("entity")) {
                    helper.assertTrue(BuiltInRegistries.ENTITY_TYPE.containsKey(id), "entity " + target);
                } else if (f.kind().equals("block")) {
                    helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(id), "block " + target);
                }
            }
            ResourceLocation adv = ResourceLocation.fromNamespaceAndPath("orespawn_integrations",
                    "avistamentos/" + f.slug());
            helper.assertTrue(level.getServer().getAdvancements().get(adv) != null, "advancement " + adv);
        }
        helper.succeed();
    }

    /** "Land Ho! fires at the fortieth stamp, whichever pages they are". */
    @GameTest(template = "empty8")
    public static void landHoAtTheFortiethStamp(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper);
        SightingsData data = SightingsData.get(player.server);
        SightingsData.Player record = data.player(player.getUUID());
        record.families = 0;
        int stamped = 0;
        // Start from the extras so the forty are not families 1-40.
        int[] order = SightingFamilies.get().all().stream().mapToInt(SightingFamilies.Family::number)
                .sorted().toArray();
        for (int i = order.length - 1; i >= 0 && stamped < 40; i--) {
            helper.assertTrue(!WorldUtil.done(player, "orespawn_integrations:avistamentos/terra_a_vista"),
                    "not before forty (" + stamped + ")");
            Sightings.stampFamily(player, data, record, order[i]);
            stamped++;
        }
        helper.assertTrue(record.stamps() == 40, "forty stamps");
        helper.assertTrue(WorldUtil.done(player, "orespawn_integrations:avistamentos/terra_a_vista"), "Land Ho!");
        helper.succeed();
    }

    // ------------------------------------------------------------------ the Antechamber

    /**
     * "each lands on a ledge at chunk centre ... two return butterflies; signs read in en_us and pt_br": the ledge
     * build, 49 obsidian, three waxed signs with translate keys, and the return butterflies.
     */
    @GameTest(template = "empty8")
    public static void ledgeBuildsWithSignsAndButterflies(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos centre = helper.absolutePos(new BlockPos(4, 2, 4));
        Antechamber.buildLedge(level, centre);
        int obsidian = 0;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (level.getBlockState(centre.offset(dx, 0, dz)).is(Blocks.OBSIDIAN)) {
                    obsidian++;
                }
            }
        }
        helper.assertTrue(obsidian == 49, "49 obsidian, got " + obsidian);
        int[][] corners = {{-3, -3}, {3, -3}, {3, 3}};
        for (int[] c : corners) {
            if (!(level.getBlockEntity(centre.offset(c[0], 1, c[1])) instanceof SignBlockEntity sign)) {
                helper.fail("no sign at " + c[0] + "," + c[1]);
                return;
            }
            Component first = sign.getFrontText().getMessage(0, false);
            helper.assertTrue(first.getContents() instanceof TranslatableContents t
                    && t.getKey().startsWith("orespawn_integrations.antechamber.sign"), "sign line is a key");
            helper.assertTrue(sign.isWaxed(), "sign waxed");
        }
        EntityType<?> butterfly = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("orespawn:butterfly"));
        var near = level.getEntities((Entity) null, new AABB(centre).inflate(16.0), e -> e.getType() == butterfly);
        helper.assertTrue(near.size() >= 2, "two return butterflies");
        Antechamber.buildLedge(level, centre);
        helper.assertTrue(level.getEntities((Entity) null, new AABB(centre).inflate(16.0),
                e -> e.getType() == butterfly).size() == near.size(), "no more butterflies when two are near");
        near.forEach(Entity::discard);
        helper.succeed();
    }

    /**
     * "a click with a held main-hand item and an empty off hand is intercepted too; a portal cooldown leaves the click
     * to OreSpawn; a Frontier holder takes the shipped landing" (and a sneak-use from Squire still takes the ledge).
     */
    @GameTest(template = "empty8")
    public static void interceptPerHandPassAndRank(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = MockPlayers.inLevel(helper);
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("orespawn:luna_moth"));
        Entity moth = type.spawn(level, helper.absolutePos(new BlockPos(2, 2, 2)), MobSpawnType.EVENT);
        if (moth == null) {
            helper.fail("no luna moth");
            return;
        }
        ItemStack stick = new ItemStack(Items.STICK);
        helper.assertTrue(Antechamber.decide(player, moth, stick) == Antechamber.Decision.PASS,
                "the main-hand pass with an item passes");
        helper.assertTrue(Antechamber.decide(player, moth, ItemStack.EMPTY) == Antechamber.Decision.LEDGE,
                "the empty off-hand pass is taken");
        moth.addTag(Antechamber.PUPPET_TAG);
        helper.assertTrue(Antechamber.decide(player, moth, ItemStack.EMPTY) == Antechamber.Decision.PASS,
                "a puppet is skipped");
        moth.removeTag(Antechamber.PUPPET_TAG);
        player.setPortalCooldown(80);
        helper.assertTrue(Antechamber.decide(player, moth, ItemStack.EMPTY) == Antechamber.Decision.PASS,
                "a portal cooldown leaves it to OreSpawn");
        player.setPortalCooldown(0);
        if (WorldUtil.grant(player, "apotheosis:progression/frontier")
                || WorldUtil.done(player, "apotheosis:progression/frontier")) {
            helper.assertTrue(Antechamber.decide(player, moth, ItemStack.EMPTY)
                    == Antechamber.Decision.SHIPPED_WITH_PERCH, "a Frontier holder takes the shipped trip");
            player.setShiftKeyDown(true);
            helper.assertTrue(Antechamber.decide(player, moth, ItemStack.EMPTY) == Antechamber.Decision.LEDGE,
                    "a sneak-use from Squire takes the ledge");
            player.setShiftKeyDown(false);
        }
        moth.discard();
        helper.succeed();
    }

    /** "one forced over void stands on a 3x3 perch": the void check and the perch. */
    @GameTest(template = "empty8")
    public static void perchOverVoid(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(4, 0, 4));
        BlockPos feet = new BlockPos(base.getX(), level.getMaxBuildHeight() - 2, base.getZ());
        helper.assertTrue(Antechamber.overVoid(level, feet), "nothing within 64 below the sky");
        Antechamber.buildPerch(level, feet.below());
        helper.assertTrue(!Antechamber.overVoid(level, feet), "the perch stands under the feet");
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos p = feet.below().offset(dx, 0, dz);
                helper.assertTrue(level.getBlockState(p).is(Blocks.OBSIDIAN), "perch block");
                level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
            }
        }
        helper.succeed();
    }

    /** "stepping off over void below Squire lands the player 2 blocks above an island top or back on the ledge". */
    @GameTest(template = "empty8")
    public static void catchLandsInTheOpen(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(4, 0, 4));
        BlockPos ledge = new BlockPos(base.getX(), level.getMaxBuildHeight() - 20, base.getZ());
        Vec3 spot = Antechamber.catchSpot(level, ledge, Vec3.atCenterOf(ledge.below(10)));
        BlockPos at = BlockPos.containing(spot);
        helper.assertTrue(level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir(),
                "the catch never sets anyone inside a block");
        helper.assertTrue(spot.y > level.getMinBuildHeight(), "above the world's floor");
        helper.succeed();
    }
}
