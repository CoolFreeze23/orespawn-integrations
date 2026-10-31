package danger.orespawn.integrations.story.chaos;

import danger.orespawn.integrations.OreSpawnIntegrations;
import danger.orespawn.integrations.config.IntegrationsConfig;
import danger.orespawn.integrations.story.StoryCore;
import danger.orespawn.integrations.story.registry.ScrapCaps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * The Hoard caskets' Java side (system-hoard-caskets). The rolls themselves are data: the root function
 * {@code lucky_blocks:random/random} and {@code orespawn_integrations:lucky/*}. This class only gives those functions
 * what a function can't read on its own, and keeps the Hatchling table's traps off barns:
 * <ul>
 *   <li>the switch {@code story.scaled_caskets}, written to command storage {@code orespawn_integrations:config} at
 *       start-up and on every config reload, as {@code scaled_caskets:1b/0b};</li>
 *   <li>the scores the functions read, created at start-up: {@code cc_lucky} (the functions' own working numbers),
 *       {@code cc_lucky_dia} (the game day of a player's first join, for the Hatchling grace) and
 *       {@code cc_ovo_semana} (the Land's Week of a player's last whole boss egg);</li>
 *   <li>the first join's day, written once at login (a veteran with three game days of play time is dated back, so
 *       nobody who played before 0.10.0 starts a grace);</li>
 *   <li>the traps tagged {@code cc_lucky_trap}: their blasts break no block and hurt only players, a small fireball
 *       lights no block, and a lightning bolt strikes only the players beside it and sets no fire;</li>
 *   <li>the first Sealed Casket ({@code simpleluckyblock:lucky_block}) a player opens pays one Ledger Scrap;</li>
 *   <li>every Pandora's Box shows its small gift tag on its first open, unless a Great Refusal already tagged it.</li>
 * </ul>
 */
public final class CasketCore {

    public static final String TRAP_TAG = "cc_lucky_trap";
    public static final String REFUSAL_TAG = "cc_recusa";
    public static final String OBJ_WORK = "cc_lucky";
    public static final String OBJ_JOIN_DAY = "cc_lucky_dia";
    public static final String OBJ_EGG_WEEK = "cc_ovo_semana";
    public static final ResourceLocation CONFIG_STORAGE = ResourceLocation.fromNamespaceAndPath(OreSpawnIntegrations.MODID, "config");
    static final String SEALED_SCRAP_FLAG = "orespawn_integrations:sealed_casket_scrap";
    static final ResourceLocation SEALED_CASKET = ResourceLocation.fromNamespaceAndPath("simpleluckyblock", "lucky_block");
    static final ResourceLocation PANDORAS_BOX = ResourceLocation.fromNamespaceAndPath("pandorasbox", "pandoras_box");
    /** A game day is 24,000 ticks; three of them is the Hatchling grace. */
    static final int GRACE_DAYS = 3;
    /** The senders a small box can name until the World Register (0.11.0) gives every box its own world. */
    static final String[] SMALL_BOX_SENDERS = {
            "orespawn_integrations.lucky.sender.sand",
            "orespawn_integrations.lucky.sender.towers",
            "orespawn_integrations.lucky.sender.inner_sea",
            "orespawn_integrations.lucky.sender.kaiju",
    };

    private CasketCore() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(CasketCore::onConfigReload);
        NeoForge.EVENT_BUS.addListener(CasketCore::onServerStarted);
        NeoForge.EVENT_BUS.addListener(CasketCore::onLogin);
        NeoForge.EVENT_BUS.addListener(CasketCore::onBreak);
        NeoForge.EVENT_BUS.addListener(CasketCore::onDetonate);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, CasketCore::onImpact);
        NeoForge.EVENT_BUS.addListener(CasketCore::onJoin);
    }

    // --- what the functions read ---

    private static void onServerStarted(ServerStartedEvent event) {
        try {
            prepare(event.getServer());
        } catch (Throwable t) {
            StoryCore.logOnce("caskets.prepare", t);
        }
    }

    private static void onConfigReload(ModConfigEvent.Reloading event) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && event.getConfig().getSpec() == IntegrationsConfig.SPEC) {
            server.execute(() -> writeConfigStorage(server));
        }
    }

    /** Creates the functions' scores and writes the switch. Public for the GameTests. */
    public static void prepare(MinecraftServer server) {
        Scoreboard board = server.getScoreboard();
        objective(board, OBJ_WORK);
        objective(board, OBJ_JOIN_DAY);
        objective(board, OBJ_EGG_WEEK);
        writeConfigStorage(server);
    }

    static void writeConfigStorage(MinecraftServer server) {
        CompoundTag tag = server.getCommandStorage().get(CONFIG_STORAGE).copy();
        tag.putBoolean("scaled_caskets", IntegrationsConfig.on(IntegrationsConfig.storyScaledCaskets));
        server.getCommandStorage().set(CONFIG_STORAGE, tag);
    }

    static Objective objective(Scoreboard board, String name) {
        Objective objective = board.getObjective(name);
        if (objective == null) {
            objective = board.addObjective(name, ObjectiveCriteria.DUMMY, Component.literal(name),
                    ObjectiveCriteria.RenderType.INTEGER, false, null);
        }
        return objective;
    }

    /** The current game day, as the functions read it ({@code time query day}). */
    static int today(ServerLevel level) {
        return (int) (level.getDayTime() / 24000L % 2147483647L);
    }

    /** The day a first join is dated: today, or three days back for a player who already has three days of play. */
    public static int joinDay(int today, int playTicks) {
        return playTicks >= GRACE_DAYS * 24000 ? today - GRACE_DAYS : today;
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        try {
            Scoreboard board = player.getServer().getScoreboard();
            Objective objective = objective(board, OBJ_JOIN_DAY);
            ReadOnlyScoreInfo info = board.getPlayerScoreInfo(player, objective);
            if (info == null) {
                int play = player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
                board.getOrCreatePlayerScore(player, objective).set(joinDay(today(player.serverLevel()), play));
            }
        } catch (Throwable t) {
            StoryCore.logOnce("caskets.join_day", t);
        }
    }

    // --- the Hatchling traps stay off barns ---

    private static void onDetonate(ExplosionEvent.Detonate event) {
        Entity source = event.getExplosion().getDirectSourceEntity();
        if (source == null || !source.getTags().contains(TRAP_TAG)) {
            return;
        }
        event.getAffectedBlocks().clear();
        event.getAffectedEntities().removeIf(e -> !(e instanceof Player));
    }

    private static void onImpact(ProjectileImpactEvent event) {
        if (event.getProjectile() instanceof SmallFireball fireball && fireball.getTags().contains(TRAP_TAG)
                && event.getRayTraceResult().getType() == HitResult.Type.BLOCK) {
            event.setCanceled(true);
            if (!fireball.level().isClientSide) {
                fireball.discard();
            }
        }
    }

    private static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || event.loadedFromDisk()) {
            return;
        }
        try {
            Entity entity = event.getEntity();
            if (entity instanceof LightningBolt bolt && bolt.getTags().contains(TRAP_TAG)
                    && event.getLevel() instanceof ServerLevel level) {
                // A visual-only bolt sets no fire and strikes nobody; the players beside it are struck by hand.
                bolt.setVisualOnly(true);
                for (Player player : level.getEntitiesOfClass(Player.class, bolt.getBoundingBox().inflate(3.0))) {
                    player.thunderHit(level, bolt);
                }
                return;
            }
            if (BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).equals(PANDORAS_BOX)) {
                giftTag(entity);
            }
        } catch (Throwable t) {
            StoryCore.logOnce("caskets.join", t);
        }
    }

    /** The small gift tag; a box a Great Refusal opened already carries its own, so it says nothing. */
    private static void giftTag(Entity box) {
        Player opener = box.level().getNearestPlayer(box, 16.0);
        if (!(opener instanceof ServerPlayer player)) {
            return;
        }
        if (player.getTags().contains(REFUSAL_TAG)) {
            player.removeTag(REFUSAL_TAG);
            return;
        }
        String sender = SMALL_BOX_SENDERS[Math.floorMod(box.getUUID().hashCode(), SMALL_BOX_SENDERS.length)];
        player.sendSystemMessage(Component.translatable("orespawn_integrations.lucky.box_tag",
                Component.translatable(sender)).withStyle(net.minecraft.ChatFormatting.GOLD));
    }

    // --- the first Sealed Casket pays a scrap ---

    private static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || player.isCreative() || player.isSpectator()) {
            return;
        }
        try {
            if (!BuiltInRegistries.BLOCK.getKey(event.getState().getBlock()).equals(SEALED_CASKET)) {
                return;
            }
            CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
            if (persisted.getBoolean(SEALED_SCRAP_FLAG)) {
                return;
            }
            if (ScrapCaps.give(player, 1)) {
                persisted.putBoolean(SEALED_SCRAP_FLAG, true);
                player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
                player.displayClientMessage(Component.translatable("orespawn_integrations.lucky.sealed_scrap"), true);
            }
        } catch (Throwable t) {
            StoryCore.logOnce("caskets.sealed_scrap", t);
        }
    }
}
