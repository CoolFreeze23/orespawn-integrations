package danger.orespawn.integrations.story.firsthour;

import danger.orespawn.integrations.story.Ranks;
import danger.orespawn.integrations.story.StoryCore;
import danger.orespawn.integrations.story.StoryScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import danger.orespawn.integrations.npc.RegistroInstaller;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Field Odete (B04b, P06): when a player opens their First Fall crate, a clerk climbs the far rim, 12 blocks off,
 * walks in, files the applicant (her greeting by the clock, the origin's line, the Form 7-B label, R2), gives the
 * casket tip to a player who has spent no scrap yet, hands over the Field Notebook, sets the gold Registry pin and
 * walks 48 blocks toward it. She is spawned from registry's {@code odete_campo} clone (tab 9) by
 * {@code /noppes clone spawn odete_campo 9 <pos>}, carries the tag {@code cc_event_npc}, and is never saved: removed
 * after 90 s and at her player's logout and server stop, and refused if a chunk unload ever wrote her to disk. Her
 * lines are spoken to her player alone, as keys; the scores {@code cc_turno} and {@code cc_origin_id} are set before
 * she arrives for any dialog the clone carries. Without the clone the lines, the Notebook and the pin still come.
 *
 * <p>Her pin, in order: a listed desk within 2,000 blocks (storage {@code orespawn_integrations:registro}, list
 * {@code desks}; a hub place named {@code registro} first), else the nearest village by a 40-chunk locate (with her
 * refurbishment warning), else the Four Ways line and no pin. A player already served at a desk gets no pin.</p>
 */
public final class FieldOdete {

    public static final String EVENT_TAG = "cc_event_npc";
    public static final String TURN_SCORE = "cc_turno";
    public static final String ORIGIN_ID_SCORE = "cc_origin_id";
    private static final String K = "orespawn_integrations.odete_campo.";
    private static final ResourceLocation NPC = ResourceLocation.fromNamespaceAndPath("customnpcs", "customnpc");
    private static final ResourceLocation DESKS = FirstHourUtil.oi("registro");
    private static final ResourceLocation HUB_PLACES = FirstHourUtil.oi("hub_places");
    private static final int LIFE = 90 * 20;
    private static final int LINE_GAP = 60;
    private static final int DESK_RANGE = 2000;
    private static final int VILLAGE_CHUNKS = 40;

    private record Visit(long scene, Entity npc, boolean veteran) {}

    private static final Map<UUID, Visit> LIVE = new HashMap<>();

    private FieldOdete() {
    }

    public static void init(IEventBus modBus) {
        FirstHourScenes.odete = FieldOdete::start;
        NeoForge.EVENT_BUS.addListener(FieldOdete::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(FieldOdete::onInteract);
        NeoForge.EVENT_BUS.addListener(FieldOdete::onLogout);
        NeoForge.EVENT_BUS.addListener(FieldOdete::onServerStopping);
    }

    private static FirstHourScenes.Result start(ServerPlayer player, BlockPos crate, boolean veteran) {
        if (!(player.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return FirstHourScenes.Result.BUSY;
        }
        MinecraftServer server = level.getServer();
        long scene = FirstHourScenes.CAP.start(server.getTickCount());
        if (scene == 0) {
            return FirstHourScenes.Result.BUSY;
        }
        FirstHourUtil.setScore(player, TURN_SCORE, turn(level.getDayTime()));
        String origin = EggLabel.originPath(player);
        int originId = origin == null ? 0 : OriginGroups.get().originNumberOfPath(origin);
        if (originId > 0) {
            FirstHourUtil.setScore(player, ORIGIN_ID_SCORE, originId);
        } else {
            FirstHourUtil.resetScore(player, ORIGIN_ID_SCORE);
        }
        Entity npc = spawn(level, rimPosition(level, player, crate));
        LIVE.put(player.getUUID(), new Visit(scene, npc, veteran));
        StoryScheduler.schedule(server, LIFE, "odete_campo_leave", () -> remove(player.getUUID()));
        walkTo(npc, player.position());
        StoryScheduler.schedule(server, LINE_GAP * 2, "odete_campo_talk", () -> talk(player, veteran, 0));
        return FirstHourScenes.Result.STARTED;
    }

    /** cc_turno: 0 from 06:00 to 11:59, 1 from 12:00 to 17:59, 2 otherwise (day time 0 is 06:00). */
    public static int turn(long dayTime) {
        int hour = (int) ((dayTime % 24000L) / 1000L + 6) % 24;
        return hour >= 6 && hour < 12 ? 0 : hour >= 12 && hour < 18 ? 1 : 2;
    }

    /** The far rim of the crate, 12 blocks off on the side away from the player; else 12 blocks from the player. */
    private static BlockPos rimPosition(ServerLevel level, ServerPlayer player, BlockPos crate) {
        Vec3 from = crate != null ? Vec3.atCenterOf(crate) : player.position();
        Vec3 dir = crate != null ? from.subtract(player.position()).multiply(1, 0, 1)
                : Vec3.directionFromRotation(0, player.getYRot());
        if (dir.lengthSqr() < 1.0E-4) {
            dir = new Vec3(1, 0, 0);
        }
        Vec3 at = from.add(dir.normalize().scale(12.0));
        int x = (int) Math.floor(at.x);
        int z = (int) Math.floor(at.z);
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    private static Entity spawn(ServerLevel level, BlockPos pos) {
        try {
            GoldPin.run(level.getServer(), "execute in minecraft:overworld run noppes clone spawn odete_campo 9 "
                    + pos.getX() + " " + pos.getY() + " " + pos.getZ());
            AABB box = new AABB(pos).inflate(2.0);
            for (Entity e : level.getEntities((Entity) null, box,
                    e -> NPC.equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()))
                            && !e.getTags().contains(EVENT_TAG))) {
                e.addTag(EVENT_TAG);
                return e;
            }
        } catch (Throwable t) {
            StoryCore.logOnce("odete_campo_spawn", t);
        }
        return null; // no clone: she is heard, not seen
    }

    /**
     * A click on her opens the dialog the clock asks for (the clone's own is the morning one, 720): her options, "What
     * system?" and the coins, live there. Her walk-up lines come in chat either way, so the dialog never pops up unasked.
     */
    private static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide() || event.getHand() != InteractionHand.MAIN_HAND
                || !(event.getEntity() instanceof ServerPlayer player) || !event.getTarget().getTags().contains(EVENT_TAG)) {
            return;
        }
        Visit visit = LIVE.get(player.getUUID());
        if (visit == null || visit.npc() != event.getTarget()) {
            return;
        }
        int dialog = visit.veteran() ? RegistroInstaller.DIALOG_FIELD_VETERAN
                : new int[]{RegistroInstaller.DIALOG_FIELD, RegistroInstaller.DIALOG_FIELD_AFTERNOON,
                        RegistroInstaller.DIALOG_FIELD_EVENING}[turn(player.level().getDayTime())];
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        try {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                    "noppes dialog show " + player.getGameProfile().getName() + " " + dialog + " Odete");
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        } catch (Throwable t) {
            StoryCore.logOnce("odete_campo_dialog", t);
        }
    }

    private static void walkTo(Entity npc, Vec3 target) {
        if (npc instanceof Mob mob && !npc.isRemoved()) {
            mob.getNavigation().moveTo(target.x, target.y, target.z, 0.8);
        }
    }

    /** Her lines, one every three seconds, in the order the card gives. */
    private static void talk(ServerPlayer player, boolean veteran, int line) {
        Visit visit = LIVE.get(player.getUUID());
        if (visit == null || player.hasDisconnected()) {
            return;
        }
        switch (line) {
            case 0 -> {
                say(player, veteran ? "greeting.veteran"
                        : new String[]{"greeting.morning", "greeting.afternoon", "greeting.evening"}
                        [turn(player.level().getDayTime())]);
                String origin = EggLabel.originPath(player);
                if (origin != null && (origin.equals("royal_blood") || origin.equals("saci")
                        || origin.equals("curupira") || origin.equals("time_lord"))) {
                    say(player, "origin." + origin);
                }
            }
            case 1 -> {
                int group = FirstHourUtil.score(player, EggLabel.GROUP_SCORE, 10);
                say(player, "label." + (group < 1 || group > 10 || group == 2 ? 10 : group));
            }
            case 2 -> {
                say(player, "r2");
                FirstHourUtil.award(player, "livro/what_am_i");
            }
            case 3 -> say(player, "file_number");
            case 4 -> {
                if (!Ranks.hasAdvancement(player, "orespawn_integrations:correcoes/primeiro_gasto")) {
                    say(player, "casket_tip");
                }
            }
            case 5 -> {
                say(player, "notebook");
                FirstHourUtil.give(player, FirstHourUtil.patchouliBookStack(FirstHourUtil.oi("caderneta_de_campo")));
            }
            case 6 -> {
                leave(player, visit);
                return;
            }
            default -> {
                return;
            }
        }
        StoryScheduler.schedule(player.getServer(), LINE_GAP, "odete_campo_talk", () -> talk(player, veteran, line + 1));
    }

    /** The Registry pin and the parting line, then the walk toward it. */
    private static void leave(ServerPlayer player, Visit visit) {
        PriorityTicket.announce(player, 6);
        TicketState s = FirstHourData.ticket(player);
        if (s.flag(TicketState.FLAG_REGISTRY_REACHED)) {
            say(player, "leaving");
            return; // already served at a desk: no Registry pin
        }
        ServerLevel level = (ServerLevel) player.level();
        BlockPos desk = registryPin(level, player.blockPosition());
        boolean village = false;
        if (desk == null) {
            desk = level.findNearestMapStructure(StructureTags.VILLAGE, player.blockPosition(), VILLAGE_CHUNKS, false);
            village = desk != null;
        }
        if (desk == null) {
            say(player, "no_village");
            return;
        }
        say(player, "leaving");
        if (village) {
            say(player, "refurbished");
            desk = new BlockPos(desk.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    desk.getX(), desk.getZ()), desk.getZ());
        }
        GoldPin.set(player, TicketState.PIN_REGISTRY, desk.getX(), desk.getY(), desk.getZ());
        if (visit.npc() != null) {
            Vec3 toward = Vec3.atCenterOf(desk).subtract(visit.npc().position()).multiply(1, 0, 1);
            if (toward.lengthSqr() > 1.0E-4) {
                walkTo(visit.npc(), visit.npc().position().add(toward.normalize().scale(48.0)));
            }
        }
    }

    /** The nearest desk on the list (or the hub's registro place) within 2,000 blocks, or null. */
    public static BlockPos registryPin(ServerLevel level, BlockPos from) {
        BlockPos best = null;
        double bestSq = (double) DESK_RANGE * DESK_RANGE;
        CompoundTag hub = level.getServer().getCommandStorage().get(HUB_PLACES);
        for (Tag t : hub.getList("places", Tag.TAG_COMPOUND)) {
            CompoundTag place = (CompoundTag) t;
            if ("registro".equals(place.getString("thing"))) {
                BlockPos p = posOf(place, level);
                if (p != null && p.distSqr(from) <= bestSq) {
                    return p;
                }
            }
        }
        CompoundTag desks = level.getServer().getCommandStorage().get(DESKS);
        for (Tag t : desks.getList("desks", Tag.TAG_COMPOUND)) {
            BlockPos p = posOf((CompoundTag) t, level);
            if (p != null && p.distSqr(from) <= bestSq) {
                bestSq = p.distSqr(from);
                best = p;
            }
        }
        return best;
    }

    private static BlockPos posOf(CompoundTag entry, ServerLevel level) {
        int[] pos = entry.getIntArray("pos");
        String dim = entry.getString("dim");
        if (pos.length != 3 || (!dim.isEmpty() && !dim.equals(level.dimension().location().toString()))) {
            return null;
        }
        return new BlockPos(pos[0], pos[1], pos[2]);
    }

    private static void say(ServerPlayer player, String key) {
        player.sendSystemMessage(Component.translatable("chat.type.text",
                Component.translatable(K + "name"), Component.translatable(K + key)));
    }

    private static void remove(UUID player) {
        Visit visit = LIVE.remove(player);
        if (visit == null) {
            return;
        }
        if (visit.npc() != null && !visit.npc().isRemoved()) {
            visit.npc().discard();
        }
        FirstHourScenes.CAP.end(visit.scene());
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        remove(event.getEntity().getUUID());
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        for (UUID id : java.util.List.copyOf(LIVE.keySet())) {
            remove(id);
        }
    }

    /** Refuses a Field Odete that reached disk anyway (a forced chunk unload): she never comes back as a ghost. */
    private static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.loadedFromDisk()
                && event.getEntity().getTags().contains(EVENT_TAG)) {
            event.setCanceled(true);
            event.getEntity().discard();
        }
    }
}
