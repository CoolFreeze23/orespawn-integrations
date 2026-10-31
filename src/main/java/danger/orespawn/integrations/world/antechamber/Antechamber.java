package danger.orespawn.integrations.world.antechamber;

import danger.orespawn.integrations.OreSpawnIntegrations;
import danger.orespawn.integrations.config.IntegrationsConfig;
import danger.orespawn.integrations.story.Ranks;
import danger.orespawn.integrations.story.StoryCore;
import danger.orespawn.integrations.world.WorldUtil;
import danger.orespawn.integrations.world.regions.RegionNames;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Antechamber (structure-chaos-antechamber, the 0.10.0 part). OreSpawn beta.15's butterfly
 * ({@code EntityButterfly.mobInteract}, read with javap; the luna moth and Mothra extend it without overriding it)
 * sends a server player with an empty hand, off portal cooldown, to Chaos (or home from Chaos) through
 * {@code EntityAnt.findSafeSpot}, a widening column search. Below Squire our {@code EntityInteract} listener takes
 * that same click first, per hand pass (so a held main-hand item with an empty off hand is taken on the off-hand
 * pass), cancels it with SUCCESS and lands the player on a 7x7 obsidian ledge at the centre of the chunk under them:
 * Y = max(100, MOTION_BLOCKING top + 1), capped at 240, no search. The ledge is built into air by the transition's
 * post callback, with three signs (translate keys) and two return butterflies when fewer than two are near.
 * A Mothra tagged {@code cc_mothra_puppet} is left to its own refusal.
 *
 * <p>From Squire a plain click runs OreSpawn's trip with a one-shot arrival check that puts a 3x3 perch under a
 * player left over void; a sneak-click still lands on the ledge (the rank-up's announcement promises it). Below
 * Squire the first fall off the ledge is caught once per player. Off with {@code story.antechamber = false}: the
 * shipped trip as it was.
 */
public final class Antechamber {

    public static final ResourceKey<Level> CHAOS =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("orespawn", "chaos"));
    public static final Set<String> DOORS = Set.of("orespawn:butterfly", "orespawn:luna_moth", "orespawn:mothra");
    public static final String PUPPET_TAG = "cc_mothra_puppet";
    /** Player data: the armed catch's ledge position, and whether the one catch is spent. */
    public static final String CATCH_KEY = "cc_ante_catch";
    public static final String CATCH_SPENT_KEY = "cc_ante_catch_spent";
    public static final String WONDER = OreSpawnIntegrations.MODID + ":wonders/the_antechamber";
    /** R6, "Why are the nights full of horrors?", on the Ledger tab (system-advancement-tree; written by data). */
    public static final String R6 = OreSpawnIntegrations.MODID + ":livro/r6";

    private static final String KEY = "orespawn_integrations.antechamber.";
    private static final ResourceLocation BUTTERFLY = ResourceLocation.fromNamespaceAndPath("orespawn", "butterfly");
    private static final int PERCH_WINDOW_TICKS = 40;

    /** Players in Chaos whose catch is armed; filled at the landing and at login, emptied on use or on leaving. */
    private static final Set<UUID> ARMED = new HashSet<>();
    /** Squire+ players whose shipped trip is under way: the tick it was armed. */
    private static final Map<UUID, Integer> PERCH = new HashMap<>();

    private Antechamber() {
    }

    public static void init(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(Antechamber::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(Antechamber::onChangedDimension);
        NeoForge.EVENT_BUS.addListener(Antechamber::onServerTick);
        NeoForge.EVENT_BUS.addListener(Antechamber::onLoggedIn);
        NeoForge.EVENT_BUS.addListener(Antechamber::onLoggedOut);
        NeoForge.EVENT_BUS.addListener(Antechamber::onClone);
        if (FMLEnvironment.dist.isClient()) {
            AntechamberGlow.register();
        }
    }

    /** What our listener does with one hand pass on a door. Public for the GameTests. */
    public enum Decision { PASS, LEDGE, SHIPPED_WITH_PERCH }

    public static Decision decide(ServerPlayer player, Entity target, ItemStack handItem) {
        if (!IntegrationsConfig.on(IntegrationsConfig.storyAntechamber)) {
            return Decision.PASS;
        }
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString();
        if (!DOORS.contains(id) || target.getTags().contains(PUPPET_TAG)) {
            return Decision.PASS;
        }
        // The shipped trip's own gates: an empty hand on this pass, no portal cooldown, not from inside Chaos.
        if (!handItem.isEmpty() || player.isOnPortalCooldown() || target.level().dimension() == CHAOS) {
            return Decision.PASS;
        }
        if (Ranks.atLeast(player, Ranks.SQUIRE) && !player.isShiftKeyDown()) {
            return Decision.SHIPPED_WITH_PERCH;
        }
        return Decision.LEDGE;
    }

    private static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        try {
            Decision decision = decide(player, event.getTarget(), event.getItemStack());
            if (decision == Decision.SHIPPED_WITH_PERCH) {
                PERCH.put(player.getUUID(), player.server.getTickCount());
            } else if (decision == Decision.LEDGE) {
                ServerLevel chaos = player.server.getLevel(CHAOS);
                if (chaos == null) {
                    return;
                }
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
                land(player, chaos, !Ranks.atLeast(player, Ranks.SQUIRE));
            }
        } catch (Throwable t) {
            StoryCore.logOnce("antechamber_interact", t);
        }
    }

    /**
     * The ledge landing: one chunk load and one heightmap read at the chunk centre under the player, then the
     * dimension change; the ledge, signs and butterflies are built by the post callback in the same tick.
     */
    public static void land(ServerPlayer player, ServerLevel chaos, boolean armCatch) {
        player.setPortalCooldown(AntechamberRules.PORTAL_COOLDOWN);
        int cx = AntechamberRules.chunkCentre(player.getBlockX());
        int cz = AntechamberRules.chunkCentre(player.getBlockZ());
        LevelChunk chunk = chaos.getChunk(cx >> 4, cz >> 4);
        int y = AntechamberRules.ledgeY(chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, cx & 15, cz & 15));
        BlockPos centre = new BlockPos(cx, y, cz);
        RegionNames.markLedgeArrival(player, cx + 0.5, cz + 0.5);
        DimensionTransition transition = new DimensionTransition(chaos, new Vec3(cx + 0.5, y + 1, cz + 0.5), Vec3.ZERO,
                player.getYRot(), player.getXRot(), entity -> {
                    try {
                        buildLedge(chaos, centre);
                        entity.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 1.0F);
                        if (entity instanceof ServerPlayer arrived) {
                            arrived(arrived, centre, armCatch);
                        }
                    } catch (Throwable t) {
                        StoryCore.logOnce("antechamber_ledge", t);
                    }
                });
        player.changeDimension(transition);
    }

    private static void arrived(ServerPlayer player, BlockPos centre, boolean armCatch) {
        CompoundTag data = player.getPersistentData();
        if (armCatch && !data.getBoolean(CATCH_SPENT_KEY)) {
            data.putLong(CATCH_KEY, centre.asLong());
            ARMED.add(player.getUUID());
        }
        if (WorldUtil.grant(player, WONDER)) {
            WorldUtil.grant(player, R6);
        }
    }

    /**
     * The 7x7 obsidian ledge at {@code centre}'s Y, set only into air (a rebuilt ledge never replaces anything), the
     * three signs on top at their corners, and return butterflies up to two within 16 blocks. Public for the
     * GameTests.
     */
    public static void buildLedge(ServerLevel level, BlockPos centre) {
        BlockState obsidian = Blocks.OBSIDIAN.defaultBlockState();
        for (int dx = -AntechamberRules.HALF; dx <= AntechamberRules.HALF; dx++) {
            for (int dz = -AntechamberRules.HALF; dz <= AntechamberRules.HALF; dz++) {
                BlockPos pos = centre.offset(dx, 0, dz);
                if (level.getBlockState(pos).isAir()) {
                    level.setBlock(pos, obsidian, 3);
                }
            }
        }
        String[][] lines = {
                {KEY + "sign1.1", KEY + "sign1.2", KEY + "sign1.3", KEY + "sign1.4"},
                {KEY + "sign2.1", KEY + "sign2.2"},
                {KEY + "sign3.1", KEY + "sign3.2", KEY + "sign3.3"}};
        for (int i = 0; i < AntechamberRules.SIGNS.length; i++) {
            int dx = AntechamberRules.SIGNS[i][0];
            int dz = AntechamberRules.SIGNS[i][1];
            placeSign(level, centre.offset(dx, 1, dz), AntechamberRules.signRotation(dx, dz), lines[i]);
        }
        EntityType<?> butterfly = BuiltInRegistries.ENTITY_TYPE.get(BUTTERFLY);
        if (BuiltInRegistries.ENTITY_TYPE.getKey(butterfly).equals(BUTTERFLY)) {
            int near = level.getEntities((Entity) null, new AABB(centre).inflate(16.0),
                    e -> e.getType() == butterfly).size();
            for (int i = near; i < 2; i++) {
                butterfly.spawn(level, centre.offset(i == 0 ? -1 : 1, 3, 0), MobSpawnType.EVENT);
            }
        }
    }

    private static void placeSign(ServerLevel level, BlockPos pos, int rotation, String[] keys) {
        if (!level.getBlockState(pos).isAir()) {
            return;
        }
        BlockState sign = Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, rotation);
        level.setBlock(pos, sign, 3);
        if (level.getBlockEntity(pos) instanceof SignBlockEntity be) {
            SignText text = new SignText();
            for (int i = 0; i < keys.length; i++) {
                text = text.setMessage(i, Component.translatable(keys[i]));
            }
            be.setText(text, true);
            be.setWaxed(true);
            be.setChanged();
            level.sendBlockUpdated(pos, sign, sign, 3);
        }
    }

    private static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        try {
            if (event.getFrom() == CHAOS) {
                // Leaving Chaos disarms the catch; it stays unspent for the next ledge landing.
                ARMED.remove(player.getUUID());
                player.getPersistentData().remove(CATCH_KEY);
            }
            Integer armedAt = PERCH.remove(player.getUUID());
            if (event.getTo() == CHAOS && armedAt != null
                    && player.server.getTickCount() - armedAt <= PERCH_WINDOW_TICKS) {
                rankedArrival(player);
            }
        } catch (Throwable t) {
            StoryCore.logOnce("antechamber_arrival", t);
        }
    }

    /** A ranked player's shipped arrival: the perch if they hang over void, and Wonder 4 if they never had a ledge. */
    private static void rankedArrival(ServerPlayer player) {
        if (overVoid(player.serverLevel(), player.blockPosition())) {
            buildPerch(player.serverLevel(), player.blockPosition().below());
            player.displayClientMessage(Component.translatable(KEY + "perch"), true);
        }
        if (WorldUtil.grant(player, WONDER)) {
            WorldUtil.grant(player, R6);
            player.sendSystemMessage(Component.translatable(KEY + "ranked_arrival"));
        }
    }

    /** True when no block stands within 64 below {@code feet}. One column read. Public for the GameTests. */
    public static boolean overVoid(ServerLevel level, BlockPos feet) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(feet.getX() >> 4, feet.getZ() >> 4);
        if (chunk == null) {
            return false;
        }
        BlockPos.MutableBlockPos pos = feet.mutable();
        int bottom = Math.max(level.getMinBuildHeight(), feet.getY() - AntechamberRules.VOID_DEPTH);
        for (int y = feet.getY() - 1; y >= bottom; y--) {
            pos.setY(y);
            if (!chunk.getBlockState(pos).isAir()) {
                return false;
            }
        }
        return true;
    }

    /** The 3x3 obsidian perch at {@code floor}, into air only. Public for the GameTests. */
    public static void buildPerch(ServerLevel level, BlockPos floor) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos pos = floor.offset(dx, 0, dz);
                if (level.getBlockState(pos).isAir()) {
                    level.setBlock(pos, Blocks.OBSIDIAN.defaultBlockState(), 3);
                }
            }
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (ARMED.isEmpty() || server.getTickCount() % 10 != 0) {
            return;
        }
        for (UUID id : Set.copyOf(ARMED)) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null || player.level().dimension() != CHAOS) {
                ARMED.remove(id);
                continue;
            }
            try {
                checkCatch(player);
            } catch (Throwable t) {
                StoryCore.logOnce("antechamber_catch", t);
            }
        }
    }

    private static void checkCatch(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(CATCH_KEY) || data.getBoolean(CATCH_SPENT_KEY)) {
            ARMED.remove(player.getUUID());
            return;
        }
        BlockPos ledge = BlockPos.of(data.getLong(CATCH_KEY));
        boolean falling = !player.onGround() && player.getDeltaMovement().y < 0;
        if (!AntechamberRules.shouldCatch(player.getY(), ledge.getY(), falling)) {
            return;
        }
        Vec3 to = catchSpot(player.serverLevel(), ledge, player.position());
        player.teleportTo(to.x, to.y, to.z);
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        player.resetFallDistance();
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, AntechamberRules.SLOW_FALLING_TICKS));
        player.displayClientMessage(Component.translatable(KEY + "catch"), true);
        data.remove(CATCH_KEY);
        data.putBoolean(CATCH_SPENT_KEY, true);
        ARMED.remove(player.getUUID());
    }

    /**
     * Where the catch sets the player: the MOTION_BLOCKING top at the centres of the ledge's chunk and its eight
     * neighbours (loaded chunks only), the column nearest the player whose top is island ground and not the ledge,
     * 2 blocks above it; with none, back on the ledge. Public for the GameTests.
     */
    public static Vec3 catchSpot(ServerLevel level, BlockPos ledge, Vec3 from) {
        int lcx = ledge.getX() >> 4;
        int lcz = ledge.getZ() >> 4;
        Vec3 best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(lcx + dx, lcz + dz);
                if (chunk == null) {
                    continue;
                }
                int top = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, 8, 8);
                int x = ((lcx + dx) << 4) + 8;
                int z = ((lcz + dz) << 4) + 8;
                if (top < level.getMinBuildHeight() || (dx == 0 && dz == 0 && top == ledge.getY())) {
                    continue;
                }
                Vec3 spot = new Vec3(x + 0.5, top + 1 + AntechamberRules.CATCH_ABOVE, z + 0.5);
                double d = (spot.x - from.x) * (spot.x - from.x) + (spot.z - from.z) * (spot.z - from.z);
                if (d < bestDistance) {
                    best = spot;
                    bestDistance = d;
                }
            }
        }
        return best != null ? best : new Vec3(ledge.getX() + 0.5, ledge.getY() + 1, ledge.getZ() + 0.5);
    }

    private static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.level().dimension() == CHAOS
                && player.getPersistentData().contains(CATCH_KEY)) {
            ARMED.add(player.getUUID());
        }
    }

    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        ARMED.remove(event.getEntity().getUUID());
        PERCH.remove(event.getEntity().getUUID());
    }

    /** The catch is once per player, so a death keeps it spent. */
    private static void onClone(PlayerEvent.Clone event) {
        CompoundTag old = event.getOriginal().getPersistentData();
        if (old.getBoolean(CATCH_SPENT_KEY)) {
            event.getEntity().getPersistentData().putBoolean(CATCH_SPENT_KEY, true);
        }
    }
}
