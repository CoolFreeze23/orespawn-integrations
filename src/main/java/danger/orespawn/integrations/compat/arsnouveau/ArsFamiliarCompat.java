package danger.orespawn.integrations.compat.arsnouveau;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.hollingsworth.arsnouveau.api.event.FamiliarSummonEvent;
import com.hollingsworth.arsnouveau.api.registry.FamiliarRegistry;
import com.hollingsworth.arsnouveau.common.entity.familiar.FamiliarEntity;

import danger.orespawn.integrations.OreSpawnIntegrations;
import danger.orespawn.integrations.config.IntegrationsConfig;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Thread 3 "Her Side of the Story" — Girlfriend Familiar (Ars Nouveau bridge).
 * Registers a familiar holder so the standard Ars Nouveau Ritual of Binding,
 * performed near a <b>wild (untamed)</b> Girlfriend, drops a Bound Script that
 * unlocks a pocket-sized Girlfriend familiar. The jealousy payoff: while she is
 * summoned, any living attacker that hurts her owner is hit with Weakness II
 * for 5 seconds plus angry particles.
 *
 * <p><b>Verified against (policy 4)</b> ars_nouveau-1.21.1-5.13.0 (jar in
 * {@code libs/}, all via javap):
 * <ul>
 * <li>{@code api.registry.FamiliarRegistry.registerFamiliar(AbstractFamiliarHolder)}
 *     — static, code-side registry (the jar ships no data-driven familiar
 *     JSONs). <b>Timing is load-bearing:</b>
 *     {@code setup.registry.ItemsRegistry.onItemRegistry} iterates
 *     {@code getFamiliarHolderMap()} during the item {@code RegisterEvent},
 *     auto-creating and registering one {@code FamiliarScript} item per holder
 *     under the holder's own ResourceLocation (bytecode-verified) — so the
 *     holder must already be registered when registry events fire. This
 *     addon's compat table runs inside the {@code OreSpawnIntegrations} mod
 *     constructor, which FML completes for every mod before any
 *     {@code RegisterEvent}, so reflective {@code init} here is early enough;
 *     no earlier parent hook is needed. The auto item for this holder is
 *     {@code orespawn_integrations:girlfriend} (model + texture shipped in
 *     this addon's assets).</li>
 * <li>{@code api.event.FamiliarSummonEvent(Entity familiar, Entity owner)} —
 *     posted on {@code NeoForge.EVENT_BUS} by
 *     {@code common.network.PacketSummonFamiliar} <i>before</i>
 *     {@code addFreshEntity}, cancellable; our advancement listener runs at
 *     {@code EventPriority.LOWEST} without {@code receiveCanceled} so a
 *     cancelled summon never awards.</li>
 * <li>{@code common.entity.familiar.FamiliarEntity.attributes()} — the static
 *     {@code AttributeSupplier.Builder} AN's own familiars use; wired below via
 *     {@code EntityAttributeCreationEvent}. AN registers its familiar entity
 *     types as {@code MobCategory.CREATURE} (ModEntities bytecode); mirrored
 *     here.</li>
 * </ul>
 *
 * <p><b>NeoForge 21.1.223</b> (dev jar; pack runs 21.1.248, API stable across
 * 21.1.x): {@code LivingDamageEvent.Post} exposes {@code getSource()} /
 * {@code getNewDamage()} on the damaged {@code LivingEntity} — used for the
 * jealousy hook, server side only.
 *
 * <p><b>Both partners required:</b> the compat table row gates on
 * {@code ars_nouveau}; {@link #init} additionally bails unless {@code orespawn}
 * is loaded, because the holder predicate and the familiar's model/texture
 * reference port classes and assets. The {@code girlfriend} thread config
 * toggle is honoured at runtime (holder predicate + jealousy hook), so
 * {@code /reload}-style live disabling works even though registry entries are
 * permanent.
 *
 * <p>House defensive style ({@code compat/alive/AliveWorldCompat} pattern):
 * every game-event handler body is try/catch with a log-once mute.
 */
public final class ArsFamiliarCompat {

    /** Thread 3 config id — {@link IntegrationsConfig#isThreadEnabled}. */
    static final String THREAD_ID = "girlfriend";

    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();

    /** How close (blocks) the familiar must be to her owner to retaliate. */
    private static final double JEALOUSY_RANGE = 24.0;

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, OreSpawnIntegrations.MODID);

    /**
     * Pocket-sized wrapper entity. Port Girlfriend is sized 0.5x1.6 (mirror
     * ModEntities.java:456); the familiar renders at 0.4x that scale, so the
     * hitbox matches: 0.2x0.64, rounded up a hair for pickability.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<FamiliarGirlfriend>> FAMILIAR_GIRLFRIEND =
            ENTITIES.register("familiar_girlfriend",
                    () -> EntityType.Builder.of(FamiliarGirlfriend::new, MobCategory.CREATURE)
                            .sized(0.25f, 0.7f).eyeHeight(0.58f)
                            .clientTrackingRange(10)
                            .build("familiar_girlfriend"));

    private ArsFamiliarCompat() {
    }

    public static void init(IEventBus modBus) {
        if (!ModList.get().isLoaded("orespawn")) {
            OreSpawnIntegrations.LOGGER.info(
                    "[ars_familiar] orespawn absent - Girlfriend familiar stays unregistered");
            return;
        }
        ENTITIES.register(modBus);
        modBus.addListener(ArsFamiliarCompat::onEntityAttributes);
        // Mod-ctor timing (see class javadoc): must precede the item RegisterEvent.
        FamiliarRegistry.registerFamiliar(new GirlfriendFamiliarHolder());
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ArsFamiliarCompat::onFamiliarSummoned);
        NeoForge.EVENT_BUS.addListener(ArsFamiliarCompat::onOwnerDamaged);
        if (FMLEnvironment.dist.isClient()) {
            modBus.addListener(ArsFamiliarClient::onRegisterRenderers);
        }
    }

    private static void onEntityAttributes(EntityAttributeCreationEvent event) {
        try {
            event.put(FAMILIAR_GIRLFRIEND.get(), FamiliarEntity.attributes().build());
        } catch (Throwable t) {
            logOnce("attributes", t);
        }
    }

    /**
     * First summon of the Girlfriend familiar by a player grants criterion
     * "witnessed" of {@code orespawn_integrations:her_side/familiar_bound}.
     * LOWEST priority + no receiveCanceled = only uncancelled summons count;
     * re-awarding an already-earned criterion is a vanilla no-op, so "first"
     * needs no bookkeeping. Missing advancement (her_side datapack tree not
     * landed / stripped) logs once and no-ops — same contract as
     * {@code AliveWorldCompat.grantWitnessed}.
     */
    private static void onFamiliarSummoned(FamiliarSummonEvent event) {
        try {
            if (!(event.getEntity() instanceof FamiliarGirlfriend)
                    || !(event.owner instanceof ServerPlayer player)) {
                return;
            }
            AdvancementHolder holder = player.serverLevel().getServer().getAdvancements()
                    .get(ResourceLocation.fromNamespaceAndPath(OreSpawnIntegrations.MODID,
                            "her_side/familiar_bound"));
            if (holder == null) {
                logMissingOnce("advancement her_side/familiar_bound");
                return;
            }
            player.getAdvancements().award(holder, "witnessed");
        } catch (Throwable t) {
            logOnce("familiar_summoned", t);
        }
    }

    /**
     * The jealousy payoff (north star: empowers the player, punishes nobody's
     * fun): when the owner takes damage from a living attacker, a nearby
     * summoned Girlfriend familiar hits the attacker with Weakness II (5s) and
     * fumes angry particles. Server side by construction —
     * {@code LivingDamageEvent.Post} only fires there and the damaged entity is
     * matched as {@code ServerPlayer}.
     */
    private static void onOwnerDamaged(LivingDamageEvent.Post event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer player)
                    || event.getNewDamage() <= 0.0f
                    || !IntegrationsConfig.isThreadEnabled(THREAD_ID)) {
                return;
            }
            if (!(event.getSource().getEntity() instanceof LivingEntity attacker)
                    || attacker == player) {
                return;
            }
            for (FamiliarGirlfriend familiar : player.serverLevel().getEntitiesOfClass(
                    FamiliarGirlfriend.class, player.getBoundingBox().inflate(JEALOUSY_RANGE))) {
                if (player.getUUID().equals(familiar.getOwnerID())) {
                    familiar.defendOwner(attacker);
                    break;
                }
            }
        } catch (Throwable t) {
            logOnce("jealousy", t);
        }
    }

    // --- defensive plumbing (AliveWorldCompat idiom) ---

    static void logOnce(String key, Throwable t) {
        if (LOGGED.add(key)) {
            OreSpawnIntegrations.LOGGER.error(
                    "[ars_familiar] handler '{}' failed; muting further reports of this failure",
                    key, t);
        }
    }

    static void logMissingOnce(String what) {
        if (LOGGED.add("missing:" + what)) {
            OreSpawnIntegrations.LOGGER.warn(
                    "[ars_familiar] '{}' is not registered/loaded; the feature that needs it stays inert",
                    what);
        }
    }
}
