package danger.orespawn.integrations.compat.hats;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import danger.orespawn.integrations.OreSpawnIntegrations;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.neoforged.fml.loading.FMLPaths;

/**
 * The head anchors: one entry per OreSpawn species, keyed by entity id.
 *
 * <pre>
 * "orespawn:alien":     {"geo": "alien", "bone": "head"}
 * "orespawn:mantis":    {"geo": "mantis", "bone": "head1", "also": ["head2"]}
 * "orespawn:boyfriend": {"geo": "boyfriend", "bone": "head", "offset": [0, 0.5, 0]}
 * "orespawn:coin":      {"geo": "coin", "mode": "none"}
 * </pre>
 *
 * <ul>
 *   <li>{@code bone}: the GeckoLib bone the hat rides on. The layer measures its cubes at render time, so the anchor
 *       is exact for the rig that ships and needs no coordinates here.</li>
 *   <li>{@code also}: further bones whose cubes count as part of the head when the top-centre and the width are
 *       measured (a head split into halves, a ridge on top of the skull). The hat still follows {@code bone}.</li>
 *   <li>{@code mode}: {@code top} (default) or {@code none} (the species wears no hat; the mod's own fallback is
 *       silenced too).</li>
 *   <li>{@code offset}: [x, y, z] in pixels, applied in the upright frame after the anchor (y up; a built-in hat or
 *       flame layer the hat should clear).</li>
 *   <li>{@code minScale} / {@code maxScale}: clamps on the head-width scale (hat width = head width, a player head
 *       being 8 px = scale 1; default 0.4 to 6).</li>
 *   <li>{@code geo}: the rig's geo name, for the table's own test; the layer does not read it.</li>
 * </ul>
 *
 * Loaded once from the mod jar; {@code config/orespawn_integrations/hat_anchors.json}, if present, overrides entries
 * by id (same shape), so a pack can retarget a species without a rebuild.
 */
public final class HatAnchors {

    public enum Mode { TOP, NONE }

    public record Anchor(String geo, @Nullable String bone, List<String> also, Mode mode,
                         float offX, float offY, float offZ, float minScale, float maxScale) {
        public boolean none() {
            return mode == Mode.NONE || bone == null;
        }
    }

    private static final String BUILT_IN = "/assets/orespawn_integrations/hats/anchors.json";
    private static final Map<ResourceLocation, Anchor> ANCHORS = new HashMap<>();
    /** Render-thread cache by entity type (identity), so the per-bone lookups skip the registry key. */
    private static final Map<EntityType<?>, Optional<Anchor>> BY_TYPE = new IdentityHashMap<>();
    private static volatile boolean loaded;

    private HatAnchors() {
    }

    @Nullable
    public static Anchor get(EntityType<?> type) {
        if (!loaded) {
            load();
        }
        return BY_TYPE.computeIfAbsent(type, t -> Optional.ofNullable(ANCHORS.get(EntityType.getKey(t)))).orElse(null);
    }

    public static Map<ResourceLocation, Anchor> all() {
        if (!loaded) {
            load();
        }
        return Map.copyOf(ANCHORS);
    }

    public static synchronized void load() {
        if (loaded) {
            return;
        }
        ANCHORS.clear();
        BY_TYPE.clear();
        try (InputStream in = HatAnchors.class.getResourceAsStream(BUILT_IN)) {
            if (in == null) {
                OreSpawnIntegrations.LOGGER.error("Hats compat: built-in anchor table {} is missing from the jar", BUILT_IN);
            } else {
                read(new InputStreamReader(in, StandardCharsets.UTF_8), "built-in");
            }
        } catch (Exception e) {
            OreSpawnIntegrations.LOGGER.error("Hats compat: could not read the built-in anchor table", e);
        }
        Path override = FMLPaths.CONFIGDIR.get().resolve("orespawn_integrations").resolve("hat_anchors.json");
        if (Files.isRegularFile(override)) {
            try (Reader r = Files.newBufferedReader(override, StandardCharsets.UTF_8)) {
                int n = read(r, "config");
                OreSpawnIntegrations.LOGGER.info("Hats compat: {} anchor override(s) from {}", n, override);
            } catch (Exception e) {
                OreSpawnIntegrations.LOGGER.error("Hats compat: could not read {}", override, e);
            }
        }
        loaded = true;
        OreSpawnIntegrations.LOGGER.info("Hats compat: {} head anchors loaded", ANCHORS.size());
    }

    private static int read(Reader reader, String source) {
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        int n = 0;
        for (Map.Entry<String, JsonElement> e : root.entrySet()) {
            ResourceLocation id = ResourceLocation.tryParse(e.getKey());
            if (id == null || !e.getValue().isJsonObject()) {
                OreSpawnIntegrations.LOGGER.warn("Hats compat ({}): skipping malformed anchor entry '{}'", source, e.getKey());
                continue;
            }
            JsonObject o = e.getValue().getAsJsonObject();
            String geo = o.has("geo") ? o.get("geo").getAsString() : "";
            String modeText = o.has("mode") ? o.get("mode").getAsString() : "top";
            Mode mode = "none".equalsIgnoreCase(modeText) ? Mode.NONE : Mode.TOP;
            String bone = o.has("bone") && !o.get("bone").isJsonNull() ? o.get("bone").getAsString() : null;
            List<String> also = new ArrayList<>();
            if (o.has("also") && o.get("also").isJsonArray()) {
                for (JsonElement a : o.getAsJsonArray("also")) {
                    also.add(a.getAsString());
                }
            }
            float offX = 0f, offY = 0f, offZ = 0f;
            if (o.has("offset") && o.get("offset").isJsonArray()) {
                JsonArray off = o.getAsJsonArray("offset");
                if (off.size() == 3) {
                    offX = off.get(0).getAsFloat();
                    offY = off.get(1).getAsFloat();
                    offZ = off.get(2).getAsFloat();
                } else {
                    OreSpawnIntegrations.LOGGER.warn("Hats compat ({}): anchor '{}' offset needs [x, y, z]; ignored", source, e.getKey());
                }
            }
            float minScale = o.has("minScale") ? o.get("minScale").getAsFloat() : 0.4f;
            float maxScale = o.has("maxScale") ? o.get("maxScale").getAsFloat() : 6f;
            if (mode == Mode.TOP && bone == null) {
                OreSpawnIntegrations.LOGGER.warn("Hats compat ({}): anchor '{}' has no bone; treated as none", source, e.getKey());
                mode = Mode.NONE;
            }
            ANCHORS.put(id, new Anchor(geo, bone, List.copyOf(also), mode, offX, offY, offZ, minScale, maxScale));
            n++;
        }
        return n;
    }
}
