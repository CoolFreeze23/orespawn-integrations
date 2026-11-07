package danger.orespawn.integrations.story.hunt;

import com.mojang.datafixers.util.Pair;
import danger.orespawn.integrations.config.IntegrationsConfig;
import danger.orespawn.integrations.story.StoryClock;
import danger.orespawn.integrations.story.StoryCore;
import danger.orespawn.integrations.story.firsthour.FirstHourData;
import danger.orespawn.integrations.story.firsthour.FirstHourUtil;
import danger.orespawn.integrations.story.firsthour.GoldPin;
import danger.orespawn.integrations.story.firsthour.TicketState;
import danger.orespawn.integrations.story.shelf9.HubPlaces;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.ArrayList;
import java.util.List;

/**
 * Where the Hunt is (structure-hunt-lodge, npc-iracema): the Registry's Lodge errand, the first hour's third gold pin.
 * Once a player's ticket has reached the Registry (P09), the pin goes to the Lodge: the hub's placed one first
 * ({@code hub_places}), then the world's ring start (ring positions are precomputed, so the lookup is instant); with no
 * Lodge, one locate on the Monster Hunter tents (at most 50 chunks out, never past 4,000 blocks from spawn) pins the
 * tent nearest the desk; with none in range the desk says so and pins nothing.
 */
public final class HuntLodge {

    static final ResourceKey<Structure> LODGE = ResourceKey.create(Registries.STRUCTURE, FirstHourUtil.oi("hunt_lodge"));
    private static final String[] TENTS = {"blue", "brown", "cyan", "gray", "green", "light_gray", "purple", "red", "white"};
    private static final int TENT_RADIUS_CHUNKS = 50;
    private static final int MAX_FROM_SPAWN = 4000;

    private HuntLodge() {
    }

    static void init() {
        StoryClock.everySecondPerPlayer("hunt_lodge_errand", HuntLodge::second);
    }

    private static void second(ServerPlayer player) {
        if (!IntegrationsConfig.on(IntegrationsConfig.storyRoyalHunt) || !FirstHourData.hasTicket(player)) {
            return;
        }
        TicketState s = FirstHourData.ticket(player);
        if (!s.handed(9)) {
            return;
        }
        CompoundTag state = player.getData(MondayBoard.STATE.get());
        if (state.getBoolean("errand")) {
            return;
        }
        CompoundTag next = state.copy();
        next.putBoolean("errand", true);
        player.setData(MondayBoard.STATE.get(), next);
        errand(player);
    }

    /** Sets the Lodge errand pin from where {@code player} stands (the desk), or says there is no tent in range. */
    public static void errand(ServerPlayer player) {
        try {
            BlockPos target = nearestBoard(player.serverLevel(), player.blockPosition());
            if (target != null) {
                GoldPin.set(player, TicketState.PIN_LODGE, target.getX(), target.getY(), target.getZ());
            } else {
                MondayBoard.say(player, Component.translatable(MondayBoard.KEY + "no_tent"));
            }
        } catch (Throwable t) {
            StoryCore.logOnce("hunt_lodge_errand", t);
        }
    }

    /** The world's ring start, by the lookup the board itself uses; null when the ring gave this world no Lodge. */
    static BlockPos hasStart(ServerLevel level, BlockPos from) {
        Holder<Structure> lodge = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolder(LODGE).orElse(null);
        if (lodge == null) {
            return null;
        }
        Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator()
                .findNearestMapStructure(level, HolderSet.direct(lodge), from, 64, false);
        return found == null ? null : found.getFirst();
    }

    /** The Lodge (hub list, then the ring start), else the nearest Hunter tent within range; null when none. */
    public static BlockPos nearestBoard(ServerLevel level, BlockPos from) {
        if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) {
            level = level.getServer().overworld();
        }
        BlockPos best = null;
        for (HubPlaces.Place p : HubPlaces.of(level.getServer(), "hunt_lodge")) {
            if (best == null || p.pos().distSqr(from) < best.distSqr(from)) {
                best = p.pos();
            }
        }
        if (best != null) {
            return best;
        }
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        if (IntegrationsConfig.on(IntegrationsConfig.storyHuntLodge)) {
            BlockPos start = hasStart(level, from);
            if (start != null) {
                return start;
            }
        }
        List<Holder<Structure>> tents = new ArrayList<>();
        for (String color : TENTS) {
            registry.getHolder(ResourceKey.create(Registries.STRUCTURE,
                    ResourceLocation.fromNamespaceAndPath("monster_hunter_villager", "hunters_tent_" + color))).ifPresent(tents::add);
        }
        if (tents.isEmpty()) {
            return null;
        }
        Pair<BlockPos, Holder<Structure>> tent = level.getChunkSource().getGenerator()
                .findNearestMapStructure(level, HolderSet.direct(tents), from, TENT_RADIUS_CHUNKS, false);
        if (tent == null) {
            return null;
        }
        BlockPos spawn = level.getSharedSpawnPos();
        return tent.getFirst().distSqr(spawn) <= (double) MAX_FROM_SPAWN * MAX_FROM_SPAWN ? tent.getFirst() : null;
    }
}
