package danger.orespawn.integrations.npc;

import danger.orespawn.integrations.OreSpawnIntegrations;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * EXPERIMENTAL CustomNPCs (Unofficial NeoForge port) compat: installs three
 * premade OreSpawn starter quests and one hunter dialog into the world's
 * {@code <world>/customnpcs/} data folder on first boot, then drops a marker
 * file so the install never repeats.
 *
 * <p>Format and timing were both verified against
 * {@code CustomNPCs-Unofficial-NeoForge-1.21.1.20241226.jar} bytecode:
 * <ul>
 *   <li>{@code QuestController.load} scans {@code <world>/customnpcs/quests/<CategoryFolder>/<id>.json}
 *       (folder name = category title, file name = global quest id) and parses each
 *       file with the mod's own {@code NBTJsonUtil} (SNBT-flavoured JSON, e.g. {@code 0b}
 *       bytes) - the bundled resources follow that exact format. Dialogs mirror this
 *       under {@code dialogs/}.</li>
 *   <li>CustomNPCs runs {@code QuestController.load}/{@code DialogController.load} in its own
 *       <b>ServerStartedEvent</b> handler. Listener order between mods on the same event is
 *       not guaranteed, so we install on {@link ServerAboutToStartEvent} - which always fires
 *       earlier - and the content is live on the very first boot instead of the second.</li>
 * </ul>
 *
 * <p>Everything is wrapped so a failure here can never take the pack down, and
 * existing files are never overwritten (pack-author edits win).
 */
public final class QuestCompat {

    /** Marker inside <world>/customnpcs/; nothing in CustomNPCs iterates root files, so it is inert. */
    private static final String MARKER_NAME = "orespawn_integrations_quests.installed";

    /** Classpath root of the bundled quest/dialog payload. */
    private static final String RESOURCE_ROOT = "/orespawn_integrations/customnpcs/";

    /**
     * Relative install paths (identical under RESOURCE_ROOT and <world>/customnpcs/).
     * High ids (701+) dodge the default Villager dialogs (ids 1-3) CustomNPCs ships
     * and anything a pack author is likely to have hand-made.
     */
    private static final String[] FILES = {
            "quests/OreSpawn/701.json",         // Hunt: Mobzilla       (kill orespawn:godzilla)
            "quests/OreSpawn/702.json",         // Miner's Fortune      (gather 9 orespawn:uranium_nugget)
            "quests/OreSpawn/703.json",         // Tea with the Queen   (kill orespawn:the_queen; chains into 704)
            "quests/Modern Wonders/704.json",   // The Trials           (deliver 1 minecraft:trial_key)
            "quests/Modern Wonders/705.json",   // Vault Heist          (show both orespawn_integrations vault keys; kept)
            "quests/Modern Wonders/706.json",   // Enchanter of Legend  (deliver enchanted_book + 8 uranium_nugget)
            "dialogs/OreSpawn/701.json",        // The Monster Hunter   (starts quest 701 when read)
    };

    private QuestCompat() {
    }

    public static void init(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(QuestCompat::onServerAboutToStart);
    }

    private static void onServerAboutToStart(ServerAboutToStartEvent event) {
        try {
            if (!ModList.get().isLoaded("customnpcs")) {
                return; // belt-and-braces; init() is only invoked when customnpcs is present
            }
            // Same location CustomNpcs.getLevelSaveDirectory() resolves:
            // server.getWorldPath(new LevelResource("customnpcs")).
            Path cnpcDir = event.getServer().getWorldPath(new LevelResource("customnpcs"));
            Path marker = cnpcDir.resolve(MARKER_NAME);
            if (Files.exists(marker)) {
                return;
            }

            int installed = 0;
            for (String rel : FILES) {
                Path target = cnpcDir.resolve(rel);
                if (Files.exists(target)) {
                    continue; // never clobber existing world content
                }
                try (InputStream in = QuestCompat.class.getResourceAsStream(RESOURCE_ROOT + rel)) {
                    if (in == null) {
                        OreSpawnIntegrations.LOGGER.warn(
                                "[customnpcs] bundled resource missing from jar: {}{}", RESOURCE_ROOT, rel);
                        continue;
                    }
                    Files.createDirectories(target.getParent());
                    Files.copy(in, target);
                    installed++;
                }
            }

            Files.createDirectories(cnpcDir);
            Files.writeString(marker,
                    "OreSpawn Integrations premade CustomNPCs content was installed into this world.\n"
                    + "Delete this file to allow a reinstall of any quest/dialog file that is missing.\n");
            OreSpawnIntegrations.LOGGER.info(
                    "[customnpcs] installed {} premade OreSpawn quest/dialog file(s) into {} "
                    + "(quest ids 701-703 in category 'OreSpawn', 704-706 in category 'Modern Wonders', "
                    + "dialog id 701 in category 'OreSpawn'; quest 703 chains into 704-706). "
                    + "Attach dialog 701 to any NPC via /noppes to hand out the quests.",
                    installed, cnpcDir);
        } catch (Throwable t) {
            OreSpawnIntegrations.LOGGER.error(
                    "[customnpcs] premade quest install failed - CustomNPCs will simply run without "
                    + "the OreSpawn starter quests", t);
        }
    }
}
