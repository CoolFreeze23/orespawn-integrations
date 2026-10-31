package danger.orespawn.integrations.story.firsthour;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

/**
 * The Egg's Label groups against Origin Awakening's {@code categories.json}. That file lives in OA's client assets,
 * so the server reads our copy, {@code origin_groups.json}; this test fails the build when the two differ by a single
 * origin. It always compares against the checked-in reference ({@code src/test/resources/reference/originawakening/
 * categories.json}, copied from OA 1.0.0) and, when the pack's OA jar is on this machine (or named by
 * {@code -Doa.jar=<path>}), against the jar itself too, so a newer OA can't drift past the reference unseen.
 */
class OriginGroupsTest {

    private static final String OA_ENTRY = "assets/originawakening/awakening/categories.json";
    private static final Path DEFAULT_OA_MODS = Path.of(System.getProperty("user.home"),
            "AppData", "Roaming", "PrismLauncher", "instances", "CrazyCraft 5.0", "minecraft", "mods");

    private static OriginGroups ours() throws IOException {
        try (InputStream in = OriginGroupsTest.class.getResourceAsStream(OriginGroups.RESOURCE)) {
            assertTrue(in != null, "origin_groups.json missing");
            return OriginGroups.parse(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    private static Map<String, List<String>> categories(Reader reader) {
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (JsonElement e : root.getAsJsonArray("categories")) {
            JsonObject c = e.getAsJsonObject();
            List<String> origins = new ArrayList<>();
            c.getAsJsonArray("origins").forEach(o -> origins.add(o.getAsString()));
            out.put(c.get("id").getAsString(), origins);
        }
        return out;
    }

    private static void assertSameGroups(Map<String, List<String>> oa, String source) throws IOException {
        Map<String, List<String>> mine = new LinkedHashMap<>(ours().byGroup());
        List<String> others = mine.remove("others");
        assertTrue(others == null || others.isEmpty(), "our Others group must list nothing");
        assertEquals(new ArrayList<>(oa.keySet()), new ArrayList<>(mine.keySet()),
                "group order differs from " + source);
        for (Map.Entry<String, List<String>> e : oa.entrySet()) {
            assertEquals(e.getValue(), mine.get(e.getKey()),
                    "group '" + e.getKey() + "' differs from " + source + "; update origin_groups.json");
        }
    }

    @Test
    void matchesTheCheckedInReference() throws IOException {
        try (InputStream in = OriginGroupsTest.class.getResourceAsStream(
                "/reference/originawakening/categories.json")) {
            assertTrue(in != null, "reference categories.json missing");
            assertSameGroups(categories(new InputStreamReader(in, StandardCharsets.UTF_8)), "the reference copy");
        }
    }

    @Test
    void matchesOriginAwakeningsJarWhenPresent() throws IOException {
        Path jar = findOaJar();
        if (jar == null) {
            return; // no OA jar on this machine: the reference copy above is the check
        }
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry(OA_ENTRY);
            assertTrue(entry != null, jar + " has no " + OA_ENTRY);
            try (InputStream in = zip.getInputStream(entry)) {
                assertSameGroups(categories(new InputStreamReader(in, StandardCharsets.UTF_8)), jar.toString());
            }
        }
    }

    private static Path findOaJar() throws IOException {
        String named = System.getProperty("oa.jar");
        if (named != null && Files.isRegularFile(Path.of(named))) {
            return Path.of(named);
        }
        if (!Files.isDirectory(DEFAULT_OA_MODS)) {
            return null;
        }
        try (var files = Files.list(DEFAULT_OA_MODS)) {
            return files.filter(p -> p.getFileName().toString().startsWith("originawakening-")
                    && p.toString().endsWith(".jar")).sorted().reduce((a, b) -> b).orElse(null);
        }
    }

    @Test
    void labelMapping() throws IOException {
        OriginGroups g = ours();
        assertEquals(1, g.group("neoorigins:human"));
        assertEquals(2, g.group("orespawn_integrations:kraken_spawn"));
        assertEquals(3, g.group("orespawn_integrations:saci"));
        assertEquals(4, g.group("neoorigins:sylvan"));
        assertEquals(5, g.group("neoorigins:golem"), "an Orb re-pick from sylvan to golem sets cc_ogrupo 5");
        assertEquals(6, g.group("neoorigins:sea_dragon"));
        assertEquals(7, g.group("neoorigins:vampire"));
        assertEquals(8, g.group("neoorigins:gravity_mage"));
        assertEquals(9, g.group("neoorigins:asura"));
        assertEquals(10, g.group("somemod:added_later"));
        assertEquals(1, g.classScore("neoorigins:class_warrior"));
        assertEquals(15, g.classScore("neoorigins:class_merchant"));
        assertEquals(16, g.classScore("neoorigins:class_cleric"));
        assertEquals(20, g.classScore("neoorigins:class_paladin"));
        assertEquals(0, g.classScore("neoorigins:class_unknown"));
        int total = 0;
        for (List<String> origins : g.byGroup().values()) {
            total += origins.size();
        }
        assertEquals(76, total, "all 76 origins have a group");
    }
}
