package danger.orespawn.integrations.compat.alive;

import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import danger.orespawn.integrations.config.IntegrationsConfig;
import danger.orespawn.integrations.story.firsthour.FallRecord;
import danger.orespawn.integrations.story.firsthour.FirstHourScenes;
import danger.orespawn.integrations.story.firsthour.FirstHourUtil;
import danger.orespawn.integrations.story.firsthour.PriorityTicket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.minecraft.world.InteractionResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The First Fall (B04a): a per-player, daylight, scaled first Hoard Fall, called by the Priority Ticket at P05. It
 * lands in front of the player, 60-100 blocks out in a 120 degree cone around the look direction (50-70 at night),
 * under {@link FallSafety}'s rule; with no passing spot it is held and tried again every 60 s, all around from the
 * second try, and after 10 failed tries it sets its crate down crater-less on a dry block within 8 blocks of the
 * player. The landing shapes a 7-wide, 2-deep bowl of natural ground only (two uranium and one titanium ore in its
 * wall), puts a signal campfire on hay by the crate, three OreSpawn Coins on the rim and the gold "Fall" pin up.
 *
 * <p>The crate ({@code ironchest:copper_chest}) is addressed: until its recipient opens it, right-clicks and breaks by
 * anyone else are refused with the consignee line. The recipient's opening is their P05 use and R5, puts the campfire
 * out and takes the pin down. A player from before 0.10.0 gets the Back-Dated Fall: the same landing, plus the
 * Eggshell and ITEM 6 in the crate and the late-delivery label. Coins are never saved: refused when loaded from disk,
 * discarded at their finder's logout and 20 minutes after landing. A first-hour scene, through the scene cap.</p>
 */
final class FirstFall {

    static final String COIN_TAG = "orespawn_integrations.fall_coin";
    private static final String K = "orespawn_integrations.first_fall.";
    private static final int RETRY_TICKS = 60 * 20;
    private static final int MAX_TRIES = 10;
    private static final int STREAK_TICKS = 120;
    private static final int COIN_LIFE = 20 * 60 * 20;

    private static final Map<UUID, Long> SCENES = new HashMap<>();
    private static final Map<UUID, List<Entity>> COINS = new HashMap<>();

    private FirstFall() {
    }

    static void install() {
        FirstHourScenes.fall = FirstFall::start;
        NeoForge.EVENT_BUS.addListener(FirstFall::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(FirstFall::onBreak);
        NeoForge.EVENT_BUS.addListener(FirstFall::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(FirstFall::onLogout);
        NeoForge.EVENT_BUS.addListener(FirstFall::onAttack);
    }

    static void reset() {
        SCENES.clear();
        COINS.clear();
    }

    // --- the landing ---

    private static FirstHourScenes.Result start(ServerPlayer player, boolean backDated) {
        if (!IntegrationsConfig.on(IntegrationsConfig.aliveFirstFall)
                || (backDated && !IntegrationsConfig.on(IntegrationsConfig.aliveFirstFallExistingPlayers))) {
            return FirstHourScenes.Result.UNAVAILABLE;
        }
        if (!(player.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return FirstHourScenes.Result.BUSY;
        }
        FallRecord record = FallRecord.get(level.getServer());
        FallRecord.Entry entry = record.entry(player.getUUID());
        if (entry.landed) {
            return FirstHourScenes.Result.UNAVAILABLE; // once per player, ever
        }
        long now = level.getServer().getTickCount();
        if (now - entry.lastTry < RETRY_TICKS || !FirstHourScenes.CAP.canStart(now)) {
            return FirstHourScenes.Result.BUSY;
        }
        entry.lastTry = now;
        RandomSource random = level.random;
        boolean crater = entry.tries < MAX_TRIES;
        BlockPos spot;
        if (crater) {
            boolean night = level.isNight();
            spot = FallSafety.findSpot(level, player, night ? 50 : 60, night ? 70 : 100,
                    entry.tries == 0 ? 120 : 360, random);
        } else {
            spot = player.onGround() && !player.isInWater() ? dryBlockNear(level, player, random) : null;
        }
        if (spot == null) {
            entry.tries++;
            record.setDirty();
            return FirstHourScenes.Result.BUSY; // held: P05 waits, and nothing later is issued
        }
        long scene = FirstHourScenes.CAP.start(now);
        SCENES.put(player.getUUID(), scene);
        entry.backDated = backDated;
        record.setDirty();
        warn(level, player, spot);
        BlockPos at = spot;
        float yaw = player.getYRot();
        AliveScheduler.schedule(level.getServer(), STREAK_TICKS,
                () -> land(level, player.getUUID(), at, crater, backDated, yaw));
        return FirstHourScenes.Result.STARTED;
    }

    /** The shipped warning, a 6-second gold streak and the whistle. */
    private static void warn(ServerLevel level, ServerPlayer player, BlockPos spot) {
        FirstHourUtil.actionbar(player, Component.translatable(K + "warning"));
        double x = spot.getX() + 0.5;
        double z = spot.getZ() + 0.5;
        level.playSound(null, player.getX(), player.getY() + 20, player.getZ(),
                SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.AMBIENT, 4.0F, 0.5F);
        for (int t = 0; t < STREAK_TICKS; t += 5) {
            double y = spot.getY() + 90.0 * (1.0 - t / (double) STREAK_TICKS);
            AliveScheduler.schedule(level.getServer(), t, () -> {
                level.sendParticles(ParticleTypes.FLAME, x, y, z, 12, 0.3, 1.5, 0.3, 0.0);
                level.sendParticles(ParticleTypes.FIREWORK, x, y, z, 6, 0.2, 1.0, 0.2, 0.0);
            });
        }
    }

    private static void land(ServerLevel level, UUID owner, BlockPos spot, boolean crater, boolean backDated,
                             float yaw) {
        BlockPos crate;
        if (crater) {
            crate = shapeBowl(level, spot);
        } else {
            crate = spot;
        }
        BlockState chest = AliveWorldCompat.blockState("ironchest", "copper_chest");
        level.setBlockAndUpdate(crate, chest != null ? chest : Blocks.CHEST.defaultBlockState());
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
        fill(level, crate, player, backDated);
        placeIfFree(level, crate.west(), Blocks.HAY_BLOCK.defaultBlockState());
        if (level.getBlockState(crate.west()).is(Blocks.HAY_BLOCK)) {
            placeIfFree(level, crate.west().above(), Blocks.CAMPFIRE.defaultBlockState()
                    .setValue(CampfireBlock.SIGNAL_FIRE, true).setValue(CampfireBlock.LIT, true));
        }
        label(level, crate.east(), crater, backDated, yaw);
        level.playSound(null, crate, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.AMBIENT, 3.0F, 0.8F);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, crate.getX() + 0.5, crate.getY() + 1.0, crate.getZ() + 0.5,
                40, 2.0, 0.5, 2.0, 0.02);
        if (crater) {
            spawnCoins(level, owner, crate);
        }
        FallRecord record = FallRecord.get(level.getServer());
        FallRecord.Entry entry = record.entry(owner);
        entry.landed = true;
        entry.crate = crate.immutable();
        record.setDirty();
        if (player != null) {
            PriorityTicket.fallLanded(player, crate);
        }
    }

    /** Clears natural ground only in a 7-wide bowl, 2 deep in the middle; returns the crate's place at its floor. */
    private static BlockPos shapeBowl(ServerLevel level, BlockPos spot) {
        int gy = spot.getY() - 1;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 > 12) {
                    continue;
                }
                int depth = d2 <= 4 ? 2 : 1;
                BlockPos column = new BlockPos(spot.getX() + dx, gy, spot.getZ() + dz);
                BlockState above = level.getBlockState(column.above());
                if (FallSafety.clearable(above) && !above.isAir()) {
                    level.setBlockAndUpdate(column.above(), Blocks.AIR.defaultBlockState());
                }
                for (int dy = 0; dy < depth; dy++) {
                    BlockPos p = column.below(dy);
                    BlockState s = level.getBlockState(p);
                    if (FallSafety.natural(s) && level.getBlockEntity(p) == null) {
                        level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
        // Two uranium and one titanium in the bowl's wall, only where the wall is natural ground.
        BlockState uranium = AliveWorldCompat.blockState("orespawn", "ore_uranium");
        BlockState titanium = AliveWorldCompat.blockState("orespawn", "ore_titanium");
        BlockPos[] wall = {new BlockPos(3, -1, 1), new BlockPos(-3, -1, -1), new BlockPos(1, -1, -3)};
        for (int i = 0; i < wall.length; i++) {
            BlockPos p = new BlockPos(spot.getX() + wall[i].getX(), gy + wall[i].getY(), spot.getZ() + wall[i].getZ());
            BlockState ore = i < 2 ? uranium : titanium;
            if (ore != null && FallSafety.natural(level.getBlockState(p))) {
                level.setBlockAndUpdate(p, ore);
            }
        }
        return new BlockPos(spot.getX(), gy - 1, spot.getZ());
    }

    private static void placeIfFree(ServerLevel level, BlockPos pos, BlockState state) {
        BlockState there = level.getBlockState(pos);
        if (there.isAir() || FallSafety.clearable(there)) {
            level.setBlockAndUpdate(pos, state);
        }
    }

    /** The shipping label: a waxed oak sign of four translate lines, facing where the player looked from. */
    private static void label(ServerLevel level, BlockPos pos, boolean crater, boolean backDated, float yaw) {
        BlockState there = level.getBlockState(pos);
        if (!there.isAir() && !FallSafety.clearable(there)) {
            return;
        }
        level.setBlockAndUpdate(pos, Blocks.OAK_SIGN.defaultBlockState()
                .setValue(StandingSignBlock.ROTATION, RotationSegment.convertToSegment(yaw + 180.0F)));
        if (!(level.getBlockEntity(pos) instanceof SignBlockEntity sign)) {
            return;
        }
        String[] lines = backDated
                ? new String[]{"sign_late.line1", "sign_late.line2", "sign_late.line3", "sign_late.line4"}
                : new String[]{"sign.line1", "sign.line2", crater ? "sign.line3" : "sign.line3_no_coin", "sign.line4"};
        SignText text = new SignText();
        for (int i = 0; i < 4; i++) {
            text = text.setMessage(i, Component.translatable(K + lines[i]));
        }
        sign.setText(text, true);
        sign.setWaxed(true);
        sign.setChanged();
        level.sendBlockUpdated(pos, sign.getBlockState(), sign.getBlockState(), 3);
    }

    /**
     * ITEM 5's twin, from the twin's own crate table (gemeo/ovo_do_caixote): its lore and the batch key, so picking it
     * up counts as finding the twin and no log drops another. Without Brasil e Coisas the table is empty.
     */
    private static List<ItemStack> crateTwin(ServerLevel level, BlockPos pos) {
        LootTable table = level.getServer().reloadableRegistries().getLootTable(CRATE_TWIN);
        return table.getRandomItems(new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .create(LootContextParamSets.GIFT));
    }

    private static final ResourceKey<LootTable> CRATE_TWIN = ResourceKey.create(Registries.LOOT_TABLE,
            ResourceLocation.fromNamespaceAndPath("orespawn_integrations", "gemeo/ovo_do_caixote"));

    /** The crate's contents, exactly as the card lists them. */
    private static void fill(ServerLevel level, BlockPos pos, ServerPlayer player, boolean backDated) {
        if (!(level.getBlockEntity(pos) instanceof Container box)) {
            return;
        }
        List<ItemStack> items = new ArrayList<>();
        items.add(new ItemStack(Items.SPYGLASS));
        items.add(new ItemStack(Items.BUCKET));
        items.add(FirstHourUtil.stack("orespawn_integrations:ledger_scrap", 1));
        items.add(FirstHourUtil.stack("orespawn_delight:rock_candy_red", 1));
        items.add(FirstHourUtil.stack("orespawn_delight:butter_pat", 1));
        items.add(FirstHourUtil.stack("orespawn_delight:fairy_floss", 1));
        items.add(withLore(new ItemStack(Items.GLOWSTONE, 4), "glowstone_lore"));
        // ITEM 5: a player with no twin yet (cc_gemeo still 0) finds one in the crate.
        if (player == null || FirstHourUtil.score(player, "cc_gemeo", 0) == 0) {
            items.addAll(crateTwin(level, pos));
            items.add(withLore(new ItemStack(Items.PAPER), "item5_slip"));
        }
        if (backDated) {
            items.add(withLore(new ItemStack(Items.PAPER), "item6_notice"));
            items.add(FirstHourUtil.stack("orespawn_integrations:hatchling_shell", 1));
            if (player != null) {
                // Shelf 9's join handler leaves a veteran's Eggshell to this crate; it is theirs now.
                danger.orespawn.integrations.story.shelf9.Eggshell.markGiven(player);
            }
        }
        int slot = 0;
        for (ItemStack stack : items) {
            if (!stack.isEmpty() && slot < box.getContainerSize()) {
                box.setItem(slot++, stack);
            }
        }
        box.setChanged();
    }

    private static ItemStack withLore(ItemStack stack, String key) {
        stack.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable(K + key))));
        return stack;
    }

    /** A free dry block within 8 of the player, for the crater-less crate after ten tries. */
    private static BlockPos dryBlockNear(ServerLevel level, ServerPlayer player, RandomSource random) {
        for (int i = 0; i < FallSafety.MAX_TRIES; i++) {
            int x = player.getBlockX() + random.nextInt(17) - 8;
            int z = player.getBlockZ() + random.nextInt(17) - 8;
            BlockPos top = FallSafety.surface(level, x, z);
            if (top != null && level.getBlockState(top).isAir()) {
                return top;
            }
        }
        return null;
    }

    private static void spawnCoins(ServerLevel level, UUID owner, BlockPos crate) {
        EntityType<?> coin = AliveWorldCompat.entityType("orespawn", "coin");
        if (coin == null) {
            return;
        }
        List<Entity> spawned = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            double angle = i * (Math.PI * 2.0 / 3.0) + level.random.nextDouble() * 0.6;
            int x = crate.getX() + (int) Math.round(Math.cos(angle) * 5.0);
            int z = crate.getZ() + (int) Math.round(Math.sin(angle) * 5.0);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            Entity e = coin.spawn(level, new BlockPos(x, y, z), MobSpawnType.EVENT);
            if (e != null) {
                e.addTag(COIN_TAG);
                spawned.add(e);
            }
        }
        COINS.computeIfAbsent(owner, k -> new ArrayList<>()).addAll(spawned);
        AliveScheduler.schedule(level.getServer(), COIN_LIFE, () -> spawned.forEach(Entity::discard));
    }

    // --- the consignee guard ---

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)
                    || level.dimension() != Level.OVERWORLD) {
                return;
            }
            FallRecord record = FallRecord.get(level.getServer());
            UUID owner = record.unopenedOwnerAt(event.getPos());
            if (owner == null) {
                return;
            }
            if (!owner.equals(player.getUUID())) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                FirstHourUtil.actionbar(player, Component.translatable(K + "consignee"));
                return;
            }
            opened(level, player, record, event.getPos());
        } catch (Throwable t) {
            AliveWorldCompat.logOnce("first_fall_open", t);
        }
    }

    private static void onBreak(BlockEvent.BreakEvent event) {
        try {
            if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
                return;
            }
            FallRecord record = FallRecord.get(level.getServer());
            UUID owner = record.unopenedOwnerAt(event.getPos());
            if (owner != null && !owner.equals(event.getPlayer().getUUID())) {
                event.setCanceled(true);
                if (event.getPlayer() instanceof ServerPlayer player) {
                    FirstHourUtil.actionbar(player, Component.translatable(K + "consignee"));
                }
                return;
            }
            if (owner != null && event.getPlayer() instanceof ServerPlayer player) {
                opened(level, player, record, event.getPos());
            }
            for (FallRecord.Entry e : record.all().values()) {
                if (event.getPos().equals(e.crate)) {
                    e.crate = null; // a broken crate leaves the First Fall crate list
                    record.setDirty();
                }
            }
        } catch (Throwable t) {
            AliveWorldCompat.logOnce("first_fall_break", t);
        }
    }

    /** The recipient's opening: P05 used, R5 once, the campfire out, the pin down, the scene over. */
    private static void opened(ServerLevel level, ServerPlayer player, FallRecord record, BlockPos crate) {
        FallRecord.Entry entry = record.entry(player.getUUID());
        if (entry.opened) {
            return;
        }
        entry.opened = true;
        record.setDirty();
        for (BlockPos p : BlockPos.betweenClosed(crate.offset(-2, -1, -2), crate.offset(2, 2, 2))) {
            BlockState s = level.getBlockState(p);
            if (s.is(Blocks.CAMPFIRE) && s.getValue(CampfireBlock.LIT)) {
                level.setBlockAndUpdate(p, s.setValue(CampfireBlock.LIT, false));
            }
        }
        FirstHourUtil.award(player, "livro/r5");
        PriorityTicket.crateOpened(player);
        Long scene = SCENES.remove(player.getUUID());
        if (scene != null) {
            FirstHourScenes.CAP.end(scene);
        }
    }

    // --- Coins are never saved ---

    private static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.loadedFromDisk()
                && event.getEntity().getTags().contains(COIN_TAG)) {
            event.setCanceled(true);
            event.getEntity().discard();
        }
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        List<Entity> coins = COINS.remove(event.getEntity().getUUID());
        if (coins != null) {
            coins.forEach(Entity::discard);
        }
    }

    /** The first punch at a Fall's Coin: Keep the Change. */
    private static void onAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getTarget().getTags().contains(COIN_TAG)) {
            FirstHourUtil.award(player, "livro/keep_the_change");
        }
    }

    /** True when {@code be} is an opened First Fall crate with nothing in it. */
    static boolean emptiedCrate(FallRecord record, BlockEntity be) {
        return record.isOpenedCrate(be.getBlockPos()) && be instanceof Container c && c.isEmpty();
    }
}
