package danger.orespawn.integrations.story.firsthour;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Predicate;

/**
 * Where the ticket meets the systems that stage its scenes. The ticket never names a partner mod's class: the
 * Prince's flyover and the First Fall live in {@code compat/alive} (OreSpawn), the origin screen in Neo Origins, and
 * each fills its hook here at init. A hook left empty means that partner is absent, and its step is passed.
 */
public final class FirstHourScenes {

    public enum Result {
        /** The scene started; the step is handed over. */
        STARTED,
        /** The scene cap, a flyover already airborne, or a retry timer: try again next second. */
        BUSY,
        /** The scene can never run here (its mod or switch is off): pass the step. */
        UNAVAILABLE
    }

    public interface Letter {
        /** Starts the forced low flyover over {@code player} that drops their Summons. */
        Result start(ServerPlayer player, boolean numbered);
    }

    public interface Fall {
        /** Starts {@code player}'s First Fall (or Back-Dated Fall). */
        Result start(ServerPlayer player, boolean backDated);
    }

    public interface Odete {
        /** Sends the Field Odete to {@code player}, from the far rim of {@code crate} (or toward the player). */
        Result start(ServerPlayer player, BlockPos crate, boolean veteran);
    }

    /** The scene cap every first-hour scene starts through. */
    public static final SceneCap CAP = new SceneCap();

    public static volatile Letter letter;
    public static volatile Fall fall;
    public static volatile Odete odete;
    /** True once the player has closed the origin screen (both enabled layers chosen). Neo Origins fills it. */
    public static volatile Predicate<ServerPlayer> originChosen = p -> true;

    private FirstHourScenes() {
    }
}
