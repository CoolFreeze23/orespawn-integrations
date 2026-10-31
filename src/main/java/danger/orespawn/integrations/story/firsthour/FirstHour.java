package danger.orespawn.integrations.story.firsthour;

import net.neoforged.bus.api.IEventBus;

/**
 * The first hour's systems that need no partner mod: the attachments and the Priority Ticket, which conducts the
 * rest. The scenes register themselves with {@link FirstHourScenes} from their own mod's init (the flyover and the
 * Fall from {@code compat/alive}, the origin screen from Neo Origins, the Field Odete from CustomNPCs).
 */
public final class FirstHour {

    private FirstHour() {
    }

    public static void init(IEventBus modBus) {
        FirstHourData.register(modBus);
        PriorityTicket.init();
    }
}
