package danger.orespawn.integrations.compat.alive;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Meteor nights: roughly every other night (1/24000 per tick while it is
 * night) a small meteor shower strikes near a random player - warning column,
 * three angled magma "rocks", a crater seeded with orespawn uranium/titanium
 * ore, three aliens on the rim, and the close_encounter advancement for every
 * player near enough to witness it. The whole sequence is a chain of
 * {@link AliveScheduler} tasks, so a server stop mid-shower simply drops the
 * rest of it.
 */
final class MeteorEvent {

    private static final int ROLL = 24000;              // ~once per two nights of night-ticks
    private static final double MIN_DIST = 60.0;        // strike offset from the chosen player
    private static final double DIST_SPREAD = 40.0;     // 60-100 blocks
    private static final float CRATER_POWER = 3.0F;
    private static final int WITNESS_RADIUS = 64;

    private MeteorEvent() {
    }

    static void onLevelTick(LevelTickEvent.Post event) {
        try {
            if (!(event.getLevel() instanceof ServerLevel level)) {
                return;
            }
            if (level.dimension() != Level.OVERWORLD) {
                return;
            }
            if (!level.isNight()) {
                return;
            }
            if (level.random.nextInt(ROLL) != 0) {
                return;
            }
            List<ServerPlayer> players = level.players();
            if (players.isEmpty()) {
                return;
            }
            begin(level, players.get(level.random.nextInt(players.size())));
        } catch (Throwable t) {
            AliveWorldCompat.logOnce("meteor_roll", t);
        }
    }

    private static void begin(ServerLevel level, ServerPlayer player) {
        RandomSource random = level.random;
        double angle = random.nextDouble() * Math.PI * 2.0;
        double dist = MIN_DIST + random.nextDouble() * DIST_SPREAD;
        double tx = player.getX() + Math.cos(angle) * dist;
        double tz = player.getZ() + Math.sin(angle) * dist;
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(tx), Mth.floor(tz));
        MinecraftServer server = level.getServer();

        // t+0..40: warning - a burning column high above the strike point + ominous ambience
        AliveScheduler.schedule(server, 0, () -> level.playSound(null, tx, surfaceY, tz,
                SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 4.0F, 0.55F));
        for (int t = 0; t <= 40; t += 10) {
            AliveScheduler.schedule(server, t, () -> {
                level.sendParticles(ParticleTypes.FLAME,
                        tx, surfaceY + 35.0, tz, 60, 0.6, 20.0, 0.6, 0.0);
                level.sendParticles(ParticleTypes.LARGE_SMOKE,
                        tx, surfaceY + 35.0, tz, 40, 1.0, 20.0, 1.0, 0.0);
            });
        }

        // t+20: three magma rocks streak in at an angle
        AliveScheduler.schedule(server, 20, () -> {
            for (int i = 0; i < 3; i++) {
                double ox = (random.nextDouble() - 0.5) * 50.0;
                double oz = (random.nextDouble() - 0.5) * 50.0;
                BlockPos highPos = BlockPos.containing(tx + ox, surfaceY + 60.0, tz + oz);
                FallingBlockEntity rock =
                        FallingBlockEntity.fall(level, highPos, Blocks.MAGMA_BLOCK.defaultBlockState());
                rock.setDeltaMovement(new Vec3(tx - rock.getX(), surfaceY - rock.getY(), tz - rock.getZ())
                        .normalize().scale(1.4));
                rock.setHurtsEntities(2.0F, 40);
            }
        });

        // t+60: impact - crater, ore inlay, aliens, advancement
        AliveScheduler.schedule(server, 60, () -> {
            level.explode(null, tx, surfaceY, tz, CRATER_POWER, Level.ExplosionInteraction.BLOCK);
            inlayOres(level, tx, tz);
            spawnAliens(level, tx, tz);
            AliveWorldCompat.grantWitnessed(level, "modern_wonders/close_encounter",
                    tx, surfaceY, tz, WITNESS_RADIUS);
        });
    }

    /**
     * Seeds 3x orespawn:ore_uranium + 2x orespawn:ore_titanium into solid
     * blocks just under the (post-explosion) blast point. Runs in the same
     * task as the explosion, which completes synchronously, so the fresh
     * heightmap already reflects the crater.
     */
    private static void inlayOres(ServerLevel level, double tx, double tz) {
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
                if (level.getBlockState(pos).isSolidRender(level, pos)) {
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
            double r = 3.0 + random.nextDouble() * 2.0;
            double ax = tx + Math.cos(angle) * r;
            double az = tz + Math.sin(angle) * r;
            int ay = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(ax), Mth.floor(az));
            alien.spawn(level, BlockPos.containing(ax, ay, az), MobSpawnType.EVENT);
        }
    }
}
