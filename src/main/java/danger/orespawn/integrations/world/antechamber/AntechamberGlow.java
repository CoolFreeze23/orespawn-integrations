package danger.orespawn.integrations.world.antechamber;

import danger.orespawn.integrations.config.IntegrationsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Comparator;
import java.util.List;

/**
 * The door's glow (structure-chaos-antechamber, client only): OreSpawn's butterfly and luna moth renderers draw no
 * glow, so from dusk (time of day 12,000) to dawn, every 10 client ticks, the 10 nearest {@code orespawn:butterfly} and
 * {@code orespawn:luna_moth} within 32 blocks of the camera each get one faint {@code minecraft:glow} particle: at most
 * 10 every half second, 20 a second in all. Naturalist's and Distant Lands' butterflies never glow, so at dusk the one
 * that shines is the door. Nothing is sent or saved.
 */
final class AntechamberGlow {

    private static final ResourceLocation BUTTERFLY = ResourceLocation.fromNamespaceAndPath("orespawn", "butterfly");
    private static final ResourceLocation LUNA_MOTH = ResourceLocation.fromNamespaceAndPath("orespawn", "luna_moth");
    private static final int PERIOD = 10;
    private static final int NEAREST = 10;
    private static final double RADIUS = 32.0;

    private static int ticks;
    private static EntityType<?> butterfly;
    private static EntityType<?> lunaMoth;

    private AntechamberGlow() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(AntechamberGlow::onClientTick);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        if (++ticks % PERIOD != 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused() || !IntegrationsConfig.on(IntegrationsConfig.storyAntechamber)) {
            return;
        }
        if (Math.floorMod(level.getDayTime(), 24000L) < 12000L) {
            return;
        }
        if (butterfly == null) {
            butterfly = BuiltInRegistries.ENTITY_TYPE.get(BUTTERFLY);
            lunaMoth = BuiltInRegistries.ENTITY_TYPE.get(LUNA_MOTH);
        }
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        List<Entity> doors = level.getEntities((Entity) null, new AABB(camera, camera).inflate(RADIUS),
                e -> (e.getType() == butterfly || e.getType() == lunaMoth)
                        && e.distanceToSqr(camera) <= RADIUS * RADIUS);
        if (doors.isEmpty()) {
            return;
        }
        doors.sort(Comparator.comparingDouble(e -> e.distanceToSqr(camera)));
        for (int i = 0; i < doors.size() && i < NEAREST; i++) {
            Entity e = doors.get(i);
            level.addParticle(ParticleTypes.GLOW, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 0.0, 0.01, 0.0);
        }
    }
}
