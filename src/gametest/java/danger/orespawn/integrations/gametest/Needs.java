package danger.orespawn.integrations.gametest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

/**
 * A test that checks another mod's content can only judge a server where that mod is installed. The dev GameTest run
 * carries a smaller set of mods than the pack, so such a test logs one "skipped" line and passes there; on the pack,
 * with every mod present, it runs in full. The skip lines are counted in the run's report.
 */
final class Needs {

    private static final Logger LOGGER = LoggerFactory.getLogger("OreSpawnIntegrationsTests");

    private Needs() {
    }

    /** True when every mod is loaded; otherwise logs the skip, passes the test and returns false. */
    static boolean mods(GameTestHelper helper, String test, String... mods) {
        List<String> missing = Arrays.stream(mods).filter(m -> !ModList.get().isLoaded(m)).toList();
        if (missing.isEmpty()) {
            return true;
        }
        LOGGER.warn("GameTest skipped: {} needs {}", test, missing);
        helper.succeed();
        return false;
    }
}
