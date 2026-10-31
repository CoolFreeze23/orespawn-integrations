package danger.orespawn.integrations.world.sightings;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.locale.Language;
import net.minecraft.server.level.ServerPlayer;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Map pins are literal text (JourneyMap's command takes a plain name), so the server writes each one in the player's
 * own language: it reads our jar's {@code assets/orespawn_integrations/lang/<lang>.json} once per language, falling
 * back to English, then to the server's own language table (other mods' names, in English), then to the key.
 */
public final class SightingNames {

    private static final Map<String, Map<String, String>> TABLES = new HashMap<>();

    private SightingNames() {
    }

    public static String resolve(ServerPlayer player, String key) {
        String lang = player.clientInformation().language();
        String text = table(lang == null ? "en_us" : lang.toLowerCase(Locale.ROOT)).get(key);
        if (text == null) {
            text = table("en_us").get(key);
        }
        if (text == null) {
            text = Language.getInstance().getOrDefault(key, key);
        }
        return text;
    }

    private static synchronized Map<String, String> table(String lang) {
        return TABLES.computeIfAbsent(lang, SightingNames::load);
    }

    private static Map<String, String> load(String lang) {
        Map<String, String> out = new HashMap<>();
        String path = "/assets/orespawn_integrations/lang/" + lang + ".json";
        try (InputStream in = SightingNames.class.getResourceAsStream(path)) {
            if (in != null) {
                JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                    if (e.getValue().isJsonPrimitive()) {
                        out.put(e.getKey(), e.getValue().getAsString());
                    }
                }
            }
        } catch (Exception ignored) {
            // a missing or broken table falls back to English and then to the key
        }
        return out;
    }
}
