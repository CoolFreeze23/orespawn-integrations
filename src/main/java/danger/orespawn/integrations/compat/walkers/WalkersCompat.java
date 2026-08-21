package danger.orespawn.integrations.compat.walkers;

import danger.orespawn.integrations.OreSpawnIntegrations;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import tocraft.walkers.api.PlayerShapeChanger;
import tocraft.walkers.api.variant.ShapeType;
import tocraft.walkers.impl.PlayerDataProvider;
import tocraft.walkers.integrations.Integrations;

/**
 * Entry point for the Walkers (Woodwalkers) integration. Only classloaded when
 * the "walkers" mod is present - {@link danger.orespawn.integrations.OreSpawnIntegrations}
 * invokes {@link #init(IEventBus)} reflectively after a ModList check.
 */
public final class WalkersCompat {

    /** Entity-type tag marking OreSpawn boss mobs; killing one absorbs its shape. */
    static final TagKey<EntityType<?>> BOSSES_TAG =
            TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("c", "bosses"));

    private WalkersCompat() {
    }

    public static void init(IEventBus modBus) {
        // Must run before the first datapack load: walkers wipes every Java-side
        // ability/trait registration on each (re)load via Registry.clearAll() and
        // then re-invokes the callbacks of integrations registered here. Routing
        // everything through Integrations.register is what makes the content in
        // OreSpawnWalkersIntegration survive /reload.
        Integrations.register("orespawn", OreSpawnWalkersIntegration::new);
        NeoForge.EVENT_BUS.addListener(WalkersCompat::onBossKilled);
    }

    /**
     * Auto-grant: a player killing an orespawn {@code #c:bosses} mob absorbs its
     * shape as their walkers 2nd shape. The pack blacklists the ultrabosses from
     * the normal U-hold unlock scan, but {@code PlayerShapeChanger.change2ndShape}
     * only fires the cancellable UNLOCK_SHAPE event and never consults
     * EntityBlacklist (verified against 5.8.12 bytecode: the entity blacklist is
     * enforced solely in KeyPressHandler/UnlockPackets behind
     * blacklistPreventsUnlocking, and SwapPackets only checks the player-UUID
     * blacklist) - so this API path bypasses the scan blacklist by design and the
     * granted shape remains fully morphable.
     */
    private static void onBossKilled(LivingDeathEvent event) {
        LivingEntity died = event.getEntity();
        if (died.level().isClientSide()) {
            return;
        }
        EntityType<?> type = died.getType();
        if (!"orespawn".equals(EntityType.getKey(type).getNamespace()) || !type.is(BOSSES_TAG)) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        try {
            // from(LivingEntity) also captures the killed mob's variant data.
            ShapeType<LivingEntity> shape = ShapeType.from(died);
            if (shape == null) {
                return;
            }
            if (player instanceof PlayerDataProvider data && shape.equals(data.walkers$get2ndShape())) {
                return; // already absorbed this exact shape - no regrant spam
            }
            if (PlayerShapeChanger.change2ndShape(player, shape)) {
                player.displayClientMessage(Component.translatable(
                        "message.orespawn_integrations.shape_absorbed", died.getDisplayName()), false);
            }
        } catch (Throwable t) {
            OreSpawnIntegrations.LOGGER.error("Walkers shape auto-grant failed for {} - continuing",
                    EntityType.getKey(type), t);
        }
    }
}
