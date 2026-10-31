package danger.orespawn.integrations.story.week;

import danger.orespawn.integrations.OreSpawnIntegrations;
import danger.orespawn.integrations.story.StoryClock;
import danger.orespawn.integrations.story.StoryCore;
import danger.orespawn.integrations.story.firsthour.FirstHourData;
import danger.orespawn.integrations.story.firsthour.FirstHourUtil;
import danger.orespawn.integrations.story.firsthour.TicketState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * The two first-hour leftovers the 0.10.0 data could not do alone (REPORT item 11), both outside the ticket's queue:
 * <ul>
 *   <li><b>The canopy's hold</b> (system-extravio): a canopy find's actionbar line waits 10 s behind a ticket handover
 *   that is still showing. Functions cannot read the ticket, so it is mirrored once a second into the score
 *   {@code cc_senha_hold} (1 while held); a held find's function tags the player {@code cc_linha_copa_<row>} instead
 *   of printing, and the line is shown here once the hold is over.</li>
 *   <li><b>The twin's line 3</b> (wonder-gemeo-da-arvore): every log a player breaks before picking up their egg drops
 *   it again, and from the second drop on the actionbar says so ("It's looking for you."). The drop count is
 *   {@code cc_gemeo_quedas}; a break counts when it is a survival player's, the log has one of the twin's tables
 *   ({@code orespawn_integrations:arvore/<namespace>/<log>}) and {@code cc_gemeo} is still unset or 0. The line keeps
 *   the same 10 s hold.</li>
 * </ul>
 */
public final class CanopyLines {

    public static final String HOLD_SCORE = "cc_senha_hold";
    public static final String TWIN_DROPS = "cc_gemeo_quedas";
    static final String TWIN_TAG = "cc_linha_gemeo";
    static final String[] ROWS = {"arca", "chapeu", "gaveta", "parte", "presente", "retalho"};

    private CanopyLines() {
    }

    static void init() {
        StoryClock.everySecondPerPlayer("canopy_hold", CanopyLines::second);
        NeoForge.EVENT_BUS.addListener(CanopyLines::onBreak);
    }

    static boolean held(ServerPlayer player) {
        if (!FirstHourData.hasTicket(player)) {
            return false;
        }
        TicketState t = FirstHourData.ticket(player);
        return WeekRules.canopyHeld(t.open(), t.onlineTicks, t.lastHandover);
    }

    private static void second(ServerPlayer player) {
        boolean held = held(player);
        int want = held ? 1 : 0;
        if (FirstHourUtil.score(player, HOLD_SCORE, -1) != want) {
            FirstHourUtil.setScore(player, HOLD_SCORE, want);
        }
        if (held) {
            return;
        }
        for (String row : ROWS) {
            String tag = "cc_linha_copa_" + row;
            if (player.getTags().contains(tag)) {
                player.removeTag(tag);
                FirstHourUtil.actionbar(player, Component.translatable("orespawn_integrations.extravio.copa." + row));
                return;
            }
        }
        if (player.getTags().contains(TWIN_TAG)) {
            player.removeTag(TWIN_TAG);
            FirstHourUtil.actionbar(player, Component.translatable("orespawn_integrations.gemeo.again"));
        }
    }

    private static void onBreak(BlockEvent.BreakEvent event) {
        try {
            if (!(event.getPlayer() instanceof ServerPlayer player) || player.isCreative() || player.isSpectator()
                    || !event.getState().is(BlockTags.LOGS) || !ModList.get().isLoaded("brasilcoisas")
                    || FirstHourUtil.score(player, "cc_gemeo", 0) > 0) {
                return;
            }
            ResourceLocation log = BuiltInRegistries.BLOCK.getKey(event.getState().getBlock());
            ResourceKey<LootTable> table = ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath(
                    OreSpawnIntegrations.MODID, "arvore/" + log.getNamespace() + "/" + log.getPath()));
            if (player.getServer().reloadableRegistries().getLootTable(table) == LootTable.EMPTY) {
                return;
            }
            int drops = FirstHourUtil.score(player, TWIN_DROPS, 0) + 1;
            FirstHourUtil.setScore(player, TWIN_DROPS, drops);
            if (drops < 2) {
                return;
            }
            if (held(player)) {
                player.addTag(TWIN_TAG);
            } else {
                FirstHourUtil.actionbar(player, Component.translatable("orespawn_integrations.gemeo.again"));
            }
        } catch (Throwable t) {
            StoryCore.logOnce("twin_again", t);
        }
    }
}
