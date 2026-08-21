package danger.orespawn.integrations.compat.curios;

import danger.orespawn.integrations.OreSpawnIntegrations;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import org.slf4j.Logger;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.List;

/**
 * Curios integration: turns five existing OreSpawn drops into wearable trinkets.
 *
 * <ul>
 *   <li>kraken_repellent — ward: Kraken-family mobs will not target the wearer</li>
 *   <li>godzilla_scale — +4 max health, +1 armor toughness</li>
 *   <li>basilisk_scale — +2 armor</li>
 *   <li>water_dragon_scale — water breathing while equipped</li>
 *   <li>tigers_eye_ingot — +0.5 attack damage, +1 luck</li>
 * </ul>
 *
 * <p>All five are ALWAYS_KEEP on death inside OreSpawn dimensions (see
 * {@link OreSpawnCurio}). Slot wiring ("charm") is data-driven and lives in
 * {@code data/curios/tags/item/charm.json} + {@code data/orespawn_integrations/curios/}.
 *
 * <p>Only ever classloaded when the curios mod is present — the main mod class
 * invokes {@link #init(IEventBus)} reflectively.
 */
public final class CuriosCompat {

    private static final Logger LOGGER = OreSpawnIntegrations.LOGGER;

    /** Namespace shared by OreSpawn's registries and its dimension ids. */
    static final String ORESPAWN_NAMESPACE = "orespawn";

    /**
     * Resolved during common setup; the ward handler no-ops while this is null,
     * so a missing/renamed item degrades to "no ward" instead of crashing.
     */
    private static volatile Item krakenRepellent;

    private CuriosCompat() {}

    public static void init(IEventBus modBus) {
        // Item registry lookups + CuriosApi.registerCurio belong in common setup:
        // registries are frozen and Curios' item->ICurioItem map is ready by then.
        // enqueueWork keeps the map writes on the main thread.
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(CuriosCompat::registerCurios));
        NeoForge.EVENT_BUS.addListener(CuriosCompat::onLivingChangeTarget);
    }

    private static void registerCurios() {
        try {
            krakenRepellent = register("kraken_repellent", new OreSpawnCurio(
                    List.of(), false,
                    "curios.orespawn_integrations.kraken_repellent.tooltip"));

            register("godzilla_scale", new OreSpawnCurio(
                    List.of(new OreSpawnCurio.Bonus(Attributes.MAX_HEALTH, "max_health", 4.0D),
                            new OreSpawnCurio.Bonus(Attributes.ARMOR_TOUGHNESS, "armor_toughness", 1.0D)),
                    false, null));

            register("basilisk_scale", new OreSpawnCurio(
                    List.of(new OreSpawnCurio.Bonus(Attributes.ARMOR, "armor", 2.0D)),
                    false, null));

            register("water_dragon_scale", new OreSpawnCurio(
                    List.of(), true,
                    "curios.orespawn_integrations.water_dragon_scale.tooltip"));

            register("tigers_eye_ingot", new OreSpawnCurio(
                    List.of(new OreSpawnCurio.Bonus(Attributes.ATTACK_DAMAGE, "attack_damage", 0.5D),
                            new OreSpawnCurio.Bonus(Attributes.LUCK, "luck", 1.0D)),
                    false, null));
        } catch (Throwable t) {
            // A Curios API mismatch must degrade this module, never kill the pack.
            LOGGER.error("Curios trinket registration failed - OreSpawn trinkets inactive", t);
        }
    }

    /**
     * Looks up an existing OreSpawn item and attaches curio behavior to it.
     *
     * @return the resolved item, or null if OreSpawn does not expose that id
     */
    private static Item register(String path, OreSpawnCurio behavior) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ORESPAWN_NAMESPACE, path);
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item == null) {
            LOGGER.warn("OreSpawn item {} not found - curio behavior skipped", id);
            return null;
        }
        CuriosApi.registerCurio(item, behavior);
        return item;
    }

    /**
     * Kraken ward: cancels any orespawn "kraken"-family mob acquiring a player
     * wearing the repellent in a curios slot as its target.
     */
    private static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        Item repellent = krakenRepellent;
        if (repellent == null || !(event.getNewAboutToBeSetTarget() instanceof Player player)) {
            return;
        }
        ResourceLocation attackerId = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType());
        if (!ORESPAWN_NAMESPACE.equals(attackerId.getNamespace())
                || !attackerId.getPath().contains("kraken")) {
            return;
        }
        try {
            boolean warded = CuriosApi.getCuriosInventory(player)
                    .flatMap(inventory -> inventory.findFirstCurio(repellent))
                    .isPresent();
            if (warded) {
                event.setCanceled(true);
            }
        } catch (Exception e) {
            // Never let a curios inventory hiccup break mob AI ticking.
            LOGGER.debug("Kraken ward check failed for {}", player.getScoreboardName(), e);
        }
    }
}
