package danger.orespawn.integrations.npc;

import danger.orespawn.integrations.OreSpawnIntegrations;
import danger.orespawn.integrations.config.IntegrationsConfig;
import danger.orespawn.integrations.story.StoryCore;
import danger.orespawn.integrations.story.StoryScheduler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.handler.data.IDialog;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The Registry's versioned CustomNPCs installer (0.10.0 on). The shipped {@link QuestCompat} installs its quests once
 * and then skips any world holding {@code orespawn_integrations_quests.installed}; this installer keeps its own marker
 * per content version beside it ({@code orespawn_integrations_registro_<version>.installed}), so the hub and every
 * world made before 0.10.0 still get the Registry. It places no NPC.
 *
 * <p>Two steps, each timed against {@code CustomNPCs-Unofficial-NeoForge-1.21.1.20241226.jar}:
 * <ul>
 *   <li><b>Dialogs</b>, on {@link ServerAboutToStartEvent}, before CustomNPCs' own ServerStarted handler runs
 *       {@code DialogController.load} over {@code <world>/customnpcs/dialogs/<Category>/<id>.json}. The files follow
 *       the shipped quest dialogs' format (SNBT-flavoured JSON read by {@code NBTJsonUtil}). Existing files are never
 *       overwritten. Dialog text and option titles hold translate keys only: the dialog screen passes every text and
 *       title through {@code NoppesStringUtils.formatText}, which looks the whole string up with
 *       {@code Language.getOrDefault} on the client, so each player reads the key in their own language.</li>
 *   <li><b>The two clones</b>, one tick after ServerStarted (CustomNPCs' controllers are up by then):
 *       {@code clones/9/odete_registro.json} (the desk Odete) and {@code clones/9/odete_campo.json} (the Field
 *       Odete, spawned and removed by the first-hour code). A clone file is the NPC's full entity NBT
 *       ({@code ServerCloneController.set} saves {@code saveAsPassenger} and strips position and UUID), and its
 *       readers take no defaults (a missing {@code MaxHealth} reads 0), so the NPC is built through CustomNPCs' own
 *       API and saved by CustomNPCs itself rather than written by hand. The NPC name is a translate key too:
 *       {@code EntityNPCInterface.getName} wraps it in {@code Component.translatable}.</li>
 * </ul>
 * The marker is written only once both clones exist, so a failed step retries at the next boot. Off with
 * {@code [story] registry}. Dialog option commands only run with {@code enable-command-block=true} (CustomNPCs'
 * {@code NoppesUtilServer.runCommand} refuses otherwise; the pack's server.properties has it on).
 */
public final class RegistroInstaller {

    /** Content version of the Registry install; raise it whenever the dialogs or clones change. */
    public static final String VERSION = "0.10.0";
    public static final String MARKER_NAME = "orespawn_integrations_registro_" + VERSION + ".installed";

    /** OI's clone tab (CmdClone takes an integer tab). */
    public static final int CLONE_TAB = 9;
    public static final String CLONE_DESK = "odete_registro";
    public static final String CLONE_FIELD = "odete_campo";

    /**
     * The desk dialog (category Registro) and the Field Odete's four greetings: 720 morning (her clone's own dialog),
     * 721 afternoon, 722 evening, 723 a veteran's Back-Dated Fall. The first-hour code opens the one the clock asks
     * for with {@code /noppes dialog show}. Ids 710+ sit beside the shipped 701-706.
     */
    public static final int DIALOG_DESK = 710;
    public static final int DIALOG_FIELD = 720;
    public static final int DIALOG_FIELD_AFTERNOON = 721;
    public static final int DIALOG_FIELD_EVENING = 722;
    public static final int DIALOG_FIELD_VETERAN = 723;

    private static final String RESOURCE_ROOT = "/orespawn_integrations/customnpcs/";
    private static final String[] DIALOG_FILES = {
            "dialogs/Registro/" + DIALOG_DESK + ".json",
            "dialogs/Registro/" + DIALOG_FIELD + ".json",
            "dialogs/Registro/" + DIALOG_FIELD_AFTERNOON + ".json",
            "dialogs/Registro/" + DIALOG_FIELD_EVENING + ".json",
            "dialogs/Registro/" + DIALOG_FIELD_VETERAN + ".json",
    };

    private RegistroInstaller() {
    }

    static Path customNpcsDir(MinecraftServer server) {
        return server.getWorldPath(new LevelResource("customnpcs"));
    }

    public static Path clonePath(MinecraftServer server, String name) {
        return customNpcsDir(server).resolve("clones").resolve(Integer.toString(CLONE_TAB)).resolve(name + ".json");
    }

    static void onServerAboutToStart(ServerAboutToStartEvent event) {
        try {
            if (!IntegrationsConfig.on(IntegrationsConfig.storyRegistry)) {
                return;
            }
            Path dir = customNpcsDir(event.getServer());
            if (Files.exists(dir.resolve(MARKER_NAME))) {
                return;
            }
            int written = 0;
            for (String rel : DIALOG_FILES) {
                Path target = dir.resolve(rel);
                if (Files.exists(target)) {
                    continue;
                }
                try (InputStream in = RegistroInstaller.class.getResourceAsStream(RESOURCE_ROOT + rel)) {
                    if (in == null) {
                        OreSpawnIntegrations.LOGGER.warn("[registro] bundled dialog missing from jar: {}", rel);
                        continue;
                    }
                    Files.createDirectories(target.getParent());
                    Files.copy(in, target);
                    written++;
                }
            }
            OreSpawnIntegrations.LOGGER.info("[registro] {} Registry dialog file(s) written for install {}", written, VERSION);
        } catch (Throwable t) {
            StoryCore.logOnce("registro.installer.dialogs", t);
        }
    }

    static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        if (!IntegrationsConfig.on(IntegrationsConfig.storyRegistry)) {
            return;
        }
        if (Files.exists(customNpcsDir(server).resolve(MARKER_NAME))) {
            return;
        }
        // One tick later every mod's ServerStarted handler has run, CustomNPCs' dialog and clone controllers included.
        StoryScheduler.schedule(server, 1, "registro.installer.clones", () -> installClones(server));
    }

    /** Builds whichever clone file is missing, then writes the version marker. Safe to call again. */
    public static boolean installClones(MinecraftServer server) {
        try {
            if (!NpcAPI.IsAvailable() || NpcAPI.Instance().getClones() == null) {
                OreSpawnIntegrations.LOGGER.warn("[registro] CustomNPCs API not ready; the clones wait for the next boot");
                return false;
            }
            ServerLevel level = server.overworld();
            boolean ok = true;
            if (!Files.exists(clonePath(server, CLONE_DESK))) {
                ok &= saveClone(level, CLONE_DESK, "orespawn_integrations.odete.npc.name",
                        "orespawn_integrations.odete.npc.title", DIALOG_DESK);
            }
            if (!Files.exists(clonePath(server, CLONE_FIELD))) {
                ok &= saveClone(level, CLONE_FIELD, "orespawn_integrations.odete_campo.npc.name",
                        "orespawn_integrations.odete_campo.npc.title", DIALOG_FIELD);
            }
            if (ok && Files.exists(clonePath(server, CLONE_DESK)) && Files.exists(clonePath(server, CLONE_FIELD))) {
                Path dir = customNpcsDir(server);
                Files.createDirectories(dir);
                Files.writeString(dir.resolve(MARKER_NAME),
                        "OreSpawn Integrations Registry install " + VERSION + " (dialogs 710, 720-723; clones "
                        + CLONE_DESK + " and " + CLONE_FIELD + " in tab " + CLONE_TAB + ").\n"
                        + "Delete this file to rebuild any missing dialog or clone file at the next boot.\n");
                OreSpawnIntegrations.LOGGER.info("[registro] Registry install {} complete: clones {} and {} in tab {}",
                        VERSION, CLONE_DESK, CLONE_FIELD, CLONE_TAB);
                return true;
            }
            return false;
        } catch (Throwable t) {
            StoryCore.logOnce("registro.installer.clones", t);
            return false;
        }
    }

    /**
     * One Odete, built in memory (never added to the level) and saved by CustomNPCs' clone handler: a standing clerk
     * who keeps her post, with the Odete skin from OI's assets and her dialog in slot 0.
     */
    private static boolean saveClone(ServerLevel level, String cloneName, String nameKey, String titleKey, int dialogId) {
        ICustomNpc<?> npc = NpcAPI.Instance().createNPC(level);
        if (npc == null) {
            return false;
        }
        npc.getDisplay().setName(nameKey);
        npc.getDisplay().setTitle(titleKey);
        npc.getDisplay().setSkinTexture("orespawn_integrations:textures/entity/npc/" + cloneName + ".png");
        npc.getAi().setMovingType(0);
        npc.getAi().setReturnsHome(true);
        npc.getAi().setStopOnInteract(true);
        IDialog dialog = NpcAPI.Instance().getDialogs().get(dialogId);
        if (dialog == null) {
            // Dialog files land before CustomNPCs loads them; one missing here was refused or deleted. Retry next boot.
            OreSpawnIntegrations.LOGGER.warn("[registro] dialog {} not loaded; clone {} waits for the next boot", dialogId, cloneName);
            return false;
        }
        npc.setDialog(0, dialog);
        NpcAPI.Instance().getClones().set(CLONE_TAB, cloneName, npc);
        return true;
    }
}
