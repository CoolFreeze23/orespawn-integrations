package danger.orespawn.integrations.story.week;

import danger.orespawn.integrations.OreSpawnIntegrations;
import danger.orespawn.integrations.story.StoryCore;
import danger.orespawn.integrations.story.StoryScheduler;
import danger.orespawn.integrations.story.firsthour.FirstHourUtil;
import danger.orespawn.integrations.story.shelf9.HubPlaces;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.List;
import java.util.Set;

/**
 * Saturday truco at Seu Zé's counter (wonder-saturday-truco; the Base reading of system-deed-log; npc-seu-ze's
 * 0.10.1 D). Before the regulars arrive in 0.15.0 the week's deeds are Seu Zé's chat lines from the counter.
 *
 * <ul>
 *   <li><b>The reading.</b> A Saturday dusk beat (the dusk check or the listener's first bed attempt, whichever comes
 *   first): every player within 32 blocks of a boteco or a D&T tavern hears up to four of their own retold lines,
 *   each a translate component, so each listener reads them in their own language. A causo in parts goes out 3 s a
 *   part. Every line asks the Saturday chat budget first; a spent budget files the line for Sunday's Gazette.</li>
 *   <li><b>The grant.</b> A retold line whose subject is the listener grants {@code wonder/13} (Saturday Truco), once;
 *   the first reading a player hears also brings Seu Zé's R18 line.</li>
 *   <li><b>Missed it?</b> A player who was asleep, away or far from every boteco hears their own lines the next time
 *   they right-click any Seu Zé before the next Saturday's dusk, once, with the missed-truco line; the shop still
 *   opens (the event is never cancelled).</li>
 * </ul>
 * A venue is any Seu Zé within 32 blocks (his counter, placed, generated, or an old barzinho's), a boteco on the hub
 * list, or the inside of a D&T tavern, read from loaded chunks only and never by a locate.
 */
public final class SaturdayTruco {

    static final String KEY = "orespawn_integrations.seu_ze.";
    static final ResourceLocation SEU_ZE = ResourceLocation.fromNamespaceAndPath("brasilcoisas", "seu_ze");
    static final Set<String> TAVERNS = Set.of("acacia", "birch", "cherry", "dark_oak", "desert", "jungle", "mangrove",
            "oak", "snowy", "spruce", "swamp");
    static final ResourceLocation BOTECO = ResourceLocation.fromNamespaceAndPath(OreSpawnIntegrations.MODID, "boteco");

    private SaturdayTruco() {
    }

    public static void init(IEventBus modBus) {
        Almanac.onDuskBeat("saturday_truco", SaturdayTruco::duskReading);
        NeoForge.EVENT_BUS.addListener(SaturdayTruco::onSeuZe);
    }

    // --- the live reading ---

    static void duskReading(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        long today = LandsWeek.landDay(server);
        if (!TrucoRules.saturday(today) || !atVenue(player)) {
            return;
        }
        WeekState s = WeekData.of(player);
        s.presentSaturday = (int) today;
        WeekData.touch(player);
        read(player, today, false);
    }

    /** Tells a listener their lines for the Saturday {@code saturday}; true when any line was told. */
    static boolean read(ServerPlayer player, long saturday, boolean missed) {
        MinecraftServer server = player.getServer();
        List<Component> lines = DeedBridge.saturdayLines(server, player);
        WeekState s = WeekData.of(player);
        s.heardSaturday = (int) saturday;
        WeekData.touch(player);
        if (lines.isEmpty()) {
            return false;
        }
        int delay = 0;
        if (missed) {
            say(player, Component.translatable("orespawn_integrations.deed.missed_truco"), 0);
            delay += TrucoRules.PART_GAP_TICKS;
        }
        for (Component line : lines) {
            boolean part = line.getContents() instanceof TranslatableContents t && t.getKey().endsWith(".2");
            if (part) {
                delay += TrucoRules.PART_GAP_TICKS;
            }
            if (missed || DeedBridge.saturdayBudget(player)) {
                say(player, line, delay);
            } else {
                fileOverflow(server, line);
            }
        }
        // Every line Seu Zé tells a listener is the listener's own deed: hearing one is Saturday Truco.
        FirstHourUtil.award(player, "wonder/13");
        if (!s.flag(WeekState.FLAG_R18)) {
            s.setFlag(WeekState.FLAG_R18);
            WeekData.touch(player);
            say(player, Component.translatable(KEY + "r18"), delay + TrucoRules.PART_GAP_TICKS);
            FirstHourUtil.award(player, "livro/r18");
        }
        return true;
    }

    private static void fileOverflow(MinecraftServer server, Component line) {
        if (line.getContents() instanceof TranslatableContents t) {
            Object[] args = t.getArgs();
            Object[] plain = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                plain[i] = args[i] instanceof Component c ? c.getString() : String.valueOf(args[i]);
            }
            DeedBridge.fileForGazette(server, t.getKey(), plain);
        }
    }

    /** One chat line from the counter: "Seu Zé: ...", now or {@code delay} ticks later. */
    static void say(ServerPlayer player, Component line, int delay) {
        Component framed = Component.translatable(KEY + "says", line);
        if (delay <= 0) {
            player.sendSystemMessage(framed);
        } else {
            StoryScheduler.schedule(player.getServer(), delay, "truco.line", () -> {
                if (!player.isRemoved()) {
                    player.sendSystemMessage(framed);
                }
            });
        }
    }

    // --- the missed reading ---

    private static void onSeuZe(PlayerInteractEvent.EntityInteract event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer player) || event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND
                    || !SEU_ZE.equals(BuiltInRegistries.ENTITY_TYPE.getKey(event.getTarget().getType()))) {
                return;
            }
            MinecraftServer server = player.getServer();
            long today = LandsWeek.landDay(server);
            long saturday = TrucoRules.lastReading(today, WeekRules.timeOfDay(server.overworld().getDayTime()));
            WeekState s = WeekData.of(player);
            if (s.heardSaturday >= saturday || saturday < 0) {
                return;
            }
            read(player, saturday, true);
        } catch (Throwable t) {
            StoryCore.logOnce("truco.missed", t);
        }
    }

    // --- the venue ---

    /** Within 32 blocks of a Seu Zé, a listed boteco or a D&T tavern, in loaded chunks only. */
    static boolean atVenue(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos at = player.blockPosition();
        AABB box = player.getBoundingBox().inflate(TrucoRules.RADIUS);
        EntityType<?> seuZe = BuiltInRegistries.ENTITY_TYPE.containsKey(SEU_ZE) ? BuiltInRegistries.ENTITY_TYPE.get(SEU_ZE) : null;
        if (seuZe != null && !level.getEntities((Entity) null, box, e -> e.getType() == seuZe).isEmpty()) {
            return true;
        }
        String dim = level.dimension().location().toString();
        for (HubPlaces.Place place : HubPlaces.of(level.getServer(), "boteco")) {
            if (place.dim().equals(dim) && place.pos().closerThan(at, TrucoRules.RADIUS + 12)) {
                return true;
            }
        }
        var structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (int dx = -TrucoRules.RADIUS; dx <= TrucoRules.RADIUS; dx += TrucoRules.RADIUS) {
            for (int dz = -TrucoRules.RADIUS; dz <= TrucoRules.RADIUS; dz += TrucoRules.RADIUS) {
                BlockPos p = at.offset(dx, 0, dz);
                if (!level.hasChunkAt(p)) {
                    continue;
                }
                for (Structure structure : level.structureManager().getAllStructuresAt(p).keySet()) {
                    ResourceLocation id = structures.getKey(structure);
                    if (venueId(id) && level.structureManager().getStructureAt(p, structure).isValid()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    static boolean venueId(ResourceLocation id) {
        if (id == null) {
            return false;
        }
        if (id.equals(BOTECO)) {
            return true;
        }
        return "nova_structures".equals(id.getNamespace()) && id.getPath().startsWith("tavern_")
                && TAVERNS.contains(id.getPath().substring("tavern_".length()));
    }
}
