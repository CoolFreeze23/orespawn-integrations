package danger.orespawn.integrations.story.week;

import com.mojang.serialization.Codec;
import danger.orespawn.integrations.OreSpawnIntegrations;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.IntStream;

/**
 * The week's per-player attachments: {@code cc_week} (the {@link WeekState} days, 32 bytes) and
 * {@code cc_week_explainers} (the first week's explainers waiting for the next dawn, a few short keys). Both survive
 * death.
 */
public final class WeekData {

    private static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, OreSpawnIntegrations.MODID);

    private static final Codec<WeekState> STATE_CODEC = Codec.INT_STREAM.xmap(
            stream -> WeekState.fromInts(stream.toArray()),
            state -> IntStream.of(state.toInts()));

    public static final Supplier<AttachmentType<WeekState>> WEEK = TYPES.register("cc_week",
            () -> AttachmentType.builder(WeekState::new).serialize(STATE_CODEC).copyOnDeath().build());

    public static final Supplier<AttachmentType<List<String>>> EXPLAINERS = TYPES.register("cc_week_explainers",
            () -> AttachmentType.<List<String>>builder(() -> new ArrayList<>())
                    .serialize(Codec.STRING.listOf().xmap(ArrayList::new, l -> l)).copyOnDeath().build());

    private WeekData() {
    }

    static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }

    public static WeekState of(Player player) {
        return player.getData(WEEK.get());
    }

    /** Marks the record changed, so it is saved with the player. */
    public static void touch(Player player) {
        player.setData(WEEK.get(), player.getData(WEEK.get()));
    }
}
