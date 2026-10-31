package danger.orespawn.integrations.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import danger.orespawn.integrations.OreSpawnIntegrations;
import danger.orespawn.integrations.story.shelf9.HatchingGroundData;
import danger.orespawn.integrations.story.shelf9.HatchingGroundPlacer;
import danger.orespawn.integrations.story.shelf9.HubPlaces;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BedBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@code /cc}, the operators' root (permission 2) for the hub's placements (structure-hatching-ground,
 * system-cronograma-de-obras):
 * <ul>
 *   <li>{@code /cc hatching_ground place [keep_spawn]}: Shelf 9 at the operator's feet, in Java; moves world spawn onto
 *   the straw unless {@code keep_spawn};</li>
 *   <li>{@code /cc <thing> place [variant]} and {@code /cc <thing> upgrade}: run the datapack function
 *   {@code orespawn_integrations:ops/<thing>/place} (or {@code /upgrade}) at the operator's feet when it exists;</li>
 *   <li>{@code /cc hub_places}: the hub list.</li>
 * </ul>
 * Before a place, every chunk of the thing's footprint must be loaded, a card's "too close" distance is checked, and a
 * one-off thing already in the hub list is refused with the first one's position.
 *
 * <p>The function hand-off: before running, the command writes {@code orespawn_integrations:cc_args}
 * {@code {thing, action, variant}}; the function may write {@code orespawn_integrations:cc_result} with
 * {@code placed:0b} (it refused), {@code pos:[I;x,y,z]} (where the place really is), {@code pieces} and
 * {@code version} (an upgrade's count). With no result, a place counts as done at the operator's feet.
 */
public final class CcCommand {

    public static final ResourceLocation ARGS =
            ResourceLocation.fromNamespaceAndPath(OreSpawnIntegrations.MODID, "cc_args");
    public static final ResourceLocation RESULT =
            ResourceLocation.fromNamespaceAndPath(OreSpawnIntegrations.MODID, "cc_result");
    /** firsthour's block tag of home block entities; read when present (it carries the Parish Stone later). */
    static final TagKey<Block> HOMELY = BlockTags.create(
            ResourceLocation.fromNamespaceAndPath(OreSpawnIntegrations.MODID, "homely"));
    static final String KEY = "orespawn_integrations.cc.";

    private CcCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cc")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("hatching_ground")
                        .then(Commands.literal("place")
                                .executes(ctx -> placeShelf9(ctx.getSource(), true))
                                .then(Commands.literal("keep_spawn")
                                        .executes(ctx -> placeShelf9(ctx.getSource(), false)))))
                .then(Commands.literal("hub_places").executes(ctx -> listHub(ctx.getSource())))
                .then(Commands.argument("thing", StringArgumentType.word())
                        .suggests(THINGS)
                        .then(Commands.literal("place")
                                .executes(ctx -> dispatch(ctx, "place", ""))
                                .then(Commands.argument("variant", StringArgumentType.word())
                                        .executes(ctx -> dispatch(ctx, "place",
                                                StringArgumentType.getString(ctx, "variant")))))
                        .then(Commands.literal("upgrade")
                                .executes(ctx -> dispatch(ctx, "upgrade", "")))));
    }

    /** Suggests every thing that has an {@code ops/<thing>/place} or {@code /upgrade} function in the loaded data. */
    private static final SuggestionProvider<CommandSourceStack> THINGS = (ctx, builder) -> {
        List<String> things = new ArrayList<>();
        for (ResourceLocation id : ctx.getSource().getServer().getFunctions().getFunctionNames()) {
            String path = id.getPath();
            if (OreSpawnIntegrations.MODID.equals(id.getNamespace()) && path.startsWith("ops/")
                    && (path.endsWith("/place") || path.endsWith("/upgrade"))) {
                String thing = path.substring(4, path.lastIndexOf('/'));
                if (!thing.contains("/") && !things.contains(thing)) {
                    things.add(thing);
                }
            }
        }
        return SharedSuggestionProvider.suggest(things, builder);
    };

    static int placeShelf9(CommandSourceStack source, boolean moveSpawn) {
        String thing = "hatching_ground";
        MinecraftServer server = source.getServer();
        ServerLevel level = source.getLevel();
        HatchingGroundData data = HatchingGroundData.get(server);
        if (data.placed) {
            BlockPos at = data.origin.offset(10, 5, 10);
            source.sendFailure(Component.translatable(KEY + "refused_twice", thing, at.getX(), at.getY(), at.getZ()));
            return 0;
        }
        BlockPos ground = BlockPos.containing(source.getPosition()).below();
        if (!HatchingGroundPlacer.footprintLoaded(level, ground, CcRules.footprint(thing))) {
            source.sendFailure(Component.translatable(KEY + "refused_unloaded", thing));
            return 0;
        }
        if (moveSpawn) {
            source.sendSuccess(() -> Component.translatable(KEY + "warn_spawn"), true);
        }
        BlockPos spawn = HatchingGroundPlacer.place(level, ground, moveSpawn, "command");
        if (spawn == null) {
            source.sendFailure(Component.translatable(KEY + "failed", thing));
            return 0;
        }
        HubPlaces.add(server, thing, spawn, level.dimension().location().toString());
        source.sendSuccess(() -> Component.translatable(KEY + "placed", thing,
                spawn.getX(), spawn.getY(), spawn.getZ()), true);
        source.sendSuccess(() -> Component.translatable(KEY + "ways_pending"), true);
        return 1;
    }

    private static int dispatch(CommandContext<CommandSourceStack> ctx, String action, String variant) {
        CommandSourceStack source = ctx.getSource();
        String thing = StringArgumentType.getString(ctx, "thing");
        MinecraftServer server = source.getServer();
        if (!CcRules.validName(thing)) {
            source.sendFailure(Component.translatable(KEY + "nothing", thing));
            return 0;
        }
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(OreSpawnIntegrations.MODID,
                "ops/" + thing + "/" + action);
        var function = server.getFunctions().get(id);
        if (function.isEmpty()) {
            source.sendFailure(Component.translatable(KEY + "nothing", thing));
            return 0;
        }
        ServerLevel level = source.getLevel();
        BlockPos feet = BlockPos.containing(source.getPosition());
        String dim = level.dimension().location().toString();
        if ("place".equals(action)) {
            List<HubPlaces.Place> already = HubPlaces.of(server, thing);
            if (CcRules.full(thing, already.size())) {
                BlockPos at = already.get(0).pos();
                source.sendFailure(Component.translatable(KEY + "refused_twice", thing, at.getX(), at.getY(), at.getZ()));
                return 0;
            }
            if (HubPlaces.all(server).size() >= HubPlaces.MAX) {
                source.sendFailure(Component.translatable(KEY + "hub_full", thing, HubPlaces.MAX));
                return 0;
            }
            if (!HatchingGroundPlacer.footprintLoaded(level, feet, CcRules.footprint(thing))) {
                source.sendFailure(Component.translatable(KEY + "refused_unloaded", thing));
                return 0;
            }
            int clear = CcRules.keepClear(thing);
            if (clear > 0) {
                int near = nearestHome(level, feet, clear);
                if (near >= 0) {
                    source.sendFailure(Component.translatable(KEY + "refused_close", thing, near));
                    return 0;
                }
            }
        }
        CompoundTag args = new CompoundTag();
        args.putString("thing", thing);
        args.putString("action", action);
        args.putString("variant", variant);
        server.getCommandStorage().set(ARGS, args);
        server.getCommandStorage().set(RESULT, new CompoundTag());
        server.getFunctions().execute(function.get(), source.withPermission(2));
        CompoundTag result = server.getCommandStorage().get(RESULT);

        if ("upgrade".equals(action)) {
            int pieces = result.getInt("pieces");
            String version = result.contains("version", Tag.TAG_STRING) ? result.getString("version") : HubPlaces.VERSION;
            source.sendSuccess(() -> Component.translatable(KEY + "upgraded", thing, pieces, version), true);
            return Math.max(1, pieces);
        }
        if (result.contains("placed") && !result.getBoolean("placed")) {
            source.sendFailure(Component.translatable(KEY + "refused_unloaded", thing));
            return 0;
        }
        int[] p = result.getIntArray("pos");
        BlockPos at = p.length == 3 ? new BlockPos(p[0], p[1], p[2]) : feet;
        HubPlaces.add(server, thing, at, dim);
        source.sendSuccess(() -> Component.translatable(KEY + "placed", thing, at.getX(), at.getY(), at.getZ()), true);
        return 1;
    }

    /**
     * The distance to the nearest bed, chest or {@code #orespawn_integrations:homely} block entity within
     * {@code radius} of {@code centre}, or -1 when there is none. Only loaded chunks are read: a base no one has
     * loaded is far enough from any operator standing on the spot.
     */
    public static int nearestHome(ServerLevel level, BlockPos centre, int radius) {
        double best = Double.MAX_VALUE;
        int cr = (radius >> 4) + 1;
        for (int cx = (centre.getX() >> 4) - cr; cx <= (centre.getX() >> 4) + cr; cx++) {
            for (int cz = (centre.getZ() >> 4) - cr; cz <= (centre.getZ() >> 4) + cr; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
                    BlockEntity be = entry.getValue();
                    boolean home = be instanceof BedBlockEntity || be instanceof ChestBlockEntity
                            || be.getBlockState().is(HOMELY);
                    if (!home) {
                        continue;
                    }
                    double d = Math.sqrt(entry.getKey().distSqr(centre));
                    if (d <= radius && d < best) {
                        best = d;
                    }
                }
            }
        }
        return best == Double.MAX_VALUE ? -1 : (int) Math.floor(best);
    }

    private static int listHub(CommandSourceStack source) {
        List<HubPlaces.Place> places = HubPlaces.all(source.getServer());
        source.sendSuccess(() -> Component.translatable(KEY + "hub_list", places.size(), HubPlaces.MAX), false);
        for (HubPlaces.Place place : places) {
            source.sendSuccess(() -> Component.translatable(KEY + "hub_entry", place.thing(),
                    place.pos().getX(), place.pos().getY(), place.pos().getZ(), place.dim()), false);
        }
        return places.size();
    }
}
