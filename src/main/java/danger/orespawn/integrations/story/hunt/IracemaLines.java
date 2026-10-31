package danger.orespawn.integrations.story.hunt;

import danger.orespawn.integrations.story.Ranks;
import danger.orespawn.integrations.story.firsthour.EggLabel;
import danger.orespawn.integrations.story.firsthour.FirstHourUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Captain Iracema's once-per-player lines that need the player's own data (npc-iracema, "Her reveal lines"): R2 at a
 * first greeting, naming the Egg's Label word for the player's group, with the Royal Blood bow before it; and, once a
 * player is a Knight, the Knight line with the paired rank title. Her greeting dialog's opening command tags the
 * player {@code cc_iracema_ola}, and the board's clock pass calls {@link #greet}.
 */
final class IracemaLines {

    private IracemaLines() {
    }

    static void greet(ServerPlayer player) {
        CompoundTag state = player.getData(MondayBoard.STATE.get()).copy();
        if (!state.getBoolean("greeted")) {
            state.putBoolean("greeted", true);
            if (player.getTags().contains(EggLabel.ORIGIN_TAG + "royal_blood")) {
                MondayBoard.say(player, Component.translatable(MondayBoard.KEY + "royal_blood"));
            }
            int group = Math.max(1, Math.min(10, FirstHourUtil.score(player, EggLabel.GROUP_SCORE, 10)));
            MondayBoard.say(player, Component.translatable(MondayBoard.KEY + "r2"));
            MondayBoard.say(player, Component.translatable(MondayBoard.KEY + "r2.2",
                    Component.translatable(MondayBoard.KEY + "group." + group)));
            FirstHourUtil.award(player, "livro/r2");
        }
        if (Ranks.atLeast(player, Ranks.KNIGHT) && !state.getBoolean("knight")) {
            state.putBoolean("knight", true);
            MondayBoard.say(player, Component.translatable(MondayBoard.KEY + "knight",
                    Component.translatable("orespawn_integrations.court.rank." + Ranks.KNIGHT)));
        }
        player.setData(MondayBoard.STATE.get(), state);
    }
}
