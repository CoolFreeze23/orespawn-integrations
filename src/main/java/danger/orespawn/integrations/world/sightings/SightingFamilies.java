package danger.orespawn.integrations.world.sightings;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The sighting families, read once from {@code data/orespawn_integrations/sightings/families.json} in our jar (written
 * by {@code tools/world/world_data.py} from system-sightings' table, every id checked against the pack's registry).
 * Kinds: {@code structure} (structure ids), {@code entity} (entity type ids), {@code block} (block ids, stamped),
 * {@code hub} (a {@code hub_places} thing, or Shelf 9), {@code special} (the door cloud, the Hoard Fall, the red
 * islands, the three butterflies: matched in code) and {@code none} (no target ships yet: the page stays locked).
 * Gson only, so the table is unit tested without a game.
 */
public final class SightingFamilies {

    public static final String RESOURCE = "/data/orespawn_integrations/sightings/families.json";

    public record Family(int number, String slug, String kind, List<String> targets) {}

    public static final int DOOR_CLOUD = 37;
    public static final int HOARD_FALL = 38;
    public static final int RED_ISLANDS = 41;
    public static final int BAD_EGG = 43;
    public static final int OIL_SPILL = 46;
    public static final int THREE_BUTTERFLIES = 49;

    private static SightingFamilies instance;

    private final List<Family> all;
    private final Map<Integer, Family> byNumber = new HashMap<>();
    private final Map<String, Family> byStructure = new HashMap<>();
    private final Map<String, Family> byEntity = new HashMap<>();
    private final Map<String, Family> byBlock = new HashMap<>();
    private final Map<String, Family> byHubThing = new HashMap<>();

    private SightingFamilies(List<Family> families) {
        all = Collections.unmodifiableList(families);
        for (Family f : families) {
            byNumber.put(f.number(), f);
            Map<String, Family> index = switch (f.kind()) {
                case "structure" -> byStructure;
                case "entity" -> byEntity;
                case "block" -> byBlock;
                case "hub" -> byHubThing;
                default -> null;
            };
            if (index != null) {
                f.targets().forEach(t -> index.put(t, f));
            }
        }
    }

    public static synchronized SightingFamilies get() {
        if (instance == null) {
            try (InputStream in = SightingFamilies.class.getResourceAsStream(RESOURCE)) {
                if (in == null) {
                    throw new IllegalStateException("missing " + RESOURCE);
                }
                instance = parse(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                        .getAsJsonObject());
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
        }
        return instance;
    }

    static SightingFamilies parse(JsonObject root) {
        List<Family> families = new ArrayList<>();
        for (JsonElement e : root.getAsJsonArray("families")) {
            JsonObject o = e.getAsJsonObject();
            List<String> targets = new ArrayList<>();
            JsonArray array = o.getAsJsonArray("targets");
            array.forEach(t -> targets.add(t.getAsString()));
            families.add(new Family(o.get("n").getAsInt(), o.get("slug").getAsString(), o.get("kind").getAsString(),
                    List.copyOf(targets)));
        }
        return new SightingFamilies(families);
    }

    public List<Family> all() {
        return all;
    }

    public Family number(int n) {
        return byNumber.get(n);
    }

    public Family structure(String id) {
        return byStructure.get(id);
    }

    public Family entity(String id) {
        return byEntity.get(id);
    }

    public Family block(String id) {
        return byBlock.get(id);
    }

    public Family hubThing(String thing) {
        return byHubThing.get(thing);
    }
}
