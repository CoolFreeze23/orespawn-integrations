package danger.orespawn.integrations.story.registry;

import danger.orespawn.integrations.compat.hats.HatsFieldCorrection;
import danger.orespawn.integrations.config.IntegrationsConfig;
import danger.orespawn.integrations.story.StoryCore;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.UUID;

/**
 * The Ledger Scrap's listener ({@code item-ledger-scrap}), one class for three small hooks:
 * <ul>
 *   <li><b>The field Correction</b> (P03). A scrap used (main hand, server side) on a creature wearing one of the
 *       Wardrobe's stolen hats sends the hat to the player's Wardrobe and spends the scrap. It rides
 *       {@code PlayerInteractEvent.EntityInteract}, which fires before the creature's own {@code mobInteract}, and a
 *       success cancels with SUCCESS, so it also works on villagers and on mobs that eat every click. Refusals keep the
 *       scrap: a player (line 11) and another player's tamed animal (line 12) answer; a bare creature does nothing.
 *       Without Hats Renewed, or with {@code [story] field_correction} off, the scrap does nothing on creatures.</li>
 *   <li><b>The twin guard.</b> A crouch-tap on a tamed {@code brasilcoisas:ovinho_vivo} by anyone but its owner is
 *       cancelled with SUCCESS on either hand and both sides (the owner is synced, so the client agrees), line 13.</li>
 *   <li><b>The ovinho death line.</b> The mod's own death procedure is never called, so a killed ovinho leaves its
 *       block (its own loot) and we spawn one {@code brasilcoisas:pintinho} beside it, by registry id.</li>
 * </ul>
 * Also: the first scrap spent anywhere grants {@code correcoes/primeiro_gasto} (a Correction crafted here; the desk's
 * averbação grants it in its function), and the first field Correction grants {@code correcoes/primeira_emenda},
 * P03's used flag. One map lookup per scrap use, one owner check per crouch-tap on an ovinho, one type check per death.
 */
public final class LedgerScrapListener {

    public static final ResourceLocation OVINHO = ResourceLocation.fromNamespaceAndPath("brasilcoisas", "ovinho_vivo");
    public static final ResourceLocation PINTINHO = ResourceLocation.fromNamespaceAndPath("brasilcoisas", "pintinho");
    public static final ResourceLocation PRIMEIRO_GASTO = ResourceLocation.fromNamespaceAndPath("orespawn_integrations", "correcoes/primeiro_gasto");
    public static final ResourceLocation PRIMEIRA_EMENDA = ResourceLocation.fromNamespaceAndPath("orespawn_integrations", "correcoes/primeira_emenda");
    /** Hats Renewed's open-Wardrobe key mapping (ClientEventHandler), shown to each player as their own binding. */
    public static final String WARDROBE_KEY = "key.hats.open_menu";

    private static final String LINES = "orespawn_integrations.correcoes.campo.";

    private LedgerScrapListener() {
    }

    public static void init(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, LedgerScrapListener::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(LedgerScrapListener::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(LedgerScrapListener::onItemCrafted);
    }

    // ---- the twin guard and the field Correction

    private static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        try {
            Player player = event.getEntity();
            Entity target = event.getTarget();
            if (isForeignOvinho(player, target)) {
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
                if (!player.level().isClientSide() && event.getHand() == InteractionHand.MAIN_HAND) {
                    player.displayClientMessage(Component.translatable(LINES + "gemeo"), true);
                }
                return;
            }
            if (player.level().isClientSide() || event.getHand() != InteractionHand.MAIN_HAND
                    || !(player instanceof ServerPlayer serverPlayer)) {
                return;
            }
            ItemStack stack = event.getItemStack();
            if (stack.isEmpty() || !ScrapCaps.SCRAP.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))
                    || !IntegrationsConfig.on(IntegrationsConfig.storyFieldCorrection) || !ModList.get().isLoaded("hats")) {
                return;
            }
            InteractionResult result = fieldCorrection(serverPlayer, target, stack);
            if (result != null) {
                event.setCancellationResult(result);
                event.setCanceled(true);
            }
        } catch (Throwable t) {
            StoryCore.logOnce("scrap.interact", t);
        }
    }

    /** A crouch-tap on a tamed ovinho whose owner is someone else. */
    static boolean isForeignOvinho(Player player, Entity target) {
        if (!player.isShiftKeyDown() || !(target instanceof OwnableEntity ownable)
                || !OVINHO.equals(BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()))) {
            return false;
        }
        UUID owner = ownable.getOwnerUUID();
        return owner != null && !owner.equals(player.getUUID());
    }

    /** Null to let the click through (a bare creature); otherwise the click is spent with this result. */
    private static InteractionResult fieldCorrection(ServerPlayer player, Entity target, ItemStack stack) {
        if (target instanceof Player) {
            player.displayClientMessage(Component.translatable(LINES + "jogador"), true);
            return InteractionResult.SUCCESS;
        }
        if (!(target instanceof Mob mob)) {
            return null;
        }
        if (mob instanceof OwnableEntity ownable && ownable.getOwnerUUID() != null
                && !ownable.getOwnerUUID().equals(player.getUUID())) {
            player.displayClientMessage(Component.translatable(LINES + "com_dono"), true);
            return InteractionResult.SUCCESS;
        }
        // Hats Renewed classes load only here, after the ModList check in onEntityInteract.
        int refiled = HatsFieldCorrection.refile(player, mob);
        if (refiled == HatsFieldCorrection.NO_HAT) {
            return null;
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getEyeY() + 0.3, mob.getZ(), 8, 0.2, 0.1, 0.2, 0.02);
        if (refiled == HatsFieldCorrection.REPEAT) {
            player.sendSystemMessage(Component.translatable(LINES + "repetido"));
        }
        player.displayClientMessage(Component.translatable(LINES + "sucesso", Component.keybind(WARDROBE_KEY)), true);
        grant(player, PRIMEIRA_EMENDA);
        grant(player, PRIMEIRO_GASTO);
        return InteractionResult.SUCCESS;
    }

    // ---- the ovinho death line

    private static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(dead.level() instanceof ServerLevel level) || !OVINHO.equals(BuiltInRegistries.ENTITY_TYPE.getKey(dead.getType()))) {
            return;
        }
        try {
            EntityType<?> chick = BuiltInRegistries.ENTITY_TYPE.getOptional(PINTINHO).orElse(null);
            if (chick != null) {
                chick.spawn(level, dead.blockPosition(), MobSpawnType.EVENT);
            }
        } catch (Throwable t) {
            StoryCore.logOnce("scrap.ovinho_death", t);
        }
    }

    // ---- the first scrap spent on a Correction

    private static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        for (int i = 0; i < event.getInventory().getContainerSize(); i++) {
            ItemStack stack = event.getInventory().getItem(i);
            if (!stack.isEmpty() && ScrapCaps.SCRAP.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
                grant(player, PRIMEIRO_GASTO);
                return;
            }
        }
    }

    /** Awards every remaining criterion of an advancement; a missing advancement (switched off) is skipped. */
    public static void grant(ServerPlayer player, ResourceLocation id) {
        AdvancementHolder holder = player.server.getAdvancements().get(id);
        if (holder == null) {
            return;
        }
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
        for (String criterion : progress.getRemainingCriteria()) {
            player.getAdvancements().award(holder, criterion);
        }
    }
}
