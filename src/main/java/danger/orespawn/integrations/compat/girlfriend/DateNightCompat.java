package danger.orespawn.integrations.compat.girlfriend;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import danger.orespawn.ModSounds;
import danger.orespawn.entity.Girlfriend;
import danger.orespawn.integrations.OreSpawnIntegrations;
import danger.orespawn.integrations.config.IntegrationsConfig;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * THREAD 3 "Her Side of the Story" — the DATE NIGHT gift chain
 * (CROSSMOD-THREADS.md item 16 addendum, thread3-girlfriend-verification.json).
 *
 * <p>Eight verified cross-mod gifts, each ONE-TIME per girlfriend per gift
 * type, each granting her a small PERMANENT attribute buff plus hearts and a
 * happy voice line. A per-player milestone count (survives death, see below)
 * drives the {@code her_side} advancement spine: any gift → {@code first_date};
 * 3 → {@code going_steady} (Diary Vol. II); 6 → {@code practically_family}
 * (Diary Vol. III); with 8 milestones banked, presenting a decocraft
 * engagement ring is the finale → {@code the_question} ("Her Answer" + hearts).
 * North star: reward, never punish — a repeat gift or a too-early ring is
 * never consumed, it just gets a gentle nudge message.
 *
 * <p><b>Gift table</b> (ids verified against the pack jars —
 * BiomesOPlenty-neoforge-1.21.1-21.1.0.14, aether-1.21.1-1.5.10,
 * ars_nouveau-1.21.1-5.13.0, orespawn_delight-0.1.0, decocraft-3.0.11-1.21.1;
 * items are looked up by registry id at event time, so any absent partner mod
 * simply means its gift never matches — zero hard deps, policy 1):
 * <ul>
 *   <li>{@code biomesoplenty:rose} — +2 max health (then heal the bonus)</li>
 *   <li>{@code biomesoplenty:lavender} — +5% movement speed</li>
 *   <li>{@code biomesoplenty:pink_daffodil} — +2 armor</li>
 *   <li>{@code aether:white_flower} — +0.1 knockback resistance</li>
 *   <li>{@code ars_nouveau:sourceberry_bush} — +1 attack damage</li>
 *   <li>{@code orespawn_delight:strawberry_shortcake} — +4 max health (heals)</li>
 *   <li>{@code orespawn_delight:luna_moth_macaron} — +2 armor toughness</li>
 *   <li>{@code decocraft:teddy_bear_*} (any of the 13 colors, one shared
 *       flag) — +2 max health (heals)</li>
 *   <li>{@code decocraft:engagement_ring_*} (rosegold canon; gold/silver
 *       also accepted) — the finale, no stat, requires 8 milestones</li>
 * </ul>
 * {@code minecraft:poppy} and {@code minecraft:dandelion} are HER items
 * (tame/heal and skin-cycle, Girlfriend.java:423-457) and are deliberately
 * absent from the table. {@code orespawn_delight:heart_box_chocolates} is
 * OWNED BY orespawn_delight (InteractionsHandler.java:446-452 cancels and
 * runs its regen + cook-back) — this class never re-handles it; a LOWEST
 * priority, receiveCanceled=true observer counts a bonus milestone when the
 * delight event arrives already canceled, so registration order between the
 * two mods never matters.
 *
 * <p><b>POLICY 4 — light-code integration, verified against:</b>
 * <ul>
 *   <li>{@code orespawn-1.21.1-2.0.0-beta.1.jar} (libs/, compileOnly; mirror
 *       source Girlfriend.java:419-510) — {@code mobInteract} equips ANY
 *       unhandled non-food item as her mainhand weapon and eats any food, so
 *       every gift here MUST cancel the interact event;
 *       {@code ModSounds.O_HAPPY1..O_HAPPY7} are public
 *       {@code DeferredHolder<SoundEvent,...>} voice-bank holders (jar +
 *       mirror ModSounds.java:406-419).</li>
 *   <li>NeoForge 21.1.223 sources, {@code CommonHooks.java:797-801} +
 *       decompiled MC 1.21.1 {@code Player.java:1086-1098} —
 *       {@code Player.interactOn} posts {@code PlayerInteractEvent.EntityInteract}
 *       BEFORE {@code Entity.interact}, and cancelling with
 *       {@code setCancellationResult(InteractionResult.sidedSuccess(...))}
 *       fully preempts {@code mobInteract}. Pack runs NeoForge 21.1.248; the
 *       hook shape is stable across the 21.1.x line.</li>
 *   <li>Decompiled MC 1.21.1 {@code AttributeInstance.java:68-128,181-217} and
 *       {@code AttributeModifier.java:22-46} —
 *       {@code addPermanentModifier(new AttributeModifier(id, amount, op))}
 *       with {@code hasModifier(id)} idempotency; permanent modifiers
 *       serialize inside the entity's vanilla {@code attributes} NBT, so the
 *       buffs persist with no addon bookkeeping and survive
 *       {@code applyValentineState}'s base-value swaps
 *       (Girlfriend.java:140-147 only touches base values).</li>
 *   <li>Decompiled NeoForge-patched 1.21.1 {@code ServerPlayer.restoreFrom}
 *       (bytecode) — the {@code PlayerPersisted}
 *       ({@code Player.PERSISTED_NBT_TAG}, Player.java:117) subtag of the
 *       player's persistent data is copied to the respawned player, so the
 *       milestone count survives death.</li>
 *   <li>{@code orespawn_delight-0.1.0} InteractionsHandler.java:433-471 —
 *       the {@code cancelAndRun} idiom copied here, and the
 *       heart_box_chocolates ownership contract observed above.</li>
 * </ul>
 *
 * <p>Advancement grants use the house "witnessed" impossible-criterion idiom
 * (see {@code AliveWorldCompat.grantWitnessed}); a missing advancement
 * (partner mod absent, so its conditions stripped the JSON) logs once and
 * no-ops. All server-side effects run behind the {@code cancelAndRun} client
 * guard; there is no client-only code in this class, so no
 * {@code FMLEnvironment.dist} gate is needed. Only ever classloaded when the
 * "orespawn" mod is present — the main mod class invokes
 * {@link #init(IEventBus)} reflectively after a ModList check. The Java side
 * additionally re-checks the thread toggle live so a config flip +
 * {@code /reload} silences the handlers together with the datapack half.
 */
public final class DateNightCompat {

    private static final String THREAD_ID = "girlfriend";

    /** Sub-compound key used in both her entity NBT and the player's persisted NBT. */
    private static final String DATA_KEY = "orespawn_integrations:date_night";
    private static final String MILESTONES_KEY = "milestones";
    private static final String KEY_TEDDY = "teddy_bear";
    private static final String KEY_RING = "engagement_ring";
    private static final String KEY_CHOCOLATES = "heart_box_chocolates";

    private static final int MILESTONES_GOING_STEADY = 3;
    private static final int MILESTONES_PRACTICALLY_FAMILY = 6;
    private static final int MILESTONES_FOR_RING = 8;

    private static final ResourceLocation CHOCOLATES_ID =
            ResourceLocation.fromNamespaceAndPath("orespawn_delight", "heart_box_chocolates");

    private static final String MSG = "message.orespawn_integrations.date_night.";

    /** One verified gift: flag key, buffed attribute, modifier math, post-buff heal. */
    private record Gift(String key, Holder<Attribute> attribute, double amount,
                        AttributeModifier.Operation operation, float healAfter) {
    }

    /** Exact-id gifts; teddy bears and rings match by id prefix (see {@link #giftFor}). */
    private static final Map<ResourceLocation, Gift> GIFTS = Map.of(
            ResourceLocation.fromNamespaceAndPath("biomesoplenty", "rose"),
            new Gift("rose", Attributes.MAX_HEALTH, 2.0, AttributeModifier.Operation.ADD_VALUE, 2.0f),
            ResourceLocation.fromNamespaceAndPath("biomesoplenty", "lavender"),
            new Gift("lavender", Attributes.MOVEMENT_SPEED, 0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, 0.0f),
            ResourceLocation.fromNamespaceAndPath("biomesoplenty", "pink_daffodil"),
            new Gift("pink_daffodil", Attributes.ARMOR, 2.0, AttributeModifier.Operation.ADD_VALUE, 0.0f),
            ResourceLocation.fromNamespaceAndPath("aether", "white_flower"),
            new Gift("white_flower", Attributes.KNOCKBACK_RESISTANCE, 0.1, AttributeModifier.Operation.ADD_VALUE, 0.0f),
            ResourceLocation.fromNamespaceAndPath("ars_nouveau", "sourceberry_bush"),
            new Gift("sourceberry", Attributes.ATTACK_DAMAGE, 1.0, AttributeModifier.Operation.ADD_VALUE, 0.0f),
            ResourceLocation.fromNamespaceAndPath("orespawn_delight", "strawberry_shortcake"),
            new Gift("strawberry_shortcake", Attributes.MAX_HEALTH, 4.0, AttributeModifier.Operation.ADD_VALUE, 4.0f),
            ResourceLocation.fromNamespaceAndPath("orespawn_delight", "luna_moth_macaron"),
            new Gift("luna_moth_macaron", Attributes.ARMOR_TOUGHNESS, 2.0, AttributeModifier.Operation.ADD_VALUE, 0.0f));

    private static final Gift TEDDY_GIFT =
            new Gift(KEY_TEDDY, Attributes.MAX_HEALTH, 2.0, AttributeModifier.Operation.ADD_VALUE, 2.0f);

    /** Her registered happy voice bank (port jar, public holders). */
    private static final List<DeferredHolder<SoundEvent, SoundEvent>> HAPPY = List.of(
            ModSounds.O_HAPPY1, ModSounds.O_HAPPY2, ModSounds.O_HAPPY3, ModSounds.O_HAPPY4,
            ModSounds.O_HAPPY5, ModSounds.O_HAPPY6, ModSounds.O_HAPPY7);

    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();

    private DateNightCompat() {
    }

    public static void init(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(DateNightCompat::onGiftInteract);
        // receiveCanceled + LOWEST: sees orespawn_delight's canceled
        // heart_box_chocolates event no matter which handler ran first.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, DateNightCompat::onChocolatesObserved);
    }

    // =====================================================================
    // the gift chain
    // =====================================================================

    public static void onGiftInteract(PlayerInteractEvent.EntityInteract event) {
        try {
            if (!IntegrationsConfig.isThreadEnabled(THREAD_ID)) {
                return;
            }
            if (!(event.getTarget() instanceof Girlfriend girlfriend)) {
                return;
            }
            ItemStack stack = event.getItemStack();
            if (stack.isEmpty()) {
                return;
            }
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            boolean ring = isEngagementRing(id);
            Gift gift = ring ? null : giftFor(id);
            if (gift == null && !ring) {
                return;
            }
            // Courtship needs a companion first: an untamed (or someone
            // else's) girlfriend falls through to her canon handling. Both
            // flags are synched entity data, so client and server agree.
            if (!girlfriend.isTame() || !girlfriend.isOwnedBy(event.getEntity())) {
                return;
            }
            if (ring) {
                cancelAndRun(event, () -> presentRing(event, girlfriend));
            } else {
                cancelAndRun(event, () -> giveGift(event, girlfriend, gift));
            }
        } catch (Throwable t) {
            logOnce("gift_interact", t);
        }
    }

    /**
     * Cancels with the vanilla sided-success result (arm swing, no weapon
     * equip) and runs the action server-side only — the delight house idiom.
     */
    private static void cancelAndRun(PlayerInteractEvent.EntityInteract event, Runnable serverAction) {
        boolean clientSide = event.getLevel().isClientSide;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(clientSide));
        if (!clientSide) {
            serverAction.run();
        }
    }

    private static Gift giftFor(ResourceLocation id) {
        Gift exact = GIFTS.get(id);
        if (exact != null) {
            return exact;
        }
        if ("decocraft".equals(id.getNamespace()) && id.getPath().startsWith("teddy_bear_")) {
            return TEDDY_GIFT;
        }
        return null;
    }

    private static boolean isEngagementRing(ResourceLocation id) {
        return "decocraft".equals(id.getNamespace()) && id.getPath().startsWith("engagement_ring_");
    }

    /** Server side. One-time per girlfriend per gift type; repeats cost nothing. */
    private static void giveGift(PlayerInteractEvent.EntityInteract event, Girlfriend girlfriend, Gift gift) {
        Player player = event.getEntity();
        CompoundTag given = dateNightTag(girlfriend);
        if (given.getBoolean(gift.key())) {
            // Never punish: the repeat gift is NOT consumed.
            player.displayClientMessage(
                    Component.translatable(MSG + "already_gifted", girlfriend.getDisplayName()), true);
            return;
        }
        given.putBoolean(gift.key(), true);
        applyBuff(girlfriend, gift);
        event.getItemStack().consume(1, player);
        celebrate(girlfriend, 7);
        player.displayClientMessage(Component.translatable(
                MSG + "thanks." + (1 + girlfriend.getRandom().nextInt(3)),
                girlfriend.getDisplayName()), false);
        recordMilestone(player);
    }

    /**
     * Permanent, save-persistent, idempotent attribute buff. After a
     * +max-health gift she is healed by the bonus so the new hearts fill
     * (empowering — matches her poppy heal idiom, verification Q2).
     */
    private static void applyBuff(Girlfriend girlfriend, Gift gift) {
        AttributeInstance instance = girlfriend.getAttribute(gift.attribute());
        if (instance == null) {
            logMissingOnce("attribute for gift '" + gift.key() + "'");
            return;
        }
        ResourceLocation modifierId =
                ResourceLocation.fromNamespaceAndPath(OreSpawnIntegrations.MODID, "date_night/" + gift.key());
        if (!instance.hasModifier(modifierId)) {
            instance.addPermanentModifier(new AttributeModifier(modifierId, gift.amount(), gift.operation()));
        }
        if (gift.healAfter() > 0.0f) {
            girlfriend.heal(gift.healAfter());
        }
    }

    /**
     * Server side. The finale: with all {@value #MILESTONES_FOR_RING}
     * milestones banked, the ring is accepted — big celebration, full heal,
     * {@code the_question} advancement (whose reward loot hands over
     * "Her Answer" + 8 hearts). Too early or repeated: nothing is consumed.
     */
    private static void presentRing(PlayerInteractEvent.EntityInteract event, Girlfriend girlfriend) {
        Player player = event.getEntity();
        CompoundTag given = dateNightTag(girlfriend);
        if (given.getBoolean(KEY_RING)) {
            player.displayClientMessage(
                    Component.translatable(MSG + "ring_again", girlfriend.getDisplayName()), true);
            return;
        }
        if (milestoneCount(player) < MILESTONES_FOR_RING) {
            player.displayClientMessage(
                    Component.translatable(MSG + "ring_too_soon", girlfriend.getDisplayName()), false);
            return;
        }
        given.putBoolean(KEY_RING, true);
        event.getItemStack().consume(1, player);
        girlfriend.heal(girlfriend.getMaxHealth());
        celebrate(girlfriend, 24);
        celebrate(girlfriend, 12);
        player.displayClientMessage(
                Component.translatable(MSG + "ring_accepted", girlfriend.getDisplayName()), false);
        recordMilestone(player); // counts, and re-checks the earlier tiers
        if (player instanceof ServerPlayer serverPlayer) {
            grant(serverPlayer, "her_side/the_question");
        }
    }

    // =====================================================================
    // heart_box_chocolates: OBSERVE, never re-handle (delight owns it)
    // =====================================================================

    public static void onChocolatesObserved(PlayerInteractEvent.EntityInteract event) {
        try {
            if (!event.isCanceled() || event.getLevel().isClientSide) {
                return;
            }
            if (!IntegrationsConfig.isThreadEnabled(THREAD_ID)) {
                return;
            }
            if (!(event.getTarget() instanceof Girlfriend girlfriend)) {
                return;
            }
            if (!CHOCOLATES_ID.equals(BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem()))) {
                return;
            }
            // Delight only cancels for the owner's tamed companion
            // (InteractionsHandler.java:446-452); re-checked for safety.
            if (!girlfriend.isTame() || !girlfriend.isOwnedBy(event.getEntity())) {
                return;
            }
            CompoundTag given = dateNightTag(girlfriend);
            if (given.getBoolean(KEY_CHOCOLATES)) {
                return;
            }
            given.putBoolean(KEY_CHOCOLATES, true);
            recordMilestone(event.getEntity());
        } catch (Throwable t) {
            logOnce("chocolates_observed", t);
        }
    }

    // =====================================================================
    // milestones + advancements
    // =====================================================================

    /**
     * Bumps the per-player milestone count (death-proof, see class javadoc)
     * and grants every advancement tier the new count reaches. Grants are
     * idempotent — award() on a done advancement is a no-op.
     */
    private static void recordMilestone(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        CompoundTag data = persistedDateNightTag(serverPlayer);
        int count = data.getInt(MILESTONES_KEY) + 1;
        data.putInt(MILESTONES_KEY, count);
        grant(serverPlayer, "her_side/first_date");
        if (count >= MILESTONES_GOING_STEADY) {
            grant(serverPlayer, "her_side/going_steady");
        }
        if (count >= MILESTONES_PRACTICALLY_FAMILY) {
            grant(serverPlayer, "her_side/practically_family");
        }
    }

    private static int milestoneCount(Player player) {
        return persistedDateNightTag(player).getInt(MILESTONES_KEY);
    }

    /** Grants criterion "witnessed" of one of this addon's advancements to the player. */
    private static void grant(ServerPlayer player, String advancementPath) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        AdvancementHolder holder = server.getAdvancements()
                .get(ResourceLocation.fromNamespaceAndPath(OreSpawnIntegrations.MODID, advancementPath));
        if (holder == null) {
            // Partner mod absent -> the conditions stripped the JSON. Fine.
            logMissingOnce("advancement " + advancementPath);
            return;
        }
        player.getAdvancements().award(holder, "witnessed");
    }

    // =====================================================================
    // plumbing
    // =====================================================================

    /** Live date-night sub-compound of her persistent entity data. */
    private static CompoundTag dateNightTag(Entity entity) {
        CompoundTag root = entity.getPersistentData();
        if (!root.contains(DATA_KEY, Tag.TAG_COMPOUND)) {
            root.put(DATA_KEY, new CompoundTag());
        }
        return root.getCompound(DATA_KEY);
    }

    /**
     * Live date-night sub-compound inside the player's {@code PlayerPersisted}
     * tag — the one subtree {@code ServerPlayer.restoreFrom} carries across
     * death (bytecode-verified, class javadoc).
     */
    private static CompoundTag persistedDateNightTag(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
        if (!persisted.contains(DATA_KEY, Tag.TAG_COMPOUND)) {
            persisted.put(DATA_KEY, new CompoundTag());
        }
        return persisted.getCompound(DATA_KEY);
    }

    /** Hearts + a line from her registered happy voice bank. */
    private static void celebrate(Girlfriend girlfriend, int hearts) {
        Level level = girlfriend.level();
        level.playSound(null, girlfriend.getX(), girlfriend.getY(), girlfriend.getZ(),
                randomHappy(girlfriend.getRandom()), SoundSource.NEUTRAL, 1.0f, 1.0f);
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.HEART,
                    girlfriend.getX(), girlfriend.getEyeY() + 0.4, girlfriend.getZ(),
                    hearts, 0.5, 0.4, 0.5, 0.02);
        }
    }

    private static SoundEvent randomHappy(RandomSource random) {
        return HAPPY.get(random.nextInt(HAPPY.size())).get();
    }

    private static void logOnce(String key, Throwable t) {
        if (LOGGED.add(key)) {
            OreSpawnIntegrations.LOGGER.error(
                    "[date_night] handler '{}' failed; muting further reports of this failure", key, t);
        }
    }

    private static void logMissingOnce(String what) {
        if (LOGGED.add("missing:" + what)) {
            OreSpawnIntegrations.LOGGER.warn(
                    "[date_night] '{}' is not registered/loaded; the feature that needs it stays inert", what);
        }
    }
}
