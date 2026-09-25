package danger.orespawn.integrations.compat.mhv;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

/**
 * The Monster Hunter quarry tag against the OreSpawn jar in {@code libs/}: every entry is an OreSpawn creature the
 * jar actually ships. One wrong id would make the game drop the whole tag, so the hunters would hunt nothing extra.
 * Pure file reading; no Minecraft classes are touched.
 */
class QuarryTagTest {

    private static final String TAG = "/data/monster_hunter_villager/tags/entity_type/quarry.json";
    private static final String LANG = "assets/orespawn/lang/en_us.json";

    @Test
    void everyQuarryEntryIsAnOreSpawnCreature() throws IOException {
        JsonObject lang = oreSpawnLang(newestOreSpawnJar());

        JsonObject tag;
        try (InputStream in = QuarryTagTest.class.getResourceAsStream(TAG)) {
            assertTrue(in != null, "quarry tag missing from resources: " + TAG);
            tag = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        assertTrue(!tag.has("replace") || !tag.get("replace").getAsBoolean(), "the tag must add to Monster Hunter Villager's, not replace it");

        List<String> problems = new ArrayList<>();
        int entries = 0;
        for (JsonElement value : tag.getAsJsonArray("values")) {
            entries++;
            String id = value.getAsString();
            if (!id.startsWith("orespawn:")) {
                problems.add(id + ": not an orespawn entity id");
            } else if (!lang.has("entity.orespawn." + id.substring("orespawn:".length()))) {
                problems.add(id + ": no such creature in the OreSpawn jar");
            }
        }
        assertTrue(entries > 0, "the quarry tag is empty");
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    private static JsonObject oreSpawnLang(Path jar) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry(LANG);
            assertTrue(entry != null, LANG + " missing from " + jar);
            try (InputStream in = zip.getInputStream(entry)) {
                return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            }
        }
    }

    private static Path newestOreSpawnJar() throws IOException {
        Path libs = Path.of("libs");
        try (Stream<Path> files = Files.list(libs)) {
            return files.filter(p -> p.getFileName().toString().matches("orespawn-.*\\.jar"))
                    .max(Comparator.comparing(p -> p.getFileName().toString()))
                    .orElseThrow(() -> new IOException("no orespawn jar in " + libs.toAbsolutePath()));
        }
    }
}
