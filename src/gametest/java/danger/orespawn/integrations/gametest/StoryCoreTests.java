package danger.orespawn.integrations.gametest;

import danger.orespawn.integrations.config.IntegrationsConfig;
import danger.orespawn.integrations.story.StoryScheduler;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The shared plumbing every road system rides. Template {@code orespawn_integrations_tests:empty8} is an empty 8x8x8
 * box; each test builds what it needs inside it.
 */
@GameTestHolder("orespawn_integrations_tests")
@PrefixGameTestTemplate(false)
public final class StoryCoreTests {

    private StoryCoreTests() {
    }

    /** The scheduler runs a task after its delay, on the server thread. */
    @GameTest(template = "empty8")
    public static void schedulerRunsDelayedTask(GameTestHelper helper) {
        boolean[] ran = {false};
        StoryScheduler.schedule(helper.getLevel().getServer(), 5, "test_scheduler", () -> ran[0] = true);
        helper.runAfterDelay(4, () -> {
            if (ran[0]) {
                helper.fail("task ran before its delay");
            }
        });
        helper.succeedWhen(() -> helper.assertTrue(ran[0], "task never ran"));
    }

    /** The First Hour's switches load with the defaults the roadmap lists. */
    @GameTest(template = "empty8")
    public static void firstHourDefaults(GameTestHelper helper) {
        helper.assertTrue(IntegrationsConfig.storyHatchingGround.getDefault(), "story.hatching_ground default");
        helper.assertTrue(!IntegrationsConfig.storyGuideOnJoin.getDefault(), "story.guide_on_join default");
        helper.assertTrue(!IntegrationsConfig.aliveMeteorGrief.getDefault(), "alive.meteor_grief default");
        helper.assertTrue(IntegrationsConfig.sightingsMinDistance.getDefault() == 32, "sightings.min_distance default");
        helper.assertTrue(IntegrationsConfig.sightingsScoutDistance.getDefault() == 24, "sightings.scout_distance default");
        helper.assertTrue(IntegrationsConfig.sightingsAutoPins.getDefault() == 64, "sightings.auto_pins default");
        helper.succeed();
    }
}
