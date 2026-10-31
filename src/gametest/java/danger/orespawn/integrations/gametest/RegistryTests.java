package danger.orespawn.integrations.gametest;

import danger.orespawn.integrations.config.IntegrationsConfig;
import danger.orespawn.integrations.npc.RegistroInstaller;
import danger.orespawn.integrations.story.registry.RegistryCore;
import danger.orespawn.integrations.story.registry.ScrapCaps;
import me.guivnf.mods.hats.common.hat.HatDefinition;
import me.guivnf.mods.hats.common.hat.HatPart;
import me.guivnf.mods.hats.common.hat.HatRegistry;
import me.guivnf.mods.hats.common.world.HatsSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreHolder;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

/**
 * The registry group's card test lines a server can check without a human: the installer's clone files, the
 * averbação (a slip now, the copy refused the same day and handed out the next, the rewind guard), the desk list, the
 * field Correction's refusals and its refile, the twin guard, the ovinho's chick, the scrap caps, the Table's recipes
 * and the Cartório template. They run on a scratch copy of the full pack, where every partner mod is present.
 */
@GameTestHolder("orespawn_integrations_tests")
@PrefixGameTestTemplate(false)
public final class RegistryTests {

    private static final ResourceLocation REGISTRO = ResourceLocation.fromNamespaceAndPath("orespawn_integrations", "registro");

    private RegistryTests() {
    }

    // ---- helpers

    private static ServerPlayer player(GameTestHelper helper) {
        RegistryCore.writeSwitches(helper.getLevel().getServer());
        ServerPlayer player = MockPlayers.inLevel(helper);
        player.getInventory().clearContent();
        return player;
    }

    private static void run(ServerPlayer player, String command) {
        MinecraftServer server = player.getServer();
        server.getCommands().performPrefixedCommand(
                player.createCommandSourceStack().withPermission(2).withSuppressedOutput(), command);
    }

    private static Item item(ResourceLocation id) {
        return BuiltInRegistries.ITEM.get(id);
    }

    private static int count(ServerPlayer player, ResourceLocation id) {
        Item item = item(id);
        int n = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) {
                n += stack.getCount();
            }
        }
        return n;
    }

    private static void setScore(ServerPlayer player, String objective, int value) {
        Objective obj = player.getServer().getScoreboard().getObjective(objective);
        player.getServer().getScoreboard().getOrCreatePlayerScore(ScoreHolder.forNameOnly(player.getScoreboardName()), obj).set(value);
    }

    private static int today(GameTestHelper helper) {
        return (int) (helper.getLevel().getDayTime() / 24000L % 2147483647L);
    }

    private static InteractionResult interact(ServerPlayer player, Entity target) {
        return CommonHooks.onInteractEntity(player, target, InteractionHand.MAIN_HAND);
    }

    // ---- npc-odetes: the installer

    /** The versioned installer seeded both clones (tab 9) and its marker at server start. */
    @GameTest(template = "empty8")
    public static void installerWroteBothClones(GameTestHelper helper) {
        if (!IntegrationsConfig.on(IntegrationsConfig.storyRegistry) || !ModList.get().isLoaded("customnpcs")) {
            helper.succeed();
            return;
        }
        MinecraftServer server = helper.getLevel().getServer();
        helper.succeedWhen(() -> {
            helper.assertTrue(Files.exists(RegistroInstaller.clonePath(server, RegistroInstaller.CLONE_DESK)), "odete_registro clone missing");
            helper.assertTrue(Files.exists(RegistroInstaller.clonePath(server, RegistroInstaller.CLONE_FIELD)), "odete_campo clone missing");
        });
    }

    /** The Cartório wrapper is 7x9 and carries the desk Odete's marker; the hub desk fits 12x12. */
    @GameTest(template = "empty8")
    public static void cartorioTemplates(GameTestHelper helper) {
        var manager = helper.getLevel().getStructureManager();
        StructureTemplate cartorio = manager.get(ResourceLocation.fromNamespaceAndPath("orespawn_integrations", "registro/cartorio")).orElse(null);
        StructureTemplate balcao = manager.get(ResourceLocation.fromNamespaceAndPath("orespawn_integrations", "registro/balcao")).orElse(null);
        helper.assertTrue(cartorio != null && balcao != null, "registro templates missing");
        helper.assertTrue(cartorio.getSize().getX() == 7 && cartorio.getSize().getZ() == 9, "cartorio is not 7x9");
        helper.assertTrue(balcao.getSize().getX() <= 12 && balcao.getSize().getZ() <= 12, "desk over 12x12");
        CompoundTag saved = cartorio.save(new CompoundTag());
        helper.assertTrue(saved.getList("entities", Tag.TAG_COMPOUND).toString().contains("cc_registro_odete"), "no Odete marker");
        helper.succeed();
    }

    // ---- system-corrections: the averbação

    /** One scrap registered: one slip now, the copy refused the same day and handed out the next. */
    @GameTest(template = "empty8")
    public static void averbarSlipNowCopyTomorrow(GameTestHelper helper) {
        if (!IntegrationsConfig.on(IntegrationsConfig.storyCorrections)) {
            helper.succeed();
            return;
        }
        ServerPlayer player = player(helper);
        player.getInventory().add(new ItemStack(item(ScrapCaps.SCRAP)));
        run(player, "function orespawn_integrations:odete/averbar");
        helper.assertTrue(count(player, ScrapCaps.SCRAP) == 0, "the scrap was not taken");
        helper.assertTrue(count(player, ScrapCaps.SLIP) == 1, "no slip handed now");
        run(player, "function orespawn_integrations:odete/segunda_via");
        helper.assertTrue(count(player, ScrapCaps.SLIP) == 1, "the copy came out the same day");
        // The next day: the copy was filed yesterday.
        setScore(player, "cc_via_dia", today(helper) - 1);
        setScore(player, "cc_via_reg", today(helper) - 1);
        run(player, "function orespawn_integrations:odete/segunda_via");
        helper.assertTrue(count(player, ScrapCaps.SLIP) == 2, "the copy was not handed the next day");
        run(player, "function orespawn_integrations:odete/segunda_via");
        helper.assertTrue(count(player, ScrapCaps.SLIP) == 2, "the copy was handed twice");
        helper.succeed();
    }

    /** No scrap, no registration. */
    @GameTest(template = "empty8")
    public static void averbarWithoutScrap(GameTestHelper helper) {
        if (!IntegrationsConfig.on(IntegrationsConfig.storyCorrections)) {
            helper.succeed();
            return;
        }
        ServerPlayer player = player(helper);
        run(player, "function orespawn_integrations:odete/averbar");
        helper.assertTrue(count(player, ScrapCaps.SLIP) == 0, "a slip without a scrap");
        helper.succeed();
    }

    /** The rewind guard: a copy filed on a later day than today (the clock went back) is handed out. */
    @GameTest(template = "empty8")
    public static void averbarRewindGuard(GameTestHelper helper) {
        if (!IntegrationsConfig.on(IntegrationsConfig.storyCorrections)) {
            helper.succeed();
            return;
        }
        ServerPlayer player = player(helper);
        setScore(player, "cc_vias", 1);
        setScore(player, "cc_via_dia", today(helper) + 5);
        setScore(player, "cc_via_reg", today(helper) + 5);
        run(player, "function orespawn_integrations:odete/segunda_via");
        helper.assertTrue(count(player, ScrapCaps.SLIP) == 1, "a copy stranded by a /time set");
        helper.succeed();
    }

    // ---- npc-odetes: the desk list

    /** ops/registro_listar adds the desk once, refuses the same block twice, and stops at 32. */
    @GameTest(template = "empty8")
    public static void registroListar(GameTestHelper helper) {
        if (!IntegrationsConfig.on(IntegrationsConfig.storyRegistry)) {
            helper.succeed();
            return;
        }
        MinecraftServer server = helper.getLevel().getServer();
        CompoundTag before = server.getCommandStorage().get(REGISTRO).copy();
        try {
            ServerPlayer player = player(helper);
            server.getCommandStorage().set(REGISTRO, new CompoundTag());
            run(player, "function orespawn_integrations:ops/registro_listar");
            run(player, "function orespawn_integrations:ops/registro_listar");
            ListTag desks = server.getCommandStorage().get(REGISTRO).getList("desks", Tag.TAG_COMPOUND);
            helper.assertTrue(desks.size() == 1, "expected one desk, got " + desks.size());
            int[] pos = desks.getCompound(0).getIntArray("pos");
            BlockPos at = player.blockPosition();
            helper.assertTrue(pos.length == 3 && pos[0] == at.getX() && pos[1] == at.getY() && pos[2] == at.getZ(), "wrong desk position");
            CompoundTag full = new CompoundTag();
            ListTag list = new ListTag();
            for (int i = 0; i < 32; i++) {
                CompoundTag desk = new CompoundTag();
                desk.putIntArray("pos", new int[]{i, -60, 100000});
                desk.putString("dim", "minecraft:overworld");
                list.add(desk);
            }
            full.put("desks", list);
            server.getCommandStorage().set(REGISTRO, full);
            run(player, "function orespawn_integrations:ops/registro_listar");
            helper.assertTrue(server.getCommandStorage().get(REGISTRO).getList("desks", Tag.TAG_COMPOUND).size() == 32, "desk list past 32");
        } finally {
            server.getCommandStorage().set(REGISTRO, before);
        }
        helper.succeed();
    }

    // ---- item-ledger-scrap: the field Correction

    /** On a bare cow the scrap is kept and the click passes. */
    @GameTest(template = "empty8")
    public static void scrapOnBareCowKept(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item(ScrapCaps.SCRAP)));
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(2, 2, 2));
        interact(player, cow);
        helper.assertTrue(player.getMainHandItem().getCount() == 1, "scrap spent on a bare cow");
        helper.succeed();
    }

    /** On another player's wolf, and on a player, the scrap is kept and the click is answered. */
    @GameTest(template = "empty8")
    public static void scrapRefusalsKeepScrap(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item(ScrapCaps.SCRAP)));
        Wolf wolf = helper.spawn(EntityType.WOLF, new BlockPos(2, 2, 2));
        wolf.setTame(true, false);
        wolf.setOwnerUUID(UUID.randomUUID());
        if (ModList.get().isLoaded("hats") && IntegrationsConfig.on(IntegrationsConfig.storyFieldCorrection)) {
            helper.assertTrue(interact(player, wolf) == InteractionResult.SUCCESS, "another player's wolf not refused");
            ServerPlayer other = MockPlayers.inLevel(helper);
            helper.assertTrue(interact(player, other) == InteractionResult.SUCCESS, "a player not refused");
        }
        helper.assertTrue(player.getMainHandItem().getCount() == 1, "scrap spent on a refusal");
        helper.succeed();
    }

    /** A scrap on a hatted cow: the hat goes to the Wardrobe, the head is bare, the scrap is gone. */
    @GameTest(template = "empty8")
    public static void scrapOnHattedCowRefiles(GameTestHelper helper) {
        if (!ModList.get().isLoaded("hats") || !IntegrationsConfig.on(IntegrationsConfig.storyFieldCorrection)
                || HatRegistry.getAll().isEmpty()) {
            helper.succeed();
            return;
        }
        ServerPlayer player = player(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item(ScrapCaps.SCRAP)));
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(2, 2, 2));
        HatDefinition definition = HatRegistry.getAll().iterator().next();
        HatsSavedData data = HatsSavedData.get(helper.getLevel());
        data.setEntityHat(cow.getUUID(), new HatPart(definition.name));
        helper.assertTrue(interact(player, cow) == InteractionResult.SUCCESS, "field Correction did not take the click");
        helper.assertTrue(data.getEntityHat(cow.getUUID()) == null, "the cow still wears the hat");
        helper.assertTrue(data.hasHat(player.getUUID(), definition.name), "the hat is not in the Wardrobe");
        helper.assertTrue(player.getMainHandItem().isEmpty(), "the scrap was not spent");
        helper.succeed();
    }

    // ---- item-ledger-scrap: the twin guard and the ovinho's chick

    /** A second player's crouch-tap on an owned ovinho is refused. */
    @GameTest(template = "empty8")
    public static void twinGuardRefusesStranger(GameTestHelper helper) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.fromNamespaceAndPath("brasilcoisas", "ovinho_vivo")).orElse(null);
        if (type == null) {
            helper.succeed();
            return;
        }
        ServerPlayer player = player(helper);
        Entity ovinho = helper.spawn(type, new BlockPos(2, 2, 2));
        if (ovinho instanceof TamableAnimal tamable) {
            tamable.setTame(true, false);
            tamable.setOwnerUUID(UUID.randomUUID());
        }
        player.setShiftKeyDown(true);
        helper.assertTrue(interact(player, ovinho) == InteractionResult.SUCCESS, "a stranger's crouch-tap was not refused");
        helper.succeed();
    }

    /** A killed ovinho leaves a pintinho. */
    @GameTest(template = "empty8")
    public static void ovinhoDeathLeavesChick(GameTestHelper helper) {
        EntityType<?> ovinhoType = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.fromNamespaceAndPath("brasilcoisas", "ovinho_vivo")).orElse(null);
        EntityType<?> chickType = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.fromNamespaceAndPath("brasilcoisas", "pintinho")).orElse(null);
        if (ovinhoType == null || chickType == null) {
            helper.succeed();
            return;
        }
        Entity ovinho = helper.spawn(ovinhoType, new BlockPos(2, 2, 2));
        ovinho.kill();
        helper.succeedWhen(() -> helper.assertEntityPresent(chickType));
    }

    // ---- item-ledger-scrap: the caps

    /** The kite cap stops at 10 and the notebook cap at 8. */
    @GameTest(template = "empty8")
    public static void scrapCaps(GameTestHelper helper) {
        if (!IntegrationsConfig.on(IntegrationsConfig.storyScraps)) {
            helper.succeed();
            return;
        }
        ServerPlayer player = player(helper);
        setScore(player, ScrapCaps.Source.KITE.objective, 0);
        setScore(player, ScrapCaps.Source.NOTEBOOK.objective, 0);
        for (int i = 0; i < 12; i++) {
            ScrapCaps.give(player, ScrapCaps.Source.KITE, 1);
        }
        helper.assertTrue(count(player, ScrapCaps.SCRAP) == 10, "kite cap is not 10");
        player.getInventory().clearContent();
        for (int i = 0; i < 12; i++) {
            run(player, "function orespawn_integrations:retalho/caderneta");
        }
        helper.assertTrue(count(player, ScrapCaps.SCRAP) == 8, "notebook cap is not 8");
        helper.succeed();
    }

    // ---- system-corrections: the Table

    /** All 22 Corrections load, each shapeless with one payment. */
    @GameTest(template = "empty8")
    public static void correctionRecipesLoad(GameTestHelper helper) {
        if (!Needs.mods(helper, "correctionRecipesLoad", "distantlandsmc", "hbmsntm")) {
            return;
        }
        List<ResourceLocation> ids = helper.getLevel().getRecipeManager().getRecipeIds()
                .filter(id -> id.getNamespace().equals("orespawn_integrations") && id.getPath().startsWith("correction/"))
                .toList();
        helper.assertTrue(ids.size() == 22, "expected 22 Corrections, found " + ids.size());
        helper.succeed();
    }
}
