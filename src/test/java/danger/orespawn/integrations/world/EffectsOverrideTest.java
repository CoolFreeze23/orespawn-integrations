package danger.orespawn.integrations.world;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Our six OreSpawn biome overrides are the port's own biome files with only our effects laid over them
 * (tools/biomes/gen_biome_overrides.py). A port update that changes a biome's spawns, features or carvers fails this
 * test until the overrides are regenerated, so our copies can never hide the port's changes.
 */
class EffectsOverrideTest {

    private static final Path BIOMES = Path.of("src/main/resources/data/orespawn/worldgen/biome");

    @Test
    void overridesAreThePortsBiomesWithOurEffects() throws IOException {
        Path jar;
        try (var libs = Files.list(Path.of("libs"))) {
            jar = libs.filter(p -> p.getFileName().toString().matches("orespawn-1\\.21\\.1-.*\\.jar")).sorted()
                    .reduce((a, b) -> b).orElse(null);
        }
        Assumptions.assumeTrue(jar != null, "no OreSpawn jar in libs/");
        JsonObject effects = JsonParser.parseString(Files.readString(Path.of("tools/biomes/effects.json")))
                .getAsJsonObject().getAsJsonObject("effects");
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            for (Map.Entry<String, JsonElement> biome : effects.entrySet()) {
                ZipEntry entry = zip.getEntry("data/orespawn/worldgen/biome/" + biome.getKey() + ".json");
                assertNotNull(entry, "the port has no biome " + biome.getKey());
                JsonObject expected;
                try (var in = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
                    expected = JsonParser.parseReader(in).getAsJsonObject();
                }
                JsonObject merged = expected.has("effects") ? expected.getAsJsonObject("effects") : new JsonObject();
                biome.getValue().getAsJsonObject().entrySet().forEach(e -> merged.add(e.getKey(), e.getValue()));
                expected.add("effects", merged);
                JsonElement shipped = JsonParser.parseString(Files.readString(BIOMES.resolve(biome.getKey() + ".json")));
                assertEquals(expected, shipped, biome.getKey() + " differs from the port's biome plus our effects; "
                        + "run tools/biomes/gen_biome_overrides.py");
            }
        }
    }
}
