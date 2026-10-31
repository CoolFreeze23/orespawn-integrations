package danger.orespawn.integrations.story.shelf9;

import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads a player's Neo Origins picks (the {@code neoorigins:origin} and {@code neoorigins:class} layers). Only ever
 * called behind {@code ModList.isLoaded("neoorigins")}, so this class is never loaded without the mod.
 */
final class OriginLookup {

    static final ResourceLocation ORIGIN_LAYER = ResourceLocation.fromNamespaceAndPath("neoorigins", "origin");
    static final ResourceLocation CLASS_LAYER = ResourceLocation.fromNamespaceAndPath("neoorigins", "class");

    private OriginLookup() {
    }

    /** The player's origin id, or null while none is picked yet (the Awakening screen is still open at first join). */
    static ResourceLocation origin(ServerPlayer player) {
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        return data.getOrigin(ORIGIN_LAYER);
    }

    /** Both layers' picks, origin first; missing layers are left out. */
    static List<ResourceLocation> picks(ServerPlayer player) {
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        List<ResourceLocation> out = new ArrayList<>(2);
        ResourceLocation origin = data.getOrigin(ORIGIN_LAYER);
        if (origin != null) {
            out.add(origin);
        }
        ResourceLocation cls = data.getOrigin(CLASS_LAYER);
        if (cls != null) {
            out.add(cls);
        }
        return out;
    }
}
