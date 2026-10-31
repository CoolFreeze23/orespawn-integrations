package danger.orespawn.integrations.story.firsthour;

import com.cyberday1.neoorigins.api.event.OriginChangedEvent;
import com.cyberday1.neoorigins.api.origin.OriginLayer;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.cyberday1.neoorigins.data.LayerDataManager;
import danger.orespawn.integrations.story.StoryCore;
import danger.orespawn.integrations.story.StoryScheduler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.io.Reader;
import java.util.Optional;

/**
 * Neo Origins into the Egg's Label: on login and after Neo Origins' {@code OriginChangedEvent} (posted on the NeoForge
 * bus before the new origin is stored, and cancellable), the player's {@code PlayerOriginData} is read for both
 * layers, {@code neoorigins:origin} and {@code neoorigins:class}, one tick later, so a cancelled change writes
 * nothing new. Also tells the Priority Ticket when the origin screen (P00) has closed: every enabled layer chosen.
 * Only loaded when Neo Origins is present.
 */
public final class EtiquetaHandler {

    public static final ResourceLocation ORIGIN_LAYER = ResourceLocation.fromNamespaceAndPath("neoorigins", "origin");
    public static final ResourceLocation CLASS_LAYER = ResourceLocation.fromNamespaceAndPath("neoorigins", "class");
    private static final ResourceLocation GROUPS_FILE = FirstHourUtil.oi("origin_groups.json");

    private EtiquetaHandler() {
    }

    public static void init(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(EtiquetaHandler::onLogin);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EtiquetaHandler::onOriginChanged);
        NeoForge.EVENT_BUS.addListener(EtiquetaHandler::onReload);
        FirstHourScenes.originChosen = EtiquetaHandler::originScreenClosed;
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        try {
            if (event.getEntity() instanceof ServerPlayer player) {
                refresh(player);
            }
        } catch (Throwable t) {
            StoryCore.logOnce("etiqueta_login", t);
        }
    }

    private static void onOriginChanged(OriginChangedEvent event) {
        try {
            if (event.isCanceled()) {
                return;
            }
            ServerPlayer player = event.getEntity();
            if (player.getServer() != null) {
                StoryScheduler.schedule(player.getServer(), 1, "etiqueta_change", () -> refresh(player));
            }
        } catch (Throwable t) {
            StoryCore.logOnce("etiqueta_change", t);
        }
    }

    /** Reads both layers and writes the label. */
    public static void refresh(ServerPlayer player) {
        if (player.isRemoved()) {
            return;
        }
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        EggLabel.apply(player, data.getOrigin(ORIGIN_LAYER), data.getOrigin(CLASS_LAYER));
    }

    /** P00's close: the origin chosen, and the class too when its layer is on. */
    private static boolean originScreenClosed(ServerPlayer player) {
        try {
            PlayerOriginData data = player.getData(OriginAttachments.originData());
            if (!layerOn(ORIGIN_LAYER) || data.hasOriginForLayer(ORIGIN_LAYER)) {
                return !layerOn(CLASS_LAYER) || data.hasOriginForLayer(CLASS_LAYER);
            }
            return false;
        } catch (Throwable t) {
            StoryCore.logOnce("etiqueta_p00", t);
            return true;
        }
    }

    private static boolean layerOn(ResourceLocation id) {
        OriginLayer layer = LayerDataManager.INSTANCE.getLayer(id);
        return layer != null && layer.enabled() && !layer.hidden() && !layer.autoChoose();
    }

    /** Reloads {@code origin_groups.json} with the datapacks, so a datapack may override it. */
    private static void onReload(AddReloadListenerEvent event) {
        event.addListener(new SimplePreparableReloadListener<OriginGroups>() {
            @Override
            protected OriginGroups prepare(ResourceManager manager, ProfilerFiller profiler) {
                Optional<Resource> resource = manager.getResource(GROUPS_FILE);
                if (resource.isEmpty()) {
                    return null;
                }
                try (Reader reader = resource.get().openAsReader()) {
                    return OriginGroups.parse(reader);
                } catch (Exception e) {
                    StoryCore.logOnce("etiqueta_groups", e);
                    return null;
                }
            }

            @Override
            protected void apply(OriginGroups groups, ResourceManager manager, ProfilerFiller profiler) {
                if (groups != null) {
                    OriginGroups.set(groups);
                }
            }
        });
    }
}
