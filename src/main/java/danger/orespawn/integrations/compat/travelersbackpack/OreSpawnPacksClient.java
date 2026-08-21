package danger.orespawn.integrations.compat.travelersbackpack;

import com.tiviacz.travelersbackpack.client.renderer.BackpackItemStackRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

import java.util.function.Supplier;

/**
 * Client-only half of {@link OreSpawnPacksCompat}: registers Traveler's
 * Backpack's own {@link BackpackItemStackRenderer} as the item renderer for the
 * three OreSpawn packs. TB only wires this extension for items in its own
 * DeferredRegister (verified in ModClientEventHandler.registerClientExtenstions,
 * travelersbackpack-neoforge-1.21.1-10.1.38 bytecode), and its backpack loader
 * model bakes no standard quads — without the extension a foreign backpack item
 * is invisible in the inventory, in hand, and worn. Mirrors TB's own anonymous
 * IClientItemExtensions: renderer constructed lazily so no client classes load
 * before the first render.
 *
 * <p>Referenced only from a {@code FMLEnvironment.dist.isClient()} branch so
 * this class is never classloaded on a dedicated server.</p>
 */
final class OreSpawnPacksClient {

    private OreSpawnPacksClient() {}

    static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            private final Supplier<BlockEntityWithoutLevelRenderer> renderer =
                    () -> new BackpackItemStackRenderer(
                            Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                            Minecraft.getInstance().getEntityModels());

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return this.renderer.get();
            }
        },
                OreSpawnPacksCompat.MOBZILLA_HIDE_BACKPACK.get(),
                OreSpawnPacksCompat.KRAKEN_BACKPACK.get(),
                OreSpawnPacksCompat.GIRLFRIEND_BACKPACK.get());
    }
}
