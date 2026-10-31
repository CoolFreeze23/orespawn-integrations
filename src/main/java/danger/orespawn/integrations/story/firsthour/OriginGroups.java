package danger.orespawn.integrations.story.firsthour;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code data/orespawn_integrations/origin_groups.json}: Origin Awakening's nine groups (our copy, since OA's own
 * file is client-only) plus Others, and Neo Origins' twenty classes, in score order. Plain Java and Gson, so the
 * label mapping is tested under JUnit. The server reloads it with the datapacks; the jar's copy is the fallback.
 */
public final class OriginGroups {

    public static final int OTHERS = 10;
    public static final String RESOURCE = "/data/orespawn_integrations/origin_groups.json";

    private final Map<String, Integer> groupOf = new HashMap<>();
    private final Map<String, Integer> classIndex = new HashMap<>();
    private final List<String> originOrder = new ArrayList<>();
    private final List<String> groupIds = new ArrayList<>();

    private static volatile OriginGroups current;

    private OriginGroups() {
    }

    public static OriginGroups parse(Reader reader) {
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        OriginGroups g = new OriginGroups();
        for (JsonElement e : root.getAsJsonArray("groups")) {
            JsonObject group = e.getAsJsonObject();
            int score = group.get("score").getAsInt();
            g.groupIds.add(group.get("id").getAsString());
            JsonArray origins = group.has("origins") ? group.getAsJsonArray("origins") : new JsonArray();
            for (JsonElement o : origins) {
                String id = o.getAsString();
                g.groupOf.putIfAbsent(id, score);
                g.originOrder.add(id);
            }
        }
        JsonArray classes = root.getAsJsonArray("classes");
        for (int i = 0; i < classes.size(); i++) {
            g.classIndex.put(classes.get(i).getAsString(), i + 1);
        }
        return g;
    }

    /** The groups the server uses: the datapack's when loaded, else the jar's own copy. */
    public static OriginGroups get() {
        OriginGroups g = current;
        if (g == null) {
            try (InputStream in = OriginGroups.class.getResourceAsStream(RESOURCE)) {
                g = in == null ? new OriginGroups()
                        : parse(new InputStreamReader(in, StandardCharsets.UTF_8));
            } catch (Exception e) {
                g = new OriginGroups();
            }
            current = g;
        }
        return g;
    }

    static void set(OriginGroups groups) {
        current = groups;
    }

    /** {@code cc_ogrupo}: 1-9 by Origin Awakening's groups, 10 for any origin no group lists. */
    public int group(String originId) {
        return groupOf.getOrDefault(originId, OTHERS);
    }

    /** {@code cc_classe}: 1-20 in the class layer's order, 0 for a class not listed. */
    public int classScore(String classId) {
        return classIndex.getOrDefault(classId, 0);
    }

    /** {@code cc_origin_id}: the origin's place in this file's order (1-76), 0 for an unlisted origin. */
    public int originNumber(String originId) {
        return originOrder.indexOf(originId) + 1;
    }

    /** Every listed origin by group id, in order (the diff against Origin Awakening reads this). */
    public Map<String, List<String>> byGroup() {
        Map<String, List<String>> out = new java.util.LinkedHashMap<>();
        for (String id : groupIds) {
            out.put(id, new ArrayList<>());
        }
        for (String origin : originOrder) {
            int score = groupOf.get(origin);
            out.get(groupIds.get(score - 1)).add(origin);
        }
        return Collections.unmodifiableMap(out);
    }
}
