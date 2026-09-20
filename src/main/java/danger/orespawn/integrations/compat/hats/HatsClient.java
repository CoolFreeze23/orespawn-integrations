package danger.orespawn.integrations.compat.hats;

import java.util.function.Consumer;

import net.neoforged.neoforge.common.NeoForge;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.event.GeoRenderEvent;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Client half of {@link HatsCompat}: attaches the {@link OreSpawnHatLayer} to every GeckoLib renderer the port owns.
 * GeckoLib posts {@code CompileRenderLayers} on the game bus when a renderer is built (the replaced-entity event for
 * the port's rigs today; the plain entity event is listened to as well so a future native GeckoLib entity in the port
 * is covered), which is the sanctioned way to add a layer to another mod's renderer - it needs no access to the
 * dispatcher's renderer map and no change in the port.
 *
 * <p>Referenced only from a {@code FMLEnvironment.dist.isClient()} branch, so it is never classloaded on a dedicated
 * server (house idiom, {@code compat/travelersbackpack/OreSpawnPacksClient}).
 */
final class HatsClient {

    private HatsClient() {
    }

    static void register() {
        HatAnchors.load();
        NeoForge.EVENT_BUS.addListener(HatsClient::onCompileReplaced);
        NeoForge.EVENT_BUS.addListener(HatsClient::onCompileEntity);
    }

    private static void onCompileReplaced(GeoRenderEvent.ReplacedEntity.CompileRenderLayers event) {
        attach(event.getRenderer(), event::addLayer);
    }

    private static void onCompileEntity(GeoRenderEvent.Entity.CompileRenderLayers event) {
        attach(event.getRenderer(), event::addLayer);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void attach(GeoRenderer<?> renderer, Consumer<GeoRenderLayer> add) {
        if (renderer == null || !renderer.getClass().getName().startsWith("danger.orespawn.")) {
            return;
        }
        add.accept(make(renderer));
    }

    private static <T extends GeoAnimatable> GeoRenderLayer<T> make(GeoRenderer<T> renderer) {
        return new OreSpawnHatLayer<>(renderer);
    }
}
