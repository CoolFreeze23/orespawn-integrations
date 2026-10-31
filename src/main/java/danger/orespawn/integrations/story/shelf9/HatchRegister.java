package danger.orespawn.integrations.story.shelf9;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.neoforged.fml.ModList;

import java.util.UUID;

/**
 * The Hatch Register (structure-hatching-ground): the eight signs on the west wall inside Shelf 9, one line per player
 * in join order, the player's name. Each line's origin (once picked on the Awakening screen) and world day are kept in
 * {@link HatchingGroundData} for Jade over the sign ({@code {player} · {origin} · day {day}}). The line number is kept with the player (their fare number
 * for Mothra from 0.10.3). A player who joined before 0.10.0 gets the next free line at their first login after it,
 * with the late-entry line on the actionbar. Lines are entered whether or not Shelf 9 stands, so a hub that places it
 * later still finds every name: placing it writes all the rows at once.
 */
public final class HatchRegister {

    private static final String KEY = "orespawn_integrations.shelf9.register.";

    private HatchRegister() {
    }

    /** The player's line number, or 0 when they have none yet. */
    public static int lineOf(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return 0;
        }
        Integer line = HatchingGroundData.get(server).lines.get(player.getUUID());
        return line == null ? 0 : line;
    }

    /** Enters the player if they have no line yet; returns true when a new line was written. */
    public static boolean enter(ServerPlayer player, boolean late) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        HatchingGroundData data = HatchingGroundData.get(server);
        UUID id = player.getUUID();
        if (data.lines.containsKey(id)) {
            return false;
        }
        int line = data.nextLine++;
        data.lines.put(id, line);
        data.names.put(id, player.getGameProfile().getName());
        data.days.put(id, Eggshell.gameDay(server));
        ResourceLocation origin = originOf(player);
        if (origin == null) {
            data.pendingOrigin.add(id);
        } else {
            data.origins.put(id, origin.toString());
        }
        data.setDirty();
        writeLine(server, data, id);
        if (late) {
            player.displayClientMessage(Component.translatable(KEY + "late_entry", line), true);
        }
        return true;
    }

    /** Once a second per player: a line still waiting for its origin is rewritten as soon as the origin is picked. */
    static void checkPendingOrigin(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        HatchingGroundData data = HatchingGroundData.get(server);
        if (data.pendingOrigin.isEmpty() || !data.pendingOrigin.contains(player.getUUID())) {
            return;
        }
        ResourceLocation origin = originOf(player);
        if (origin != null) {
            data.pendingOrigin.remove(player.getUUID());
            data.origins.put(player.getUUID(), origin.toString());
            data.setDirty();
            writeLine(server, data, player.getUUID());
        }
    }

    /** Rewrites every row (after a placement, so a hub's earlier joiners are on the wall at once). */
    static void writeAll(MinecraftServer server) {
        HatchingGroundData data = HatchingGroundData.get(server);
        for (UUID id : data.lines.keySet()) {
            writeLine(server, data, id);
        }
    }

    private static void writeLine(MinecraftServer server, HatchingGroundData data, UUID id) {
        if (!data.placed || data.registerSigns.size() < RegisterLayout.SIGNS) {
            return;
        }
        ServerLevel level = server.overworld();
        int line = data.lines.getOrDefault(id, 0);
        int sign = RegisterLayout.sign(line);
        if (sign < 0) {
            writeOverflow(level, data);
            return;
        }
        // The sign row is the name alone; each line's origin and day stay in the SavedData for Jade over the sign.
        Component text = Component.literal(data.names.getOrDefault(id, "?"));
        int row = RegisterLayout.row(line);
        editSign(level, data.registerSigns.get(sign), true, t -> t.setMessage(row, text));
    }

    private static void writeOverflow(ServerLevel level, HatchingGroundData data) {
        int more = RegisterLayout.overflow(data.nextLine - 1);
        Component text = Component.translatable(KEY + "overflow", more);
        editSign(level, data.registerSigns.get(RegisterLayout.SIGNS - 1), false, t -> t.setMessage(0, text));
    }

    static void editSign(ServerLevel level, BlockPos pos, boolean front,
                         java.util.function.UnaryOperator<SignText> edit) {
        if (pos == null || !level.isLoaded(pos)) {
            return;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof SignBlockEntity sign) {
            sign.setText(edit.apply(sign.getText(front)), front);
            level.sendBlockUpdated(pos, sign.getBlockState(), sign.getBlockState(), 3);
        }
    }

    /** The picked origin's id, or null (no Neo Origins, or the Awakening screen still open). */
    static ResourceLocation originOf(ServerPlayer player) {
        if (!ModList.get().isLoaded("neoorigins")) {
            return ResourceLocation.withDefaultNamespace("none");
        }
        try {
            return OriginLookup.origin(player);
        } catch (Throwable t) {
            return ResourceLocation.withDefaultNamespace("none");
        }
    }
}
