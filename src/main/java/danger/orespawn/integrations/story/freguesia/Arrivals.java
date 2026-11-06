package danger.orespawn.integrations.story.freguesia;

import danger.orespawn.integrations.story.StoryCore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

import java.util.List;
import java.util.function.Predicate;

/**
 * Who moves in (system-freguesia, "Stages"). The stage rule: the dawn check counts the persistent entities carrying
 * the arrival tag {@code cc_chegada} (and the role tag beside it) near the stone and spawns an arrival only when there
 * is none, so a capybara that moved in from a casket is the yard's Capivara and an allay already in the yard is
 * counted (and tagged) as the first stage's arrival. Every arrival is named where the card names it, calls
 * {@code setPersistenceRequired()} and carries the tag. Neighbours within 256 blocks share the stage-3 Odete and the
 * two Guards (counted within 256 of the stone); every other arrival is the stone's own, counted within 48. One
 * bounded entity search per role, at dawn only.
 */
final class Arrivals {

    static final String ARRIVAL = "cc_chegada";
    static final String DOG = "cc_chegada.caramelo";
    static final String LAPWING = "cc_chegada.quero_quero";
    static final String VILLAGER = "cc_chegada.aldeao";
    static final String HUNTER = "cc_chegada.cacador";
    static final String ODETE = "cc_chegada.odete";
    static final String GUARD = "cc_chegada.guarda";

    private static final ResourceLocation CARAMELO = ResourceLocation.fromNamespaceAndPath("brasilcoisas", "cachorro_caramelo");
    private static final ResourceLocation CAPIVARA = ResourceLocation.fromNamespaceAndPath("brasilcoisas", "capivara");
    private static final ResourceLocation QUERO_QUERO = ResourceLocation.fromNamespaceAndPath("brasilcoisas", "quero_quero");
    private static final ResourceLocation ALLAY = ResourceLocation.fromNamespaceAndPath("minecraft", "allay");
    private static final ResourceLocation GUARD_ID = ResourceLocation.fromNamespaceAndPath("guardvillagers", "guard");
    private static final ResourceLocation NPC = ResourceLocation.fromNamespaceAndPath("customnpcs", "customnpc");
    private static final String ODETE_CLONE = "odete_registro";

    private Arrivals() {
    }

    /** Tops up every arrival the stone's stage calls for. */
    static void topUp(ServerLevel level, FreguesiaData data, FreguesiaData.Stone s) {
        try {
            if (s.stage >= StandingRules.HOMESTEAD) {
                homestead(level, s);
            }
            if (s.stage >= StandingRules.HAMLET) {
                hamlet(level, s);
            }
            if (s.stage >= StandingRules.PARISH) {
                parish(level, s);
            }
        } catch (Throwable t) {
            StoryCore.logOnce("freguesia.arrivals", t);
        }
    }

    // --- stage 1: the Caramelo (or a Capivara) and the quero-quero ---

    private static void homestead(ServerLevel level, FreguesiaData.Stone s) {
        List<Entity> dogs = around(level, s.pos, StandingRules.RADIUS, e -> {
            ResourceLocation id = id(e);
            if (ALLAY.equals(id)) {
                return true; // the helper is part of the town
            }
            return e.getTags().contains(ARRIVAL) && (CARAMELO.equals(id) || CAPIVARA.equals(id))
                    && (!(e instanceof Mob m) || m.isPersistenceRequired());
        });
        for (Entity e : dogs) {
            if (!e.getTags().contains(ARRIVAL)) {
                tag(e, DOG);
            }
        }
        if (dogs.isEmpty()) {
            boolean capivara = "curupira".equals(s.founderOrigin) || "capivara".equals(s.founderOrigin);
            Entity e = spawn(level, capivara ? CAPIVARA : CARAMELO, near(level, s.pos, 3), DOG);
            if (e != null) {
                if (!capivara) {
                    e.setCustomName(Component.translatable(StoneRecords.K + "caramelo_name"));
                }
                if (e instanceof TamableAnimal pet && s.founder() != null) {
                    pet.setTame(true, true);
                    pet.setOwnerUUID(s.founder());
                }
            }
        }
        if (around(level, s.pos, StandingRules.RADIUS,
                e -> e.getTags().contains(ARRIVAL) && QUERO_QUERO.equals(id(e))).isEmpty()) {
            spawn(level, QUERO_QUERO, near(level, s.pos, 6), LAPWING);
        }
    }

    // --- stage 2: two villagers and the tent ---

    private static void hamlet(ServerLevel level, FreguesiaData.Stone s) {
        int have = around(level, s.pos, StandingRules.RADIUS,
                e -> e instanceof Villager && e.getTags().contains(VILLAGER)).size();
        int want = 2 - have;
        if (want > 0) {
            long beds = level.getPoiManager().getCountInRange(Arrivals::isHome, s.pos, StandingRules.RADIUS,
                    PoiManager.Occupancy.HAS_SPACE);
            if (beds >= 2) {
                for (int i = 0; i < want; i++) {
                    BlockPos from = walkInFrom(level, s.pos, i);
                    if (from == null) {
                        break;
                    }
                    Entity e = spawn(level, BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.VILLAGER), from, VILLAGER);
                    if (e instanceof Villager v) {
                        v.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                                new WalkTarget(new BlockPosTracker(s.pos), 0.6F, 3));
                    }
                }
            }
        }
        if (around(level, s.pos, StandingRules.RADIUS, e -> e.getTags().contains(HUNTER)).isEmpty()) {
            Plots.raiseTent(level, s);
        }
    }

    private static boolean isHome(Holder<PoiType> holder) {
        return holder.is(PoiTypes.HOME);
    }

    // --- stage 3: the Odete and two Guards, shared by neighbours within 256 ---

    private static void parish(ServerLevel level, FreguesiaData.Stone s) {
        int reach = StandingRules.NEIGHBOUR_RADIUS;
        if (ModList.get().isLoaded("customnpcs")
                && around(level, s.pos, reach, e -> e.getTags().contains(ODETE)).isEmpty()) {
            Entity odete = odete(level, near(level, s.pos, 3));
            if (odete != null && "royal_blood".equals(s.founderOrigin)) {
                ServerPlayer founder = s.founder() == null ? null
                        : level.getServer().getPlayerList().getPlayer(s.founder());
                if (founder != null && founder.distanceToSqr(odete) < 48 * 48) {
                    founder.sendSystemMessage(Component.translatable(StoneRecords.K + "odete_royal_blood"));
                }
            }
        }
        int guards = around(level, s.pos, reach, e -> e.getTags().contains(GUARD)).size();
        for (int i = guards; i < 2; i++) {
            spawn(level, GUARD_ID, near(level, s.pos, 5 + i), GUARD);
        }
    }

    /** The Registry's Odete clone, spawned by command and tagged as the Freguesia's; null without the clone. */
    private static Entity odete(ServerLevel level, BlockPos pos) {
        if (pos == null) {
            return null;
        }
        MinecraftServer server = level.getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput()
                        .withLevel(level).withPermission(4),
                "noppes clone spawn " + ODETE_CLONE + " 9 " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
        for (Entity e : level.getEntities((Entity) null, new AABB(pos).inflate(2.0),
                e -> NPC.equals(id(e)) && !e.getTags().contains(ARRIVAL))) {
            tag(e, ODETE);
            return e;
        }
        return null;
    }

    // --- shared helpers ---

    static List<Entity> around(ServerLevel level, BlockPos center, int radius, Predicate<Entity> filter) {
        AABB box = new AABB(center).inflate(radius, 32, radius);
        double r2 = (double) radius * radius;
        Vec3 c = Vec3.atCenterOf(center);
        return level.getEntities((Entity) null, box, e -> e.isAlive() && filter.test(e)
                && horizontalSq(e.position(), c) <= r2);
    }

    private static double horizontalSq(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return dx * dx + dz * dz;
    }

    static ResourceLocation id(Entity e) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
    }

    static void tag(Entity e, String role) {
        e.addTag(ARRIVAL);
        e.addTag(role);
        if (e instanceof Mob mob) {
            mob.setPersistenceRequired();
        }
    }

    /** Spawns one arrival of {@code type} at {@code pos}, tagged and persistent; null when the type isn't loaded. */
    static Entity spawn(ServerLevel level, ResourceLocation type, BlockPos pos, String role) {
        if (pos == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(type)) {
            return null;
        }
        Entity e = BuiltInRegistries.ENTITY_TYPE.get(type).spawn(level, pos, MobSpawnType.EVENT);
        if (e != null) {
            tag(e, role);
        }
        return e;
    }

    /** A free surface spot about {@code ring} blocks from the stone in a loaded chunk, or null. */
    static BlockPos near(ServerLevel level, BlockPos stone, int ring) {
        for (int i = 0; i < 8; i++) {
            double a = level.random.nextDouble() * Math.PI * 2;
            int x = stone.getX() + (int) Math.round(Math.cos(a) * ring);
            int z = stone.getZ() + (int) Math.round(Math.sin(a) * ring);
            BlockPos top = surface(level, x, z);
            if (top != null) {
                return top;
            }
        }
        return null;
    }

    /** Where a villager starts its walk: 40 blocks out, in a loaded chunk. */
    private static BlockPos walkInFrom(ServerLevel level, BlockPos stone, int salt) {
        for (int i = 0; i < 8; i++) {
            double a = level.random.nextDouble() * Math.PI * 2 + salt;
            int x = stone.getX() + (int) Math.round(Math.cos(a) * 40);
            int z = stone.getZ() + (int) Math.round(Math.sin(a) * 40);
            BlockPos top = surface(level, x, z);
            if (top != null) {
                return top;
            }
        }
        return null;
    }

    static BlockPos surface(ServerLevel level, int x, int z) {
        BlockPos probe = new BlockPos(x, 0, z);
        if (!level.isPositionEntityTicking(probe)) {
            return null;
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos top = new BlockPos(x, y, z);
        if (!level.getFluidState(top).isEmpty() || !level.getFluidState(top.below()).isEmpty()) {
            return null;
        }
        return top;
    }
}
