package danger.orespawn.integrations.compat.walkers;

import danger.orespawn.ModEntities;
import danger.orespawn.integrations.OreSpawnIntegrations;
import danger.orespawn.integrations.compat.walkers.bespoke.GodzillaAtomicBreathAbility;
import danger.orespawn.integrations.compat.walkers.bespoke.KingThunderVolleyAbility;
import danger.orespawn.integrations.compat.walkers.bespoke.KrakenTentacleGrabAbility;
import danger.orespawn.integrations.compat.walkers.bespoke.MothraWingGustAbility;
import danger.orespawn.integrations.compat.walkers.bespoke.WaterDragonWaterBallAbility;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import tocraft.walkers.ability.AbilityRegistry;
import tocraft.walkers.integrations.AbstractIntegration;

/**
 * Walkers integration body. Instantiated by walkers itself (via the supplier
 * handed to {@code Integrations.register}) and its register* callbacks are
 * re-invoked after every datapack (re)load, right after the registries are
 * cleared - the only registration path that survives /reload.
 */
public final class OreSpawnWalkersIntegration extends AbstractIntegration {

    /** The pack's five ultrabosses (mirrors data/orespawn_integrations/walkers/blacklist.json). */
    private static final Set<ResourceLocation> ULTRABOSSES = Set.of(
            ResourceLocation.fromNamespaceAndPath("orespawn", "the_king"),
            ResourceLocation.fromNamespaceAndPath("orespawn", "the_queen"),
            ResourceLocation.fromNamespaceAndPath("orespawn", "kraken"),
            ResourceLocation.fromNamespaceAndPath("orespawn", "godzilla"),
            ResourceLocation.fromNamespaceAndPath("orespawn", "mothra"));

    @Override
    public void registerAbilities() {
        // Predicate registration covers every orespawn mob implementing
        // RangedAttackMob without instantiating any entity (the predicate runs
        // against the player's stored shape instance at lookup time). Curated
        // datapack abilities still win for their mobs: AbilityRegistry.get()
        // consults the generic (JSON) map after the predicate map.
        AbilityRegistry.registerByPredicate(
                entity -> entity instanceof RangedAttackMob
                        && "orespawn".equals(EntityType.getKey(entity.getType()).getNamespace()),
                new NativeRangedAttackAbility());

        // Bespoke boss abilities (compat.walkers.bespoke), registered AFTER the
        // generic predicate on purpose: AbilityRegistry.get() walks the
        // insertion-ordered (LinkedHashMap) specific-ability map and keeps the
        // LAST matching entry (verified against 5.8.12 bytecode), so these
        // per-type registrations beat both walkers' defaults and the generic
        // ranged ability above. Datapack JSON abilities are GenericShapeAbility
        // instances routed into a separate map that get() consults afterwards
        // and therefore always win - which is why none of these five mobs may
        // ever appear in data/orespawn_integrations/walkers/abilities/.
        try {
            AbilityRegistry.registerByType(ModEntities.MOTHRA.get(), new MothraWingGustAbility());
            AbilityRegistry.registerByType(ModEntities.KRAKEN.get(), new KrakenTentacleGrabAbility());
            AbilityRegistry.registerByType(ModEntities.GODZILLA.get(), new GodzillaAtomicBreathAbility());
            AbilityRegistry.registerByType(ModEntities.THE_KING.get(), new KingThunderVolleyAbility());
            AbilityRegistry.registerByType(ModEntities.WATER_DRAGON.get(), new WaterDragonWaterBallAbility());
        } catch (Throwable t) {
            // A port-side registry change must not break the whole integration:
            // the generic ranged ability and datapack abilities still work.
            OreSpawnIntegrations.LOGGER.error("Bespoke walkers boss abilities failed to register - continuing without them", t);
        }
    }

    /**
     * Ultrabosses and anything tagged {@code #c:bosses} see through morphs:
     * walkers' targeting mixins consult this before letting a hostile ignore a
     * morphed player (config hostilesIgnoreHostileShapedPlayer), and a
     * {@code true} here forces the attack regardless of that config.
     */
    @Override
    public boolean mightAttackInnocent(Mob mob, Player player) {
        EntityType<?> type = mob.getType();
        return type.is(WalkersCompat.BOSSES_TAG) || ULTRABOSSES.contains(EntityType.getKey(type));
    }
}
