package danger.orespawn.integrations.compat.hats;

import danger.orespawn.integrations.OreSpawnIntegrations;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * Hats Renewed x OreSpawn: hats drawn on the port's GeckoLib rigs at each species' head bone.
 *
 * <p>Hats Renewed (21.1.1) places a hat on a GeckoLib entity from its dispatcher fallback: it finds a bone
 * from a short name list ({@code head}, {@code Head}, {@code skull}, {@code neck}, ...), assumes that bone's pivot sits at
 * the bottom-centre of the head cube and lifts the hat by the cube's height, then scales the hat by the mob's
 * bounding-box width ({@code MixinEntityRenderDispatcher.hats$fallbackRender}, {@code GeckoLibCompat.buildMatrix}). The
 * port's rigs were converted from the 1.7.10 models, whose rotation points sit at the top, the middle or a corner of
 * the head; a fifth of the species name their head bone differently or have no head at all; and body width says
 * nothing about head size. So hats floated, sank, sat off-centre or came out three times too big.
 *
 * <p>A {@link OreSpawnHatLayer} on every OreSpawn GeckoLib renderer (attached through GeckoLib's
 * CompileRenderLayers events, so nothing in the port changes) draws the hat with Hats Renewed's own renderer at the
 * true top-centre of the species' designated head bone - the bone's cubes rotated by the bone's rest tilt through its
 * parent chain - upright at rest and following the bone's animation, sized from the head's own width. Hats Renewed's
 * per-entity placement file (offsets, rotation, scale; the in-game placement editor writes it) still applies on top.
 * The anchors are {@code assets/orespawn_integrations/hats/anchors.json}, one line per species, with an optional pack
 * override at {@code config/orespawn_integrations/hat_anchors.json} ({@link HatAnchors}). Species that are not
 * creatures (the coin, the T-shirt, the islands, the vortex, the Crystal orbs, the hoverboard) get no hat at all.
 *
 * <p>Client only. The server half of Hats (who wears what, the spawn roll) is untouched; classic-rendered species (the
 * two robots, the cows, the boss head parts) keep Hats Renewed's own vanilla-model placement. Toggle:
 * {@code [compat] hats_on_rigs} in the mod's config.
 */
public final class HatsCompat {

    private HatsCompat() {
    }

    public static void init(IEventBus modBus) {
        if (!ModList.get().isLoaded("geckolib") || !ModList.get().isLoaded("orespawn")) {
            OreSpawnIntegrations.LOGGER.info("Hats Renewed compat: OreSpawn or GeckoLib absent, nothing to place hats on");
            return;
        }
        if (FMLEnvironment.dist.isClient()) {
            HatsClient.register();
            OreSpawnIntegrations.LOGGER.info("Hats Renewed compat: OreSpawn rigs wear their hats on the head bone");
        }
    }
}
