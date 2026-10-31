package danger.orespawn.integrations.compat.alive;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import danger.orespawn.integrations.config.IntegrationsConfig;
import danger.orespawn.integrations.story.firsthour.FallRecord;
import danger.orespawn.integrations.story.firsthour.FirstHourUtil;
import danger.orespawn.integrations.story.week.Almanac;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The Hoard Fall (event-hoard-fall): Utopia's treasury cracks and drops a vault into the Land, at night, after a
 * whistle. One seeded roll a night replaces the shipped 1-in-24,000 tick roll at the same rate (about 0.39 a night,
 * {@link HoardFallRules}); on a Fall night the Almanac is asked first ({@link Almanac#claimNight}), and a refused night
 * postpones the Fall to the next allowed one, never cancels it. The hour is drawn unseeded between 13,000 and 22,000 and
 * the centre is a random overworld player. Everything rides the shared one-second clock; nothing runs per tick.
 *
 * <p>In order: the warning on the actionbar to every player within 512 blocks of the centre, 30 s ahead; 30 s of gold
 * streak and the whistle toward a spot the safety rule passes ({@link FallSafety}: loaded chunks 60-100 blocks out,
 * never within 64 blocks of a respawn point, a signed Parish Stone or a {@code #homely} block entity); the crater, which
 * with {@code alive.meteor_grief} off (the default) is a 7-wide, 2-deep bowl of natural ground only with uranium and
 * titanium in its wall, and breaks no player block; three {@code orespawn:alien} on the rim and Close Encounter within
 * 64; and the vault ({@link HoardVault#placeVault}). At most one a night, remembered across restarts. A Fall night
 * slept through before it lands is carried to the wake-up: it lands at first light under the same rules, with the dawn
 * line and the smoke already up instead of the streak. Without {@code threads.alive_world} there is no Fall.</p>
 *
 * <p>The last Fall's night, centre and compass quarter are kept in command storage
 * {@code orespawn_integrations:hoard_fall} ({@code last}) for the Gazette's headline, beside {@code carry}, the
 * postponed Fall.</p>
 */
final class MeteorEvent {

    private static final double MIN_DIST = 60.0;
    private static final double MAX_DIST = 100.0;
    private static final float CRATER_POWER = 3.0F;
    private static final int WITNESS_RADIUS = 64;
    private static final int RETRY_SECONDS = 30;
    private static final int MAX_TRIES = 10;
    private static final String EVENT_KEY = "hoard_fall";
    static final ResourceLocation STORAGE = FirstHourUtil.oi("hoard_fall");
    private static final String K = HoardVault.K;

    private static long plannedNight = Long.MIN_VALUE;
    private static boolean fallTonight;
    private static boolean started;
    private static int landingTick;
    private static int tries;
    private static long nextTry;
    private static long lastDayTime = Long.MIN_VALUE;

    private MeteorEvent() {
    }

    static void reset() {
        plannedNight = Long.MIN_VALUE;
        fallTonight = false;
        started = false;
        tries = 0;
        nextTry = 0;
        lastDayTime = Long.MIN_VALUE;
    }

    /** The night's check, once a second. */
    static void second(MinecraftServer server) {
        if (!IntegrationsConfig.isThreadEnabled("alive_world")) {
            return;
        }
        ServerLevel level = server.overworld();
        long time = level.getDayTime();
        long hour = Math.floorMod(time, 24000L);
        long night = HoardFallRules.night(time);
        boolean skipped = lastDayTime != Long.MIN_VALUE && time - lastDayTime > 200; // a sleep, or /time add
        lastDayTime = time;
        if (night != plannedNight) {
            plannedNight = night;
            started = false;
            tries = 0;
            nextTry = 0;
            fallTonight = hour >= 12000 && plan(server, night); // first seen by day: that night had no roll
        }
        if (!fallTonight || started || FallRecord.get(server).lastHoardNight() == night) {
            return;
        }
        long now = server.getTickCount();
        if (now < nextTry) {
            return;
        }
        if (hour >= 12000) {
            if (hour >= landingTick) {
                tryStart(level, night, false);
            }
        } else if (skipped) {
            tryStart(level, night, true); // slept through before it landed: at first light, at the wake-up
        } else {
            fallTonight = false; // morning came the slow way with nowhere wild to land: no Fall tonight
        }
    }

    /** The dusk roll: the seeded draw, then the Almanac. A refused night carries the Fall to the next. */
    static boolean plan(MinecraftServer server, long night) {
        CompoundTag storage = server.getCommandStorage().get(STORAGE).copy();
        boolean carried = storage.getBoolean("carry");
        if (!HoardFallRules.falls(server.overworld().getSeed(), night, carried)) {
            return false;
        }
        if (!Almanac.claimNight(server, EVENT_KEY)) {
            storage.putBoolean("carry", true); // postponed, never cancelled
            server.getCommandStorage().set(STORAGE, storage);
            return false;
        }
        landingTick = HoardFallRules.EARLIEST
                + server.overworld().random.nextInt(HoardFallRules.LATEST - HoardFallRules.EARLIEST + 1);
        return true;
    }

    private static void tryStart(ServerLevel level, long night, boolean firstLight) {
        MinecraftServer server = level.getServer();
        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (!p.isSpectator()) {
                players.add(p);
            }
        }
        if (players.isEmpty()) {
            return; // nobody to fall around yet
        }
        ServerPlayer centre = players.get(level.random.nextInt(players.size()));
        if (begin(level, centre, night, firstLight)) {
            started = true;
            FallRecord.get(server).setLastHoardNight(night);
            CompoundTag storage = server.getCommandStorage().get(STORAGE).copy();
            storage.putBoolean("carry", false);
            server.getCommandStorage().set(STORAGE, storage);
            return;
        }
        tries++;
        nextTry = server.getTickCount() + RETRY_SECONDS * 20L;
        if (firstLight || tries >= MAX_TRIES) {
            fallTonight = false; // nowhere wild to land tonight
        }
    }

    /** Starts a Fall around {@code centre}; false when no spot passes the safety rule. Public for the GameTests. */
    static boolean begin(ServerLevel level, ServerPlayer centre, long night, boolean firstLight) {
        RandomSource random = level.random;
        BlockPos spot = FallSafety.findSpot(level, centre, MIN_DIST, MAX_DIST, 360, random);
        if (spot == null) {
            return false;
        }
        MinecraftServer server = level.getServer();
        remember(server, night, centre, spot);
        UUID centreId = centre.getUUID();
        double x = spot.getX() + 0.5;
        double z = spot.getZ() + 0.5;
        if (firstLight) {
            tell(level, centre, Component.translatable(K + "first_light"));
            for (int t = 0; t < 200; t += 10) {
                AliveScheduler.schedule(server, t, () -> level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE,
                        x, spot.getY() + 1.0, z, 6, 0.4, 2.0, 0.4, 0.01));
            }
            impact(level, spot, centreId);
            return true;
        }
        tell(level, centre, Component.translatable(K + "warning"));
        level.playSound(null, x, spot.getY() + 40.0, z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.AMBIENT, 6.0F, 0.5F);
        int streak = HoardFallRules.STREAK_TICKS;
        for (int t = 0; t < streak; t += 10) {
            double y = spot.getY() + 140.0 * (1.0 - t / (double) streak);
            AliveScheduler.schedule(server, t, () -> {
                level.sendParticles(ParticleTypes.FLAME, x, y, z, 14, 0.4, 2.0, 0.4, 0.0);
                level.sendParticles(ParticleTypes.FIREWORK, x, y, z, 8, 0.3, 1.5, 0.3, 0.0);
            });
        }
        for (int t = 0; t < streak; t += 100) {
            AliveScheduler.schedule(server, t, () -> level.playSound(null, x, spot.getY() + 30.0, z,
                    SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.AMBIENT, 4.0F, 0.6F));
        }
        AliveScheduler.schedule(server, streak, () -> impact(level, spot, centreId));
        return true;
    }

    /** The actionbar to every player in the dimension within 512 blocks of the centre. */
    private static void tell(ServerLevel level, ServerPlayer centre, Component line) {
        double r2 = (double) HoardFallRules.WARN_RADIUS * HoardFallRules.WARN_RADIUS;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(centre) <= r2) {
                FirstHourUtil.actionbar(p, line);
            }
        }
    }

    /** The landing: crater (or bowl), ore, the vault, three Aliens, Close Encounter. */
    private static void impact(ServerLevel level, BlockPos spot, UUID centreId) {
        boolean grief = IntegrationsConfig.on(IntegrationsConfig.aliveMeteorGrief);
        double x = spot.getX() + 0.5;
        double z = spot.getZ() + 0.5;
        level.explode(null, x, spot.getY(), z, CRATER_POWER,
                grief ? Level.ExplosionInteraction.BLOCK : Level.ExplosionInteraction.NONE);
        BlockPos crate;
        if (grief) {
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, spot.getX(), spot.getZ());
            crate = new BlockPos(spot.getX(), y, spot.getZ());
            inlayOres(level, x, z, true);
        } else {
            crate = bowl(level, spot);
        }
        ServerPlayer centre = level.getServer().getPlayerList().getPlayer(centreId);
        HoardVault.placeVault(level, crate, centre);
        spawnAliens(level, x, z);
        AliveWorldCompat.grantWitnessed(level, "modern_wonders/close_encounter", x, spot.getY(), z, WITNESS_RADIUS);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, x, spot.getY() + 1.0, z, 60, 2.0, 0.6, 2.0, 0.02);
    }

    /**
     * The no-grief crater: natural ground only in a 7-wide bowl, 2 deep in the middle, three uranium and two titanium
     * in its wall where the wall is natural ground. Returns the vault's spot at the bowl's floor.
     */
    private static BlockPos bowl(ServerLevel level, BlockPos spot) {
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
                    if (FallSafety.natural(level.getBlockState(p)) && level.getBlockEntity(p) == null) {
                        level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
        BlockState uranium = AliveWorldCompat.blockState("orespawn", "ore_uranium");
        BlockState titanium = AliveWorldCompat.blockState("orespawn", "ore_titanium");
        BlockPos[] wall = {new BlockPos(3, -1, 1), new BlockPos(-3, -1, -1), new BlockPos(1, -1, -3),
                new BlockPos(-1, -1, 3), new BlockPos(2, -2, 0)};
        for (int i = 0; i < wall.length; i++) {
            BlockPos p = new BlockPos(spot.getX() + wall[i].getX(), gy + wall[i].getY(), spot.getZ() + wall[i].getZ());
            BlockState ore = i < 3 ? uranium : titanium;
            if (ore != null && FallSafety.natural(level.getBlockState(p))) {
                level.setBlockAndUpdate(p, ore);
            }
        }
        BlockPos floor = new BlockPos(spot.getX(), gy - 1, spot.getZ());
        return level.getBlockState(floor).isAir() ? floor : spot;
    }

    /** With grief on: 3 uranium and 2 titanium under the blast point, in solid ground. */
    private static void inlayOres(ServerLevel level, double tx, double tz, boolean grief) {
        BlockState uranium = AliveWorldCompat.blockState("orespawn", "ore_uranium");
        BlockState titanium = AliveWorldCompat.blockState("orespawn", "ore_titanium");
        if (uranium == null && titanium == null) {
            return;
        }
        RandomSource random = level.random;
        int craterY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(tx), Mth.floor(tz));
        List<BlockPos> spots = new ArrayList<>(5);
        for (int attempt = 0; attempt < 24 && spots.size() < 5; attempt++) {
            int x = Mth.floor(tx) + random.nextInt(5) - 2;
            int z = Mth.floor(tz) + random.nextInt(5) - 2;
            for (int y = craterY - 1; y >= craterY - 6; y--) {
                BlockPos pos = new BlockPos(x, y, z);
                if (spots.contains(pos)) {
                    continue;
                }
                BlockState there = level.getBlockState(pos);
                if (there.isSolidRender(level, pos) && (grief || FallSafety.natural(there))) {
                    spots.add(pos);
                    break;
                }
            }
        }
        for (int i = 0; i < spots.size(); i++) {
            BlockState ore = i < 3 ? uranium : titanium;
            if (ore != null) {
                level.setBlockAndUpdate(spots.get(i), ore);
            }
        }
    }

    /** Three orespawn:alien around the crater rim, spawned as EVENT (no spawn-rule veto). */
    private static void spawnAliens(ServerLevel level, double tx, double tz) {
        EntityType<?> alien = AliveWorldCompat.entityType("orespawn", "alien");
        if (alien == null) {
            return;
        }
        RandomSource random = level.random;
        for (int i = 0; i < 3; i++) {
            double angle = i * (Math.PI * 2.0 / 3.0) + random.nextDouble() * 0.8;
            double r = 4.0 + random.nextDouble() * 2.0;
            double ax = tx + Math.cos(angle) * r;
            double az = tz + Math.sin(angle) * r;
            int ay = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(ax), Mth.floor(az));
            alien.spawn(level, BlockPos.containing(ax, ay, az), MobSpawnType.EVENT);
        }
    }

    /** The Gazette's headline facts: the night, the centre's name, the compass quarter and the spot. */
    private static void remember(MinecraftServer server, long night, ServerPlayer centre, BlockPos spot) {
        CompoundTag storage = server.getCommandStorage().get(STORAGE).copy();
        CompoundTag last = new CompoundTag();
        last.putLong("night", night);
        last.putString("player", centre.getGameProfile().getName());
        last.putString("quarter", HoardFallRules.quarter(spot.getX() - centre.getX(), spot.getZ() - centre.getZ()));
        last.put("pos", new IntArrayTag(new int[]{spot.getX(), spot.getY(), spot.getZ()}));
        storage.put("last", last);
        server.getCommandStorage().set(STORAGE, storage);
    }

    /** The Gazette's headline for the last Fall: "Gold fell north of Ana last night. ..."; null before any. */
    static Component headline(MinecraftServer server) {
        CompoundTag last = server.getCommandStorage().get(STORAGE).getCompound("last");
        if (last.isEmpty()) {
            return null;
        }
        return Component.translatable(K + "headline",
                Component.translatable(K + "quarter." + last.getString("quarter")), last.getString("player"));
    }
}
