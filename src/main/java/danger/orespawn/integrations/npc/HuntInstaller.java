package danger.orespawn.integrations.npc;

import danger.orespawn.integrations.OreSpawnIntegrations;
import danger.orespawn.integrations.config.IntegrationsConfig;
import danger.orespawn.integrations.story.StoryCore;
import danger.orespawn.integrations.story.StoryScheduler;
import danger.orespawn.integrations.story.hunt.HuntIds;
import danger.orespawn.integrations.story.hunt.TentBoard;
import danger.orespawn.integrations.story.hunt.WallOfTheWanted;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Marker;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.api.entity.IPlayer;
import noppes.npcs.api.handler.data.IDialog;
import noppes.npcs.api.handler.data.IQuest;
import noppes.npcs.api.handler.data.IQuestObjective;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The Royal Hunt's CustomNPCs half (npc-iracema, 0.10.1), on the Registry installer's versioned route: its own marker
 * per content version ({@code orespawn_integrations_hunt_0.10.1.installed}) beside the Registry's, so every world, the
 * hub included, gets it once.
 *
 * <ul>
 *   <li><b>Dialogs and quests</b>, on {@link ServerAboutToStartEvent} (before CustomNPCs loads them): Iracema's
 *   dialogs 730-732 and 741-743 are written when missing. The shipped quests 701-706 and dialog 701 (and the
 *   Registry desk, dialog 710, which gains "The Captain's board") are replaced by their re-keyed versions only while
 *   the world's copy still carries the original text, so an operator's edited file is kept. Quest progress lives in
 *   player data under the same ids, so nobody's progress changes.</li>
 *   <li><b>Her clone</b> {@code iracema} (tab 9), one tick after ServerStarted, built through CustomNPCs' API with her
 *   greeting as its dialog, and saved by CustomNPCs; the marker is written once it exists.</li>
 *   <li><b>Her spawn</b>: the Lodge template carries a {@code minecraft:marker} tagged {@code cc_iracema}; when it
 *   joins a level it is dropped and the clone is spawned there (generated Lodges and {@code /cc hunt_lodge place}).</li>
 *   <li><b>The kill bridge</b>: the re-keyed kill objectives name their target by a lang key, so CustomNPCs' own
 *   kill count (which compares the entity's id or name) no longer matches; a kill of Mobzilla or the Queen credited
 *   to a player with 701 or 703 active fills that objective through the API.</li>
 *   <li>The tent board (sneak-use on a Hunter's Workbench), which opens her board with {@code /noppes dialog show}.</li>
 * </ul>
 */
public final class HuntInstaller {

    public static final String VERSION = "0.10.1";
    public static final String MARKER_NAME = "orespawn_integrations_hunt_" + VERSION + ".installed";

    private static final String NEW_ROOT = "/orespawn_integrations/customnpcs_0101/";
    private static final String OLD_ROOT = "/orespawn_integrations/customnpcs/";
    private static final String[] NEW_FILES = {
            "dialogs/Caca/730.json", "dialogs/Caca/731.json", "dialogs/Caca/732.json",
            "dialogs/Caca/741.json", "dialogs/Caca/742.json", "dialogs/Caca/743.json",
    };
    /** Files replaced while the world's copy still matches the shipped original. */
    private static final String[] REKEYED = {
            "quests/OreSpawn/701.json", "quests/OreSpawn/702.json", "quests/OreSpawn/703.json",
            "quests/Modern Wonders/704.json", "quests/Modern Wonders/705.json", "quests/Modern Wonders/706.json",
            "dialogs/OreSpawn/701.json", "dialogs/Registro/710.json",
    };
    private static final String[] TEXT_FIELDS = {"Title", "Text", "CompleteText", "DialogTitle", "DialogText"};
    /** The re-keyed kill quests and their targets. */
    private static final Map<String, Integer> KILL_QUESTS = Map.of("orespawn:godzilla", 701, "orespawn:the_queen", 703);

    private HuntInstaller() {
    }

    public static void init(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(HuntInstaller::onServerAboutToStart);
        NeoForge.EVENT_BUS.addListener(HuntInstaller::onServerStarted);
        NeoForge.EVENT_BUS.addListener(HuntInstaller::onJoin);
        NeoForge.EVENT_BUS.addListener(HuntInstaller::onDeath);
        TentBoard.init();
    }

    static Path dir(MinecraftServer server) {
        return RegistroInstaller.customNpcsDir(server);
    }

    public static Path clonePath(MinecraftServer server) {
        return dir(server).resolve("clones").resolve(Integer.toString(HuntIds.CLONE_TAB)).resolve(HuntIds.CLONE_IRACEMA + ".json");
    }

    // --- dialogs and quests ---

    private static void onServerAboutToStart(ServerAboutToStartEvent event) {
        try {
            if (!IntegrationsConfig.on(IntegrationsConfig.storyRoyalHunt)) {
                return;
            }
            Path dir = dir(event.getServer());
            if (Files.exists(dir.resolve(MARKER_NAME))) {
                return;
            }
            int written = 0;
            for (String rel : NEW_FILES) {
                Path target = dir.resolve(rel);
                if (!Files.exists(target) && copy(NEW_ROOT + rel, target)) {
                    written++;
                }
            }
            int rekeyed = 0;
            for (String rel : REKEYED) {
                Path target = dir.resolve(rel);
                if (!Files.exists(target)) {
                    // QuestCompat installs the originals first; a desk or quest file missing here is simply new.
                    if (copy(NEW_ROOT + rel, target)) {
                        rekeyed++;
                    }
                    continue;
                }
                String current = Files.readString(target, StandardCharsets.UTF_8);
                String original = resource(OLD_ROOT + rel);
                if (original != null && fields(current).equals(fields(original))) {
                    if (copy(NEW_ROOT + rel, target)) {
                        rekeyed++;
                    }
                } else if (original != null && !fields(current).equals(fields(resource(NEW_ROOT + rel)))) {
                    OreSpawnIntegrations.LOGGER.info("[hunt] {} was edited in this world; it is kept as it is", rel);
                }
            }
            OreSpawnIntegrations.LOGGER.info("[hunt] {} new dialog file(s) and {} re-keyed file(s) for install {}",
                    written, rekeyed, VERSION);
        } catch (Throwable t) {
            StoryCore.logOnce("hunt.installer.files", t);
        }
    }

    /** The text fields of a CustomNPCs file, in order: what an operator's edit would change. */
    static List<String> fields(String file) {
        List<String> out = new ArrayList<>();
        if (file == null) {
            return out;
        }
        for (String name : TEXT_FIELDS) {
            String tag = "\"" + name + "\": \"";
            int i = file.indexOf(tag);
            while (i >= 0) {
                int j = i + tag.length();
                int k = j;
                while (k < file.length() && file.charAt(k) != '"') {
                    k += file.charAt(k) == '\\' ? 2 : 1;
                }
                out.add(name + "=" + file.substring(j, Math.min(k, file.length())).replace("\r", ""));
                i = file.indexOf(tag, k);
            }
        }
        return out;
    }

    private static String resource(String path) throws java.io.IOException {
        try (InputStream in = HuntInstaller.class.getResourceAsStream(path)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static boolean copy(String resource, Path target) throws java.io.IOException {
        try (InputStream in = HuntInstaller.class.getResourceAsStream(resource)) {
            if (in == null) {
                OreSpawnIntegrations.LOGGER.warn("[hunt] bundled file missing from jar: {}", resource);
                return false;
            }
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            return true;
        }
    }

    // --- her clone ---

    private static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        if (!IntegrationsConfig.on(IntegrationsConfig.storyRoyalHunt) || Files.exists(dir(server).resolve(MARKER_NAME))) {
            return;
        }
        StoryScheduler.schedule(server, 1, "hunt.installer.clone", () -> installClone(server));
    }

    public static boolean installClone(MinecraftServer server) {
        try {
            if (!NpcAPI.IsAvailable() || NpcAPI.Instance().getClones() == null) {
                return false;
            }
            if (!Files.exists(clonePath(server))) {
                ICustomNpc<?> npc = NpcAPI.Instance().createNPC(server.overworld());
                IDialog greeting = NpcAPI.Instance().getDialogs().get(HuntIds.DIALOG_GREETING);
                if (npc == null || greeting == null) {
                    OreSpawnIntegrations.LOGGER.warn("[hunt] dialog {} not loaded; Iracema's clone waits for the next boot",
                            HuntIds.DIALOG_GREETING);
                    return false;
                }
                npc.getDisplay().setName("orespawn_integrations.iracema.npc.name");
                npc.getDisplay().setTitle("orespawn_integrations.iracema.npc.title");
                npc.getDisplay().setSkinTexture("orespawn_integrations:textures/entity/npc/iracema.png");
                npc.getAi().setMovingType(0);
                npc.getAi().setReturnsHome(true);
                npc.getAi().setStopOnInteract(true);
                npc.setDialog(0, greeting);
                NpcAPI.Instance().getClones().set(HuntIds.CLONE_TAB, HuntIds.CLONE_IRACEMA, npc);
            }
            if (Files.exists(clonePath(server))) {
                Files.writeString(dir(server).resolve(MARKER_NAME), "OreSpawn Integrations Royal Hunt install " + VERSION
                        + " (dialogs 730-732, 741-743; quests 701-706 and dialogs 701 and 710 re-keyed where unedited;"
                        + " clone iracema in tab " + HuntIds.CLONE_TAB + ").\n"
                        + "Delete this file to rebuild any missing file at the next boot.\n");
                return true;
            }
            return false;
        } catch (Throwable t) {
            StoryCore.logOnce("hunt.installer.clone", t);
            return false;
        }
    }

    // --- her spawn at a Lodge ---

    private static void onJoin(EntityJoinLevelEvent event) {
        if (event.getEntity().getType() != EntityType.MARKER || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Marker marker = (Marker) event.getEntity();
        if (!marker.getTags().contains(HuntIds.MARKER_TAG)) {
            return;
        }
        event.setCanceled(true);
        if (!IntegrationsConfig.on(IntegrationsConfig.storyRoyalHunt)) {
            return;
        }
        double x = marker.getX();
        double y = marker.getY();
        double z = marker.getZ();
        float yaw = marker.getYRot();
        StoryScheduler.schedule(level.getServer(), 1, "hunt.iracema_spawn", () -> {
            try {
                if (!Files.exists(clonePath(level.getServer())) && !installClone(level.getServer())) {
                    OreSpawnIntegrations.LOGGER.warn("[hunt] no iracema clone yet; the Lodge at {} {} {} opens without her",
                            (int) x, (int) y, (int) z);
                    return;
                }
                IEntity<?> iracema = NpcAPI.Instance().getClones().spawn(x, y, z, HuntIds.CLONE_TAB,
                        HuntIds.CLONE_IRACEMA, NpcAPI.Instance().getIWorld(level));
                if (iracema != null) {
                    iracema.setRotation(yaw);
                }
            } catch (Throwable t) {
                StoryCore.logOnce("hunt.iracema_spawn", t);
            }
        });
    }

    // --- the kill bridge for the re-keyed 701 and 703 ---

    private static void onDeath(LivingDeathEvent event) {
        try {
            LivingEntity dead = event.getEntity();
            if (dead.level().isClientSide()) {
                return;
            }
            Integer quest = KILL_QUESTS.get(BuiltInRegistries.ENTITY_TYPE.getKey(dead.getType()).toString());
            ServerPlayer killer = quest == null ? null : WallOfTheWanted.creditOf(dead);
            if (killer == null || !NpcAPI.IsAvailable()) {
                return;
            }
            if (!(NpcAPI.Instance().getIEntity(killer) instanceof IPlayer<?> player) || !player.hasActiveQuest(quest)) {
                return;
            }
            for (IQuest q : player.getActiveQuests()) {
                if (q.getId() != quest) {
                    continue;
                }
                for (IQuestObjective o : q.getObjectives(player)) {
                    if (o.getProgress() < o.getMaxProgress()) {
                        o.setProgress(o.getMaxProgress());
                    }
                }
            }
        } catch (Throwable t) {
            StoryCore.logOnce("hunt.kill_bridge", t);
        }
    }
}
