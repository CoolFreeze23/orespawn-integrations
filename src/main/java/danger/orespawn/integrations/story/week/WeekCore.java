package danger.orespawn.integrations.story.week;

import com.mojang.serialization.MapCodec;
import danger.orespawn.integrations.OreSpawnIntegrations;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * The First Night and Week's always-on half (OI 0.10.1, no partner mod): the week's attachments and its data
 * condition, the Land's Week and its dawn job, the Server's Ledger's dawn queue, the first week's explainers, and the
 * operator commands under {@code /cc}. The systems that need a partner mod register from their own class in
 * {@code OreSpawnIntegrations.COMPAT}.
 */
public final class WeekCore {

    private static final DeferredRegister<MapCodec<? extends ICondition>> CONDITIONS =
            DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, OreSpawnIntegrations.MODID);

    static {
        CONDITIONS.register("week_switch", () -> WeekSwitchCondition.CODEC);
    }

    private WeekCore() {
    }

    public static void init(IEventBus modBus) {
        CONDITIONS.register(modBus);
        WeekData.register(modBus);
        GazetteItems.register(modBus);
        LandsWeek.init();
        Almanac.init();
        DuskCry.init(modBus);
        Gazette.init();
        FirstWeek.init();
        CanopyLines.init();
        NeoForge.EVENT_BUS.addListener(ServerLedger::onServerTick);
        NeoForge.EVENT_BUS.addListener(ServerLedger::onServerStopped);
        NeoForge.EVENT_BUS.addListener(WeekCommands::onRegisterCommands);
    }
}
