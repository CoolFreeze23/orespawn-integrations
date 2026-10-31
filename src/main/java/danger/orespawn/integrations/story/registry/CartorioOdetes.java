package danger.orespawn.integrations.story.registry;

import danger.orespawn.integrations.OreSpawnIntegrations;
import danger.orespawn.integrations.config.IntegrationsConfig;
import danger.orespawn.integrations.npc.RegistroInstaller;
import danger.orespawn.integrations.story.StoryCore;
import danger.orespawn.integrations.story.StoryScheduler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.IEntity;

import java.nio.file.Files;

/**
 * The desk Odete of every new Cartório da Vila. The {@code registro/cartorio} template carries a {@code minecraft:marker}
 * tagged {@code cc_registro_odete} where she stands (a structure piece places its entities: {@code SinglePoolElement}
 * builds its settings with {@code setIgnoreEntities(false)}). When the marker joins the level we drop it and, one tick
 * later, spawn the {@code odete_registro} clone (tab 9) there through CustomNPCs' clone handler, turned the way the
 * marker faced (the template's rotation turns the marker with the building). One clone per Cartório: the marker is
 * gone once she stands, so a reload never spawns a second. With {@code [story] registry} off the marker is dropped and
 * nobody comes.
 */
public final class CartorioOdetes {

    public static final String MARKER_TAG = "cc_registro_odete";

    private CartorioOdetes() {
    }

    public static void init(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(CartorioOdetes::onJoin);
    }

    private static void onJoin(EntityJoinLevelEvent event) {
        if (event.getEntity().getType() != EntityType.MARKER || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Marker marker = (Marker) event.getEntity();
        if (!marker.getTags().contains(MARKER_TAG)) {
            return;
        }
        event.setCanceled(true);
        if (!IntegrationsConfig.on(IntegrationsConfig.storyRegistry)) {
            return;
        }
        double x = marker.getX();
        double y = marker.getY();
        double z = marker.getZ();
        float yaw = marker.getYRot();
        StoryScheduler.schedule(level.getServer(), 1, "registro.cartorio_odete", () -> spawn(level, x, y, z, yaw));
    }

    static void spawn(ServerLevel level, double x, double y, double z, float yaw) {
        try {
            if (!Files.exists(RegistroInstaller.clonePath(level.getServer(), RegistroInstaller.CLONE_DESK))
                    && !RegistroInstaller.installClones(level.getServer())) {
                OreSpawnIntegrations.LOGGER.warn("[registro] no odete_registro clone yet; the Cartorio at {} {} {} opens without its Odete",
                        (int) x, (int) y, (int) z);
                return;
            }
            IEntity<?> odete = NpcAPI.Instance().getClones().spawn(x, y, z, RegistroInstaller.CLONE_TAB,
                    RegistroInstaller.CLONE_DESK, NpcAPI.Instance().getIWorld(level));
            if (odete != null) {
                odete.setRotation(yaw);
            }
        } catch (Throwable t) {
            StoryCore.logOnce("registro.cartorio_odete", t);
        }
    }
}
