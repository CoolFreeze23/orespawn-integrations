package danger.orespawn.integrations.compat.apotheosis;

import danger.orespawn.entity.LaserBall;
import danger.orespawn.integrations.OreSpawnIntegrations;
import danger.orespawn.item.ItemRayGun;
import danger.orespawn.item.ItemSquidZooka;
import dev.shadowsoffire.apotheosis.Apoth;
import dev.shadowsoffire.apotheosis.affix.AffixHelper;
import dev.shadowsoffire.apotheosis.loot.LootCategory;
import dev.shadowsoffire.apotheosis.socket.SocketHelper;
import dev.shadowsoffire.apothic_attributes.api.ALObjects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * The {@code orespawn_integrations:orespawn_gun} Apotheosis loot category and
 * its projectile plumbing. Only ever classloaded from
 * {@link ApotheosisCompat} once a {@code RegisterEvent} for
 * {@code apotheosis:loot_category} fires, i.e. when Apotheosis is present.
 *
 * <p>API notes (all verified against the shipped jars via javap):
 * <ul>
 * <li>{@code LootCategory(Predicate, EntitySlotGroup)} is public and defaults
 * priority to 1000; instances are plain registry values in the (synced,
 * non-frozen-to-addons) custom registry {@code Apoth.BuiltInRegs.LOOT_CATEGORY}.
 * Apotheosis itself registers its categories through Placebo's DeferredHelper,
 * which also just funnels into {@code RegisterEvent} - so addon registration
 * here uses the exact same mechanism and timing.</li>
 * <li>{@code LootCategory.forItem} consults the
 * {@code apotheosis:loot_category_overrides} item data map first; our datapack
 * half maps ray_gun/squid_zooka onto this category. The validator predicate
 * below is belt-and-braces for direct construction paths.</li>
 * <li>Gun damage is hardcoded in the projectile/mob entities
 * ({@code LaserBall.onHitEntity} does {@code hurt(damageSources().thrown(...), 16f)});
 * the guns carry no attribute modifiers. Damage-affixing still works because
 * ApothicAttributes' {@code AttributeEvents.projDmg} multiplies any
 * {@code LivingIncomingDamageEvent} whose direct entity is a {@code Projectile}
 * by the owner's {@code apothic_attributes:projectile_damage} value, and
 * {@code apothCriticalStrike} rolls {@code crit_chance} for any living attacker.
 * Both attributes are granted while the gun is held (MAINHAND slot group).</li>
 * <li>Apotheosis' own {@code AdventureEvents.fireProjectile} only copies
 * affixes onto a projectile when the mainhand category {@code isRanged()},
 * which is hardcoded to BOW/TRIDENT identity - a custom category can never
 * pass it. The {@link #onGunProjectileSpawn} hook below therefore replicates
 * that handler for {@link LaserBall} (the only {@code Projectile} the guns
 * fire; the Squid Zooka launches an {@code AttackSquid} mob instead) and then
 * sets {@code apoth.generated} so Apotheosis' NORMAL-priority handler skips
 * its offhand fallback for this entity.</li>
 * </ul>
 */
final class OreSpawnGunCategory {

    /** Registry name of the category; also referenced by the datapack half. */
    static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(OreSpawnIntegrations.MODID, "orespawn_gun");

    /** NBT flag Apotheosis uses to mark already-processed projectiles. */
    private static final String APOTH_GENERATED = "apoth.generated";

    private static LootCategory gunCategory;

    private OreSpawnGunCategory() {
    }

    static void register(RegisterEvent event) {
        gunCategory = new LootCategory(OreSpawnGunCategory::isGun, ALObjects.EquipmentSlotGroups.MAINHAND);
        event.register(Apoth.BuiltInRegs.LOOT_CATEGORY.key(), ID, () -> gunCategory);
        // HIGH priority: must beat Apotheosis' @SubscribeEvent (NORMAL) handler
        // on the same event so the gun's affixes win over the offhand fallback.
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, OreSpawnGunCategory::onGunProjectileSpawn);
    }

    /** Validator for {@code LootCategory.forItem} fallback resolution. */
    private static boolean isGun(ItemStack stack) {
        return stack.getItem() instanceof ItemRayGun || stack.getItem() instanceof ItemSquidZooka;
    }

    /**
     * Mirrors {@code AdventureEvents.fireProjectile} for Ray Gun laser balls:
     * fires gem/affix onProjectileFired callbacks and stores the source weapon
     * on the projectile ({@code apoth.source_weapon}) so that
     * {@code AdventureEvents.impact} triggers {@code projectile_target}
     * mob-effect affixes (e.g. our "Antigravitic") on hit.
     */
    private static void onGunProjectileSpawn(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof LaserBall laser)) {
            return;
        }
        if (laser.getPersistentData().getBoolean(APOTH_GENERATED)) {
            return;
        }
        if (!(laser.getOwner() instanceof LivingEntity living)) {
            return;
        }
        // ItemRayGun.use fires from whichever hand held it; find that hand.
        ItemStack gun = living.getMainHandItem();
        if (!(gun.getItem() instanceof ItemRayGun)) {
            gun = living.getOffhandItem();
            if (!(gun.getItem() instanceof ItemRayGun)) {
                return; // mob-fired LaserBall (irukandji etc.) - not ours.
            }
        }
        final ItemStack weapon = gun;
        SocketHelper.getGems(weapon).onProjectileFired(living, laser);
        AffixHelper.streamAffixes(weapon).forEach(inst -> inst.onProjectileFired(living, laser));
        AffixHelper.copyToProjectile(weapon, laser);
        // Handled: stop Apotheosis' own fireProjectile from re-processing this
        // entity with its offhand fallback (our category is never isRanged()).
        laser.getPersistentData().putBoolean(APOTH_GENERATED, true);
    }
}
