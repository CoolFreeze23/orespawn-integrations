package danger.orespawn.integrations.npc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Quests 701-706 and dialog 701 re-keyed (npc-iracema): every text field is one whole lang key with an EN and a pt-BR
 * line, the 701 objective names Mobzilla through a key (never orespawn:godzilla), the rewards and chain are the
 * shipped ones, and the installer's "unedited?" test tells a shipped file from an operator's edit.
 */
class QuestRekeyTest {

    private static final Path OLD = Path.of("src/main/resources/orespawn_integrations/customnpcs");
    private static final Path NEW = Path.of("src/main/resources/orespawn_integrations/customnpcs_0101");
    private static final List<String> FILES = List.of("quests/OreSpawn/701.json", "quests/OreSpawn/702.json",
            "quests/OreSpawn/703.json", "quests/Modern Wonders/704.json", "quests/Modern Wonders/705.json",
            "quests/Modern Wonders/706.json", "dialogs/OreSpawn/701.json", "dialogs/Registro/710.json");

    private static String read(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    @Test
    void everyTextFieldIsAKeyWithBothLanguages() throws IOException {
        JsonObject en = JsonParser.parseString(read(Path.of("_lang_fragments/0.10.1/ranks.en_us.json"))).getAsJsonObject();
        JsonObject pt = JsonParser.parseString(read(Path.of("_lang_fragments/0.10.1/ranks.pt_br.json"))).getAsJsonObject();
        JsonObject reg = JsonParser.parseString(read(Path.of("_lang_fragments/0.10.0/registry.en_us.json"))).getAsJsonObject();
        for (String rel : FILES) {
            for (String field : CnpcText.fields(read(NEW.resolve(rel)))) {
                String key = field.substring(field.indexOf('=') + 1);
                assertTrue(key.startsWith("orespawn_integrations."), rel + " " + field);
                assertTrue(en.has(key) && pt.has(key) || reg.has(key), "no lines for " + key);
            }
        }
    }

    @Test
    void killObjectivesNameTheirTargetByKey() throws IOException {
        String q701 = read(NEW.resolve("quests/OreSpawn/701.json"));
        assertFalse(q701.contains("\"orespawn:godzilla\""));
        assertTrue(q701.contains("orespawn_integrations.quest.701.alvo"));
        assertTrue(q701.contains("orespawn:big_bertha"), "the shipped reward");
        assertTrue(read(NEW.resolve("quests/OreSpawn/703.json")).contains("\"NextQuestId\": 704"), "the shipped chain");
    }

    @Test
    void unchangedFilesMatchAndEditsDoNot() throws IOException {
        String original = read(OLD.resolve("quests/OreSpawn/703.json"));
        assertEquals(CnpcText.fields(original), CnpcText.fields(original.replace("\n", "\r\n")));
        String edited = original.replace("Tea with the Queen", "Tea with Her Majesty");
        assertNotEquals(CnpcText.fields(original), CnpcText.fields(edited), "an operator-edited 703 is kept");
        assertNotEquals(CnpcText.fields(original), CnpcText.fields(read(NEW.resolve("quests/OreSpawn/703.json"))));
    }
}
