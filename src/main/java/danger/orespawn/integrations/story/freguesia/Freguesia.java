package danger.orespawn.integrations.story.freguesia;

import danger.orespawn.integrations.story.StoryClock;
import danger.orespawn.integrations.story.week.LandsWeek;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * The Freguesia (system-freguesia, item-marco-da-freguesia, wonder-wednesday-market), OI 0.10.1: the Parish Stone and
 * the Plot Stake, the stone's record and its O(1) standing counter, the dawn stage check with its arrivals, the
 * signing handlers, the Jade lines and Wednesday's peddler. Every timed part rides the shared clock or the Land's one
 * dawn job; nothing here scans the world on a tick. Needs no partner mod: each partner (Brasil e Coisas, Guard
 * Villagers, CustomNPCs, Monster Hunter Villager, Railcraft) is looked up by id and skipped when absent.
 */
public final class Freguesia {

    private Freguesia() {
    }

    public static void init(IEventBus modBus) {
        FreguesiaBlocks.register(modBus);
        NeoForge.EVENT_BUS.addListener(StoneCounter::onPlace);
        NeoForge.EVENT_BUS.addListener(StoneCounter::onWake);
        NeoForge.EVENT_BUS.addListener(StoneCounter::onDeath);
        NeoForge.EVENT_BUS.addListener(StoneCounter::onLogin);
        NeoForge.EVENT_BUS.addListener(StoneSigning::onRightClick);
        NeoForge.EVENT_BUS.addListener(StoneSigning::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(Freguesia::onStarted);
        NeoForge.EVENT_BUS.addListener(Freguesia::onStopped);
        LandsWeek.onDawn("freguesia", StoneDawn::onDawn);
        StoryClock.everySecond("freguesia.noon_bell", StoneDawn::noonBell);
        StoryClock.everySecond("freguesia.market", WednesdayMarket::everySecond);
        StoryClock.everySecond("freguesia.yard_deeds", YardDeeds::everySecond);
    }

    private static void onStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        StoneRecords.mirror(server);
    }

    private static void onStopped(ServerStoppedEvent event) {
        StoneCounter.reset();
        StoneSigning.reset();
        StoneDawn.reset();
        WednesdayMarket.reset();
    }
}
