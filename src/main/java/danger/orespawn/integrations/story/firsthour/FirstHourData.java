package danger.orespawn.integrations.story.firsthour;

import com.mojang.serialization.Codec;
import danger.orespawn.integrations.OreSpawnIntegrations;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;
import java.util.stream.IntStream;

/**
 * The first hour's per-player attachments. {@code cc_first_hour} is the Priority Ticket (six ints, 24 bytes);
 * {@code cc_letters} is the mask of the Prince's letters a player has been sent (bit 0 the Summons), with room for
 * 0.16.0's queue. Both survive death; a player who never had {@code cc_first_hour} is either new (no play time yet)
 * or from before 0.10.0, which {@link PriorityTicket} tells apart at login.
 */
public final class FirstHourData {

    private static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, OreSpawnIntegrations.MODID);

    private static final Codec<TicketState> TICKET_CODEC = Codec.INT_STREAM.xmap(
            stream -> TicketState.fromInts(stream.toArray()),
            state -> IntStream.of(state.toInts()));

    public static final Supplier<AttachmentType<TicketState>> FIRST_HOUR = TYPES.register("cc_first_hour",
            () -> AttachmentType.builder(TicketState::closed).serialize(TICKET_CODEC).copyOnDeath().build());

    public static final Supplier<AttachmentType<Integer>> LETTERS = TYPES.register("cc_letters",
            () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).copyOnDeath().build());

    public static final int LETTER_SUMMONS = 1;

    private FirstHourData() {
    }

    static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }

    public static TicketState ticket(Player player) {
        return player.getData(FIRST_HOUR.get());
    }

    public static boolean hasTicket(Player player) {
        return player.hasData(FIRST_HOUR.get());
    }

    public static boolean letterSent(Player player, int bit) {
        return (player.getData(LETTERS.get()) & bit) != 0;
    }

    public static void markLetterSent(Player player, int bit) {
        player.setData(LETTERS.get(), player.getData(LETTERS.get()) | bit);
    }
}
