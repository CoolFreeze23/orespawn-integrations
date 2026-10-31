package danger.orespawn.integrations.story.hunt;

import net.neoforged.bus.api.IEventBus;

/**
 * The Royal Hunt's systems that need no partner mod (0.10.1): the Wanted poster and the hunt data condition, the Wall
 * of the Wanted's kill handler and signs, Monday's board and the Lodge errand pin. The CustomNPCs half (Captain
 * Iracema's install, her clone's spawn at a Lodge, the tent board's dialog, the re-keyed quests' kill bridge) is
 * {@link danger.orespawn.integrations.npc.HuntInstaller}, listed under {@code customnpcs}.
 */
public final class HuntCore {

    private HuntCore() {
    }

    public static void init(IEventBus modBus) {
        HuntBlocks.register(modBus);
        WallOfTheWanted.init();
        MondayBoard.init(modBus);
        HuntLodge.init();
    }
}
