package danger.orespawn.integrations.story.week;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/** The Gazette's page budget and Saturday truco's timing, as pure logic; the card lines' word counts from the fragments. */
class GazetteTrucoTest {

    private static final Path FRAGMENTS = Path.of("_lang_fragments", "0.10.1");

    @Test
    void mandatorySlotsAlwaysPrintAndTheRestWait() {
        List<GazetteLayout.Slot<String>> slots = List.of(
                new GazetteLayout.Slot<>(10, true, "masthead"),
                new GazetteLayout.Slot<>(60, false, "a"),
                new GazetteLayout.Slot<>(60, false, "b"),
                new GazetteLayout.Slot<>(40, false, "c"));
        GazetteLayout.Fill<String> fill = GazetteLayout.fill(slots, GazetteLayout.PAGE_WORDS);
        assertEquals(List.of("masthead", "a", "c"), fill.kept());
        assertEquals(List.of("b"), fill.held());
        assertTrue(fill.words() <= GazetteLayout.PAGE_WORDS);
    }

    @Test
    void wordsAndYear() {
        assertEquals(0, GazetteLayout.words("  "));
        assertEquals(4, GazetteLayout.words("Nothing misfiled.  Feijoada. Hoje"));
        assertEquals(77, GazetteLayout.year(0));
        assertEquals(78, GazetteLayout.year(96));
    }

    @Test
    void missedReadingBelongsToTheLastSaturday() {
        long saturday = 5; // land_day 5 is a Saturday (day 0 Monday)
        assertTrue(TrucoRules.saturday(saturday));
        assertEquals(saturday, TrucoRules.lastReading(saturday, 13000), "Saturday after dusk");
        assertEquals(saturday - 7, TrucoRules.lastReading(saturday, 6000), "Saturday before dusk: last week's");
        assertEquals(saturday, TrucoRules.lastReading(saturday + 2, 6000), "Monday after");
        assertEquals(saturday, TrucoRules.lastReading(saturday + 6, 18000), "the Friday after");
    }

    @Test
    void headlineOrder() {
        assertTrue(TrucoRules.priority("orespawn_integrations.deed.boss_kill") < TrucoRules.priority("x.coronation"));
        assertTrue(TrucoRules.priority("x.coronation") < TrucoRules.priority("x.atlas"));
        assertEquals(4, TrucoRules.priority("orespawn_integrations.deed.canopy_hat"));
    }

    /** The Newcomer's Edition with the first Saturday's box stays under 120 words a language. */
    @Test
    void newcomerSaturdayPageFitsInBothLanguages() throws Exception {
        for (String lang : List.of("en_us", "pt_br")) {
            JsonObject values = JsonParser.parseString(Files.readString(FRAGMENTS.resolve("week." + lang + ".json"),
                    StandardCharsets.UTF_8)).getAsJsonObject();
            int words = 0;
            for (String k : List.of("masthead", "newcomer.5", "purses.title", "purses.scraps", "purses.reais",
                    "purses.emeralds", "purses.emc")) {
                words += GazetteLayout.words(values.get("orespawn_integrations.gazette." + k).getAsString());
            }
            assertTrue(words <= GazetteLayout.PAGE_WORDS, lang + " " + words);
            assertTrue(values.get("orespawn_integrations.gazette.newcomer.6").getAsString().contains("Jade"),
                    "Sunday's fixture names the Jade marker");
        }
    }

    /** Every weekday has its sunrise line and its newcomer fixture in both languages. */
    @Test
    void sevenDaysInBothLanguages() throws Exception {
        for (String lang : List.of("en_us", "pt_br")) {
            Map<?, ?> values = new com.google.gson.Gson().fromJson(Files.readString(
                    FRAGMENTS.resolve("week." + lang + ".json"), StandardCharsets.UTF_8), Map.class);
            for (int d = 0; d < 7; d++) {
                assertTrue(values.containsKey("week.orespawn_integrations." + d), lang + " weekday " + d);
                assertTrue(values.containsKey("orespawn_integrations.gazette.newcomer." + d), lang + " fixture " + d);
            }
            assertFalse(values.containsKey("week.orespawn_integrations.7"));
        }
    }
}
