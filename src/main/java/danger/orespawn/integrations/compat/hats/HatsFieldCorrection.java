package danger.orespawn.integrations.compat.hats;

import me.guivnf.mods.hats.common.event.ServerEventHandler;
import me.guivnf.mods.hats.common.hat.HatPart;
import me.guivnf.mods.hats.common.network.NetworkHelper;
import me.guivnf.mods.hats.common.world.HatsSavedData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

/**
 * The field Correction's Hats Renewed half ({@code item-ledger-scrap}): a stolen hat sent from a creature's head to the
 * player's Wardrobe. Compile-only calls, checked against {@code HatsRenewed-21.1.1-mc1.21.1-neoforge.jar}; only ever
 * loaded behind a {@code ModList.isLoaded("hats")} check. It does what the mod's own kill hand-over does
 * ({@code ServerEventHandler.onEntityDeath}: clear the entity's hat, then {@code onPlayerPickupHat}), without the kill,
 * and broadcasts the bare head so every client drops the hat at once. {@code onPlayerPickupHat} adds the hat to the
 * Wardrobe (a repeat raises its count) and plays the mod's own toast for a hat the player never had.
 */
public final class HatsFieldCorrection {

    /** No hat on the creature; a hat new to the player's Wardrobe; a hat the player already had. */
    public static final int NO_HAT = 0;
    public static final int NEW = 1;
    public static final int REPEAT = 2;

    private HatsFieldCorrection() {
    }

    public static int refile(ServerPlayer player, Mob mob) {
        HatsSavedData data = HatsSavedData.get(mob.level());
        HatPart hat = data.getEntityHat(mob.getUUID());
        if (hat == null) {
            return NO_HAT;
        }
        boolean repeat = data.hasHat(player.getUUID(), hat.name);
        data.setEntityHat(mob.getUUID(), null);
        data.setDirty();
        NetworkHelper.broadcastEntityHat(mob);
        ServerEventHandler.onPlayerPickupHat(player, hat);
        return repeat ? REPEAT : NEW;
    }
}
