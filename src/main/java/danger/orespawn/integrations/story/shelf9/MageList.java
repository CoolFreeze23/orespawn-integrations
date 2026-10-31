package danger.orespawn.integrations.story.shelf9;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The "born with magic" list the Eggshell's Worn Notebook reads ({@code #orespawn_integrations:origin/mage}, the file
 * {@code data/orespawn_integrations/tags/origin/mage.json}). Neo Origins' origins are data files, not a registry, so the
 * game itself never loads this as a tag: the open handler reads the file through the server's resource manager, and a
 * datapack can still replace it the usual way. Pure logic here so it can be unit tested.
 */
public final class MageList {

    /** The eight Mage group origins of system-etiqueta-do-ovo, used when the file can't be read. */
    public static final List<String> DEFAULT = List.of(
            "neoorigins:fire_mage", "neoorigins:water_mage", "neoorigins:earth_mage", "neoorigins:air_mage",
            "neoorigins:gravity_mage", "neoorigins:darkness_mage", "neoorigins:necromancer", "neoorigins:monster_tamer");

    private MageList() {
    }

    /** Parses a tag-shaped file: {@code {"values": ["ns:path", {"id": "ns:path", "required": false}]}}. */
    public static Set<String> parse(String json) {
        Set<String> out = new LinkedHashSet<>();
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray values = root.getAsJsonArray("values");
        if (values == null) {
            return out;
        }
        for (JsonElement value : values) {
            if (value.isJsonPrimitive()) {
                out.add(value.getAsString());
            } else if (value.isJsonObject() && value.getAsJsonObject().has("id")) {
                out.add(value.getAsJsonObject().get("id").getAsString());
            }
        }
        return out;
    }

    /**
     * True when any of the player's picks (full ids) is on the list, or, without Neo Origins data, when any of their
     * entity tags {@code cc_origin.<path>} / {@code cc_class.<path>} (written by the Egg's Label) names a listed path.
     */
    public static boolean isMage(Set<String> list, Collection<String> picks, Collection<String> entityTags) {
        for (String pick : picks) {
            if (list.contains(pick)) {
                return true;
            }
        }
        for (String id : list) {
            int colon = id.indexOf(':');
            String path = colon < 0 ? id : id.substring(colon + 1);
            if (entityTags.contains("cc_origin." + path) || entityTags.contains("cc_class." + path)) {
                return true;
            }
        }
        return false;
    }
}
