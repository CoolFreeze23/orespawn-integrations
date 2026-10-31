package danger.orespawn.integrations.story.firsthour;

import danger.orespawn.integrations.config.IntegrationsConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * The Egg's Label: what hatched from a player's egg, as the tags and scores every reacting card reads. The origin
 * layer becomes the tag {@code cc_origin.<path>} and the score {@code cc_ogrupo} (1-10, {@link OriginGroups}); the
 * class layer becomes {@code cc_class.<path>} and {@code cc_classe} (1-20). A layer's old tag is removed before the
 * new one is written, so an Orb re-pick leaves exactly one. With {@code story.etiqueta} off nothing is written and
 * every reacting card falls back to its default line (an unset score fails every {@code matches} test).
 */
public final class EggLabel {

    public static final String ORIGIN_TAG = "cc_origin.";
    public static final String CLASS_TAG = "cc_class.";
    public static final String GROUP_SCORE = "cc_ogrupo";
    public static final String CLASS_SCORE = "cc_classe";

    private EggLabel() {
    }

    /** Writes both layers; a null id clears that layer's tag and score. */
    public static void apply(ServerPlayer player, ResourceLocation origin, ResourceLocation playerClass) {
        if (!IntegrationsConfig.on(IntegrationsConfig.storyEtiqueta)) {
            return;
        }
        OriginGroups groups = OriginGroups.get();
        writeLayer(player, ORIGIN_TAG, origin, GROUP_SCORE, origin == null ? 0 : groups.group(origin.toString()));
        writeLayer(player, CLASS_TAG, playerClass, CLASS_SCORE,
                playerClass == null ? 0 : groups.classScore(playerClass.toString()));
    }

    private static void writeLayer(ServerPlayer player, String prefix, ResourceLocation id, String score, int value) {
        List<String> old = new ArrayList<>();
        for (String tag : player.getTags()) {
            if (tag.startsWith(prefix)) {
                old.add(tag);
            }
        }
        old.forEach(player::removeTag);
        if (id != null) {
            player.addTag(prefix + id.getPath());
        }
        if (value > 0) {
            FirstHourUtil.setScore(player, score, value);
        } else {
            FirstHourUtil.resetScore(player, score);
        }
    }

    /** The player's origin path from their label tag, or null. */
    public static String originPath(ServerPlayer player) {
        for (String tag : player.getTags()) {
            if (tag.startsWith(ORIGIN_TAG)) {
                return tag.substring(ORIGIN_TAG.length());
            }
        }
        return null;
    }
}
