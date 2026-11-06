package danger.orespawn.integrations.story.hunt;

import danger.orespawn.integrations.config.IntegrationsConfig;
import danger.orespawn.integrations.story.StoryCore;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * The board at a Monster Hunter tent (npc-iracema, "The board at a Hunter tent", 0.10.1): a tent's Hunter is a plain
 * villager and hosts no dialog, so sneaking and using the tent's {@code monster_hunter_villager:hunters_table} opens
 * Captain Iracema's board dialog instead ({@code /noppes dialog show}, which keeps working options and quest
 * starts). A plain use keeps the shipped Hunter's Workbench. One handler on a block use, one structure lookup per
 * sneak-use; the table must stand inside a tent, so a crafted table in a base stays a workbench.
 */
public final class TentBoard {

    static final ResourceLocation TABLE = ResourceLocation.fromNamespaceAndPath("monster_hunter_villager", "hunters_table");

    private TentBoard() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(TentBoard::onUse);
    }

    private static void onUse(PlayerInteractEvent.RightClickBlock event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer player) || event.getHand() != InteractionHand.MAIN_HAND
                    || !player.isShiftKeyDown() || !IntegrationsConfig.on(IntegrationsConfig.storyRoyalHunt)) {
                return;
            }
            Block table = BuiltInRegistries.BLOCK.get(TABLE);
            if (!event.getLevel().getBlockState(event.getPos()).is(table) || !(event.getLevel() instanceof ServerLevel level)) {
                return;
            }
            StructureStart tent = level.structureManager().getStructureWithPieceAt(event.getPos(),
                    h -> h.unwrapKey().map(k -> k.location().getNamespace().equals("monster_hunter_villager")
                            && k.location().getPath().startsWith("hunters_tent_")).orElse(false));
            if (!tent.isValid()) {
                return;
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            showBoard(player);
        } catch (Throwable t) {
            StoryCore.logOnce("tent_board", t);
        }
    }

    /** Opens the Captain's board dialog for {@code player}, wherever they stand. */
    public static void showBoard(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput().withPermission(4);
        server.getCommands().performPrefixedCommand(source, "noppes dialog show " + player.getGameProfile().getName()
                + " " + HuntIds.DIALOG_BOARD + " Iracema");
    }

}
