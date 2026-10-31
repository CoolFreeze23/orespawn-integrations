package danger.orespawn.integrations.gametest;

import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.GlobalTestReporter;
import net.minecraft.gametest.framework.TestReporter;
import net.minecraft.gametest.framework.TestCommand;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs our GameTests on a production server. NeoForge switches GameTests off outside a development environment
 * (GameTestHooks.isGametestEnabled is false in production whatever the system properties say), so neither the tests
 * nor the /test command exist on a real pack server. With -Doi.gametests=true this mod registers the holder classes
 * itself, through vanilla's own GameTestRegistry (NeoForge's template hooks still read @GameTestHolder and
 * @PrefixGameTestTemplate), and adds vanilla's /test command. The server ticks the GameTest ticker every tick, so
 * "/test runall" then runs them in the world and broadcasts the result. Without the property the mod does nothing.
 */
@Mod("orespawn_integrations_tests")
public final class GameTestsMod {

    private static final Logger LOGGER = LoggerFactory.getLogger("OreSpawnIntegrationsTests");

    private static final List<Class<?>> HOLDERS = List.of(StoryCoreTests.class, ShelfNineTests.class,
            FirstHourTests.class, RegistryTests.class, WorldTests.class, SightingNameTests.class, ChaosTests.class,
            DataTests.class, RanksTests.class);

    public GameTestsMod(IEventBus modBus) {
        if (!Boolean.getBoolean("oi.gametests")) {
            LOGGER.warn("orespawn_integrations_tests is installed but -Doi.gametests=true is not set: it registers "
                    + "nothing. This jar belongs only on scratch test servers; remove it from a real server or client.");
            return;
        }
        NeoForge.EVENT_BUS.addListener(GameTestsMod::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(GameTestsMod::onServerAboutToStart);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        TestCommand.register(event.getDispatcher());
    }

    private static boolean registered;

    private static void onServerAboutToStart(ServerAboutToStartEvent event) {
        if (registered) {
            return;
        }
        registered = true;
        for (Class<?> holder : HOLDERS) {
            GameTestRegistry.register(holder);
        }
        int total = GameTestRegistry.getAllTestFunctions().size();
        GlobalTestReporter.replaceWith(new LogReporter(total));
        LOGGER.info("Registered {} GameTests from {} classes", total, HOLDERS.size());
    }

    /**
     * Vanilla tells only online players how the tests went. A scratch server has none, so this writes every result to
     * the log and, once all {@code total} tests have finished, the same summary line a client would see.
     */
    private static final class LogReporter implements TestReporter {
        private final int total;
        private int done;
        private final List<String> failedRequired = new ArrayList<>();
        private int failedOptional;

        LogReporter(int total) {
            this.total = total;
        }

        @Override
        public synchronized void onTestFailed(GameTestInfo info) {
            if (info.isRequired()) {
                failedRequired.add(info.getTestName());
            } else {
                failedOptional++;
            }
            LOGGER.error("GameTest FAILED: {} ({}): {}", info.getTestName(), info.isRequired() ? "required" : "optional",
                    info.getError() == null ? "no message" : info.getError().getMessage());
            count();
        }

        @Override
        public synchronized void onTestSuccess(GameTestInfo info) {
            LOGGER.info("GameTest passed: {}", info.getTestName());
            count();
        }

        private void count() {
            if (++done < total) {
                return;
            }
            if (failedRequired.isEmpty()) {
                LOGGER.info("All {} required tests passed :){}", total,
                        failedOptional > 0 ? " (" + failedOptional + " optional failed)" : "");
            } else {
                LOGGER.error("{} required tests failed :( {}", failedRequired.size(), failedRequired);
            }
        }
    }
}
