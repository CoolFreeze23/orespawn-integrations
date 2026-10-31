package danger.orespawn.integrations.story.court;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Court ranks' pure rules and the shape of their data (system-court-ranks test lines that need no server). */
class RankRulesTest {

    private static final Path DATA = Path.of("src/main/resources/data/orespawn_integrations");

    /** Utopia then the Nether on the next Land's day makes a Squire; both on one day waits. */
    @Test
    void oneStampADay() {
        assertEquals(RankRules.Stamp.FIRST, RankRules.stamp(null, 4));
        assertEquals(RankRules.Stamp.SAME_DAY, RankRules.stamp(4, 4));
        assertEquals(RankRules.Stamp.SQUIRE, RankRules.stamp(4, 5));
    }

    /** A player who stayed in the Nether across dawn completes at the next login or entry; not on the same day. */
    @Test
    void waitingCompletesOnALaterDay() {
        assertTrue(RankRules.waitingCompletes(true, 4, 5, true));
        assertFalse(RankRules.waitingCompletes(true, 4, 4, true));
        assertFalse(RankRules.waitingCompletes(true, 4, 5, false));
        assertFalse(RankRules.waitingCompletes(false, 4, 5, true));
    }

    /** Never by Chaos, the End or the overworld; the nine provinces are the card's list. */
    @Test
    void provinces() {
        assertNull(RankRules.province("orespawn:chaos"));
        assertNull(RankRules.province("minecraft:the_end"));
        assertNull(RankRules.province("minecraft:overworld"));
        assertEquals(9, RankRules.PROVINCES.size());
        assertEquals("twilight_forest", RankRules.province("twilightforest:twilight_forest"));
        assertEquals(7, RankRules.ENTRY_ADVANCEMENTS.size());
        assertTrue(RankRules.PROVINCES.values().containsAll(RankRules.ENTRY_ADVANCEMENTS.values()));
    }

    /** A rank-0 player given a Fidalgo deed gets Squire, Knight and Fidalgo in order, in one ceremony. */
    @Test
    void skippedRanksInOrder() {
        assertArrayEquals(new int[]{1, 2, 3}, RankRules.steps(0, 3));
        assertArrayEquals(new int[]{0, 1}, RankRules.steps(-1, 1));
        assertArrayEquals(new int[0], RankRules.steps(2, 2));
        assertArrayEquals(new int[0], RankRules.steps(3, 1));
        assertTrue(RankRules.skips(0, 3));
        assertFalse(RankRules.skips(-1, 1));
        assertFalse(RankRules.skips(1, 2));
    }

    /** The card per rank is level rank + 1, the Fidalgo's level 4. */
    @Test
    void keycards() {
        assertEquals(1, RankRules.keycardLevel(RankRules.HATCHLING));
        assertEquals(4, RankRules.keycardLevel(RankRules.FIDALGO));
        assertEquals(5, RankRules.keycardLevel(RankRules.REGENT));
        assertEquals(1, RankRules.rankOfTier("apotheosis:progression/frontier"));
        assertEquals(3, RankRules.rankOfTier("apotheosis:progression/summit"));
        assertEquals(-1, RankRules.rankOfTier("apotheosis:progression/haven"));
    }

    /** Every province stamp is hidden, has a display (so the earn hook fires) and shows no toast. */
    @Test
    void provinceAdvancementsHaveNoToast() throws IOException {
        for (String path : RankRules.PROVINCES.values()) {
            Path file = DATA.resolve("advancement/court/provincia/" + path + ".json");
            JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject display = json.getAsJsonObject("display");
            assertTrue(display.get("hidden").getAsBoolean(), path + " hidden");
            assertFalse(display.get("show_toast").getAsBoolean(), path + " toast");
            assertEquals("minecraft:changed_dimension",
                    json.getAsJsonObject("criteria").getAsJsonObject("entered").get("trigger").getAsString());
            assertTrue(Files.exists(DATA.resolve("function/court/provincia/" + path + ".mcfunction")));
        }
    }
}
