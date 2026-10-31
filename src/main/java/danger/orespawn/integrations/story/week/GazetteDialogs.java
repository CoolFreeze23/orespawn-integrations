package danger.orespawn.integrations.story.week;

import danger.orespawn.integrations.OreSpawnIntegrations;
import danger.orespawn.integrations.story.StoryCore;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The Gazette at the Registry desk (item-diario-oficial, "Subscribe"): dialog 711, the desk's Gazette page with its
 * list-mode options (Subscribe, Cancel subscription, Any post for me?), and one more option in the desk dialog 710's
 * free slot 5 that opens it. Run on {@code ServerAboutToStartEvent} at the lowest priority, after the Registry's own
 * installer has written 710 and before CustomNPCs loads its dialogs at server start. Both steps are idempotent: 711 is
 * written only when missing, and 710 is patched only while it lacks our option, so a hub world made on 0.10.0 gets the
 * page too. The options run {@code /cc gazeta ... @dp}.
 */
public final class GazetteDialogs {

    static final String DESK_OPTION = "orespawn_integrations.gazette.option.gazette";
    private static final String RESOURCE = "/orespawn_integrations/customnpcs/dialogs/Registro/711.json";

    private GazetteDialogs() {
    }

    public static void init(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, GazetteDialogs::onServerAboutToStart);
    }

    private static void onServerAboutToStart(ServerAboutToStartEvent event) {
        try {
            Path dir = event.getServer().getWorldPath(new LevelResource("customnpcs")).resolve("dialogs")
                    .resolve("Registro");
            Path page = dir.resolve("711.json");
            if (!Files.exists(page)) {
                try (InputStream in = GazetteDialogs.class.getResourceAsStream(RESOURCE)) {
                    if (in != null) {
                        Files.createDirectories(dir);
                        Files.copy(in, page);
                    }
                }
            }
            Path desk = dir.resolve("710.json");
            if (Files.exists(desk)) {
                String text = Files.readString(desk, StandardCharsets.UTF_8);
                String patched = patchDesk(text);
                if (!patched.equals(text)) {
                    Files.writeString(desk, patched, StandardCharsets.UTF_8);
                    OreSpawnIntegrations.LOGGER.info("[gazette] desk dialog 710 gained the Gazette option");
                }
            }
        } catch (Throwable t) {
            StoryCore.logOnce("gazette.dialogs", t);
        }
    }

    /** Adds the Gazette option in slot 5 of the desk dialog's option list, once. */
    static String patchDesk(String text) {
        if (text.contains(DESK_OPTION) || text.contains("\"OptionSlot\": 5")) {
            return text;
        }
        String anchor = "\"Options\": [";
        int at = text.indexOf(anchor);
        if (at < 0) {
            return text;
        }
        String option = "\n        {\n"
                + "            \"Option\": {\n"
                + "                \"OptionType\": 1,\n"
                + "                \"Title\": \"" + DESK_OPTION + "\",\n"
                + "                \"Dialog\": 711,\n"
                + "                \"DialogCommand\": \"\",\n"
                + "                \"DialogColor\": 14737632\n"
                + "            },\n"
                + "            \"OptionSlot\": 5\n"
                + "        },";
        int insert = at + anchor.length();
        return text.substring(0, insert) + option + text.substring(insert);
    }
}
