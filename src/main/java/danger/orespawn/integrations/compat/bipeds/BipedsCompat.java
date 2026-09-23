package danger.orespawn.integrations.compat.bipeds;

import danger.orespawn.integrations.OreSpawnIntegrations;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * The Girlfriend and the Boyfriend on the vanilla player model, for the pack's player animation mods.
 *
 *
 *
 * <p>Entity Model Features replaces a model by the layer it is baked from ({@code EntityModelSet.bakeLayer}), for any
 * renderer, and animates it through every {@code LivingEntityRenderer}. OreSpawn draws the pair with its own GeckoLib
 * rigs, which EMF cannot touch; drawn instead with the vanilla player model (the {@code player_slim} layer for her, her
 * 1.7.10 arms are three pixels wide, {@code player} for him), whatever player animation pack is enabled animates them
 * at run time. Nothing of that pack is copied or shipped here: Fresh Animations and its Player Extension are FreshLX's
 * (the Player Extension all rights reserved; their terms let a modpack include the packs, not redistribute or ship
 * their assets). With Better Combat, a weapon Better Combat knows swings with that weapon's own attack animation, played
 * through playerAnimator (MIT) from Better Combat's registered animations, again nothing copied.</p>
 *
 * <p>Gates: the renderers when EMF or Better Combat is loaded and {@code [compat] player_model_bipeds} (restart); the
 * swings when Better Combat and playerAnimator are loaded and {@code [compat] better_combat_bipeds}. Client only.</p>
 */
public final class BipedsCompat {

    private BipedsCompat() {
    }

    public static void init(IEventBus modBus) {
        final boolean emf = ModList.get().isLoaded("entity_model_features");
        final boolean betterCombat = ModList.get().isLoaded("bettercombat") && ModList.get().isLoaded("playeranimator");
        if (!emf && !betterCombat) {
            OreSpawnIntegrations.LOGGER.info("Bipeds compat: neither Entity Model Features nor Better Combat present, the pair keeps OreSpawn's rigs");
            return;
        }
        if (FMLEnvironment.dist.isClient()) {
            BipedsClient.register(modBus, emf, betterCombat);
            OreSpawnIntegrations.LOGGER.info("Bipeds compat: the Girlfriend and the Boyfriend on the player model (EMF {}, Better Combat {})",
                    emf, betterCombat);
        }
    }
}
