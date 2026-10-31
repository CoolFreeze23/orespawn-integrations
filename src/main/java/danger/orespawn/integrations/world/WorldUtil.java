package danger.orespawn.integrations.world;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

/** Small helpers the world group's systems share: granting an advancement by id and setting a dummy score. */
public final class WorldUtil {

    private WorldUtil() {
    }

    /** Grants every remaining criterion of {@code id}; false when it doesn't exist or was already done. */
    public static boolean grant(ServerPlayer player, String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        MinecraftServer server = player.getServer();
        if (location == null || server == null) {
            return false;
        }
        AdvancementHolder holder = server.getAdvancements().get(location);
        if (holder == null) {
            return false;
        }
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
        if (progress.isDone()) {
            return false;
        }
        for (String criterion : progress.getRemainingCriteria()) {
            player.getAdvancements().award(holder, criterion);
        }
        return true;
    }

    public static boolean done(ServerPlayer player, String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        MinecraftServer server = player.getServer();
        if (location == null || server == null) {
            return false;
        }
        AdvancementHolder holder = server.getAdvancements().get(location);
        return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    /** Sets {@code player}'s score on dummy objective {@code name}, creating the objective when missing. */
    public static void setScore(ServerPlayer player, String name, int value) {
        Scoreboard board = player.server.getScoreboard();
        Objective objective = board.getObjective(name);
        if (objective == null) {
            objective = board.addObjective(name, ObjectiveCriteria.DUMMY, Component.literal(name),
                    ObjectiveCriteria.RenderType.INTEGER, false, null);
        }
        board.getOrCreatePlayerScore(player, objective).set(value);
    }
}
