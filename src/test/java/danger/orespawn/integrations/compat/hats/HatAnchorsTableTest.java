package danger.orespawn.integrations.compat.hats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

/**
 * The shipped hat-anchor table against the OreSpawn jar in {@code libs/}: every rig in the jar has an anchor, and
 * every anchor names bones that exist in its rig. Pure file reading; no Minecraft classes are touched.
 */
class HatAnchorsTableTest {

    private static final String TABLE = "/assets/orespawn_integrations/hats/anchors.json";
    private static final String GEO_PREFIX = "assets/orespawn/geo/entity/";
    private static final String GEO_SUFFIX = ".geo.json";

    @Test
    void everyRigHasAnAnchorAndEveryAnchorNamesRealBones() throws IOException {
        Map<String, Set<String>> rigs = rigBones(newestOreSpawnJar());
        assertTrue(rigs.size() >= 100, "expected the port's rigs in the jar, found " + rigs.size());

        JsonObject table;
        try (InputStream in = HatAnchorsTableTest.class.getResourceAsStream(TABLE)) {
            assertTrue(in != null, "anchor table missing from resources: " + TABLE);
            table = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }

        List<String> problems = new ArrayList<>();
        Set<String> covered = new HashSet<>();
        for (Map.Entry<String, JsonElement> e : table.entrySet()) {
            String id = e.getKey();
            if (!id.startsWith("orespawn:")) {
                problems.add(id + ": not an orespawn entity id");
                continue;
            }
            JsonObject o = e.getValue().getAsJsonObject();
            String geo = o.has("geo") ? o.get("geo").getAsString() : null;
            if (geo == null || !rigs.containsKey(geo)) {
                problems.add(id + ": geo '" + geo + "' is not in the jar");
                continue;
            }
            covered.add(geo);
            boolean none = o.has("mode") && "none".equalsIgnoreCase(o.get("mode").getAsString());
            if (none) {
                continue;
            }
            Set<String> bones = rigs.get(geo);
            String bone = o.has("bone") ? o.get("bone").getAsString() : null;
            if (bone == null || !bones.contains(bone)) {
                problems.add(id + ": bone '" + bone + "' is not in " + geo);
            }
            if (o.has("also")) {
                JsonArray also = o.getAsJsonArray("also");
                for (JsonElement a : also) {
                    if (!bones.contains(a.getAsString())) {
                        problems.add(id + ": also-bone '" + a.getAsString() + "' is not in " + geo);
                    }
                }
            }
            if (o.has("offset") && o.getAsJsonArray("offset").size() != 3) {
                problems.add(id + ": offset must be [x, y, z]");
            }
        }
        Set<String> uncovered = new TreeSet<>(rigs.keySet());
        uncovered.removeAll(covered);
        if (!uncovered.isEmpty()) {
            problems.add("rigs without an anchor: " + uncovered);
        }
        assertEquals(List.of(), problems);
    }

    private static Path newestOreSpawnJar() throws IOException {
        Path libs = Path.of("libs");
        try (Stream<Path> files = Files.list(libs)) {
            return files.filter(p -> p.getFileName().toString().matches("orespawn-.*\\.jar"))
                    .max(Comparator.comparing(p -> p.getFileName().toString()))
                    .orElseThrow(() -> new IOException("no orespawn jar in " + libs.toAbsolutePath()));
        }
    }

    /** Rig name to the set of bone names, from the geo files in the jar. */
    private static Map<String, Set<String>> rigBones(Path jar) throws IOException {
        Map<String, Set<String>> rigs = new HashMap<>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            for (ZipEntry entry : zip.stream().toList()) {
                String name = entry.getName();
                if (!name.startsWith(GEO_PREFIX) || !name.endsWith(GEO_SUFFIX)) {
                    continue;
                }
                String rig = name.substring(GEO_PREFIX.length(), name.length() - GEO_SUFFIX.length());
                try (InputStream in = zip.getInputStream(entry)) {
                    JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                    JsonObject geometry = root.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
                    Set<String> bones = new HashSet<>();
                    if (geometry.has("bones")) {
                        for (JsonElement b : geometry.getAsJsonArray("bones")) {
                            bones.add(b.getAsJsonObject().get("name").getAsString());
                        }
                    }
                    rigs.put(rig, bones);
                }
            }
        }
        return rigs;
    }
}
