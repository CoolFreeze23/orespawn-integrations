package danger.orespawn.integrations.story.registry;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The registry group's pure checks: the scrap caps' arithmetic, and the static checks on its data (every JSON parses,
 * the counts the roadmap fixes, every translate key it uses has an EN and a pt-BR line in the fragments).
 */
class RegistryDataTest {

    private static final Path DATA = Path.of("src/main/resources/data/orespawn_integrations");
    private static final Path DIALOGS = Path.of("src/main/resources/orespawn_integrations/customnpcs/dialogs/Registro");
    private static final Path FRAGMENTS = Path.of("_lang_fragments/0.10.0");

    @Test
    void kiteCapStopsAtTen() {
        int paid = 0;
        for (int i = 0; i < 12; i++) {
            paid += ScrapCapRules.allowed(paid, ScrapCapRules.KITE_CAP, 1);
        }
        assertEquals(10, paid);
    }

    @Test
    void notebookPaysOnePerFiveStampsUpToEight() {
        assertEquals(0, ScrapCapRules.notebookDue(4, 0));
        assertEquals(1, ScrapCapRules.notebookDue(5, 0));
        assertEquals(2, ScrapCapRules.notebookDue(14, 0));
        assertEquals(8, ScrapCapRules.notebookDue(100, 0));
        assertEquals(1, ScrapCapRules.notebookDue(100, 7));
        assertEquals(0, ScrapCapRules.notebookDue(100, 8));
        assertEquals(0, ScrapCapRules.allowed(12, 10, 3));
        assertEquals(0, ScrapCapRules.allowed(0, 10, -1));
    }

    private static List<Path> files(Path root, String suffix) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(p -> p.toString().endsWith(suffix)).toList();
        }
    }

    @Test
    void registryJsonParsesAndCountsMatch() throws IOException {
        for (String dir : List.of("recipe/correction", "lithostitched/worldgen_modifier/cartorio", "advancement/correcoes",
                "worldgen/template_pool/registro", "tags/function/odete")) {
            for (Path p : files(DATA.resolve(dir), ".json")) {
                JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8));
            }
        }
        assertEquals(22, files(DATA.resolve("recipe/correction"), ".json").size());
        assertEquals(21, files(DATA.resolve("lithostitched/worldgen_modifier/cartorio"), ".json").size());
        for (Path p : files(DATA.resolve("recipe/correction"), ".json")) {
            JsonObject recipe = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
            assertEquals("minecraft:crafting_shapeless", recipe.get("type").getAsString(), p.toString());
            var ingredients = recipe.getAsJsonArray("ingredients");
            assertEquals("orespawn_integrations:correction_payment", ingredients.get(0).getAsJsonObject().get("tag").getAsString());
            assertEquals(ingredients.size() - 1, recipe.getAsJsonObject("result").get("count").getAsInt(), p.toString());
        }
    }

    /** True when a release after 0.10.0 defines {@code key} in {@code lang}. */
    private static boolean inLaterRelease(String key, String lang) throws IOException {
        try (Stream<Path> dirs = Files.list(FRAGMENTS.getParent())) {
            for (Path dir : dirs.filter(d -> d.getFileName().toString().startsWith("0.")
                    && !d.equals(FRAGMENTS)).toList()) {
                try (Stream<Path> files = Files.list(dir)) {
                    for (Path f : files.filter(x -> x.getFileName().toString().endsWith("." + lang + ".json")).toList()) {
                        if (JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject().has(key)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    @Test
    void everyKeyHasBothLanguages() throws IOException {
        JsonObject en = JsonParser.parseString(Files.readString(FRAGMENTS.resolve("registry.en_us.json"))).getAsJsonObject();
        JsonObject pt = JsonParser.parseString(Files.readString(FRAGMENTS.resolve("registry.pt_br.json"))).getAsJsonObject();
        // A later release's group may add its own ops function beside the Registry's; its keys are in its fragments.
        for (Path later : files(FRAGMENTS.getParent(), ".json")) {
            String name = later.getFileName().toString();
            if (!later.getParent().equals(FRAGMENTS) && name.endsWith(".en_us.json")) {
                JsonParser.parseString(Files.readString(later)).getAsJsonObject().entrySet().forEach(e -> en.add(e.getKey(), e.getValue()));
            } else if (!later.getParent().equals(FRAGMENTS) && name.endsWith(".pt_br.json")) {
                JsonParser.parseString(Files.readString(later)).getAsJsonObject().entrySet().forEach(e -> pt.add(e.getKey(), e.getValue()));
            }
        }
        Pattern key = Pattern.compile("\"(?:translate|Title|DialogText|DialogTitle)\"[ ]*:[ ]*\"((?:orespawn_integrations|advancements[.]orespawn_integrations)[.][a-z0-9_.]+)\"");
        int found = 0;
        for (Path root : List.of(DATA.resolve("function/odete"), DATA.resolve("function/odete_campo"), DATA.resolve("function/ops"),
                DATA.resolve("advancement/correcoes"), DIALOGS)) {
            for (Path p : files(root, "")) {
                if (Files.isDirectory(p)) {
                    continue;
                }
                Matcher m = key.matcher(Files.readString(p, StandardCharsets.UTF_8));
                while (m.find()) {
                    found++;
                    // A later release's lines (a boteco's ops function, the Gazette's desk page) live in its own
                    // release folder; the Registry's own lines must be in the Registry's fragments.
                    boolean registry = en.has(m.group(1));
                    assertTrue(registry || inLaterRelease(m.group(1), "en_us"), "no EN line for " + m.group(1) + " in " + p);
                    assertTrue(registry ? pt.has(m.group(1)) : inLaterRelease(m.group(1), "pt_br"),
                            "no pt-BR line for " + m.group(1) + " in " + p);
                }
            }
        }
        assertTrue(found > 30, "too few keys found: " + found);
        for (var entry : en.entrySet()) {
            JsonElement value = entry.getValue();
            assertTrue(!value.getAsString().isBlank(), entry.getKey());
        }
    }
}
