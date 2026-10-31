package danger.orespawn.integrations.story.shelf9;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Static checks of the shelf9 group's data: every JSON parses, every item and block id resolves against the pack's
 * registry dump (when the design repo is on this machine) plus our own items, and the Shelf 9 template keeps the
 * card's and 17-assets.md's limits and carries every sign role the code looks for.
 */
class ShelfNineDataTest {

    private static final String DATA = "/data/orespawn_integrations/";
    private static final List<String> JSON = List.of(
            "loot_table/gameplay/hatchling_shell.json",
            "loot_table/gameplay/hatchling_shell/guide.json",
            "loot_table/gameplay/hatchling_shell/snack.json",
            "loot_table/gameplay/hatchling_shell_mage.json",
            "tags/origin/mage.json",
            "worldgen/processor_list/hatching_ground.json",
            "tags/worldgen/structure/shelf9/village.json",
            "tags/worldgen/structure/shelf9/henge.json",
            "tags/worldgen/structure/shelf9/tower.json",
            "tags/worldgen/structure/shelf9/bar.json");
    private static final Path DUMP = Path.of(
            "C:/Homework/Sessions/Japan/crazycraft-world-design-claude/atlas/data/registry_dump.json");
    private static final Set<String> OURS = Set.of(
            "orespawn_integrations:hatchling_shell", "orespawn_integrations:ledger_scrap",
            "orespawn_integrations:guia_de_averbacao");

    private static JsonElement json(String path) throws IOException {
        try (InputStream in = ShelfNineDataTest.class.getResourceAsStream(DATA + path)) {
            assertNotNull(in, "missing " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void everyJsonParses() throws IOException {
        for (String path : JSON) {
            assertTrue(json(path).isJsonObject(), path);
        }
    }

    @Test
    void lootAndStructureIdsResolve() throws IOException {
        Assumptions.assumeTrue(Files.exists(DUMP), "registry dump not on this machine");
        JsonObject dump = JsonParser.parseString(Files.readString(DUMP)).getAsJsonObject();
        Set<String> items = ids(dump, "items");
        items.addAll(OURS);
        Set<String> structures = ids(dump, "structures");
        Pattern itemName = Pattern.compile("\"name\"\\s*:\\s*\"([a-z0-9_.\\-]+:[a-z0-9_/.\\-]+)\"");
        for (String path : JSON) {
            if (!path.startsWith("loot_table")) {
                continue;
            }
            Matcher m = itemName.matcher(json(path).toString());
            while (m.find()) {
                assertTrue(items.contains(m.group(1)), path + ": unknown item " + m.group(1));
            }
        }
        for (String kind : List.of("henge", "tower", "bar")) {
            for (JsonElement value : json("tags/worldgen/structure/shelf9/" + kind + ".json")
                    .getAsJsonObject().getAsJsonArray("values")) {
                String id = value.isJsonObject() ? value.getAsJsonObject().get("id").getAsString() : value.getAsString();
                assertTrue(structures.contains(id), kind + ": unknown structure " + id);
            }
        }
    }

    @Test
    void templateKeepsItsLimitsAndRoles() throws IOException {
        Set<String> roles = new TreeSet<>();
        int spawners = 0;
        int campfires = 0;
        CompoundTag root;
        long bytes;
        try (InputStream in = ShelfNineDataTest.class.getResourceAsStream(DATA + "structure/hatching_ground.nbt")) {
            assertNotNull(in);
            byte[] all = in.readAllBytes();
            bytes = all.length;
            root = NbtIo.readCompressed(new java.io.ByteArrayInputStream(all), NbtAccounter.unlimitedHeap());
        }
        assertTrue(bytes < 100_000, "template under 100 KB: " + bytes);
        assertEquals(3955, root.getInt("DataVersion"));
        ListTag size = root.getList("size", Tag.TAG_INT);
        assertTrue(size.getInt(0) <= 21 && size.getInt(1) <= 16 && size.getInt(2) <= 21, "21 x 16 x 21 at most");
        ListTag palette = root.getList("palette", Tag.TAG_COMPOUND);
        Set<String> blockIds = new HashSet<>();
        for (int i = 0; i < palette.size(); i++) {
            blockIds.add(palette.getCompound(i).getString("Name"));
        }
        ListTag blocks = root.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag block = blocks.getCompound(i);
            String name = palette.getCompound(block.getInt("state")).getString("Name");
            if (name.endsWith("spawner")) {
                spawners++;
            }
            if (name.equals("minecraft:campfire")) {
                campfires++;
            }
            if (block.contains("nbt") && block.getCompound("nbt").contains("cc_role")) {
                roles.add(block.getCompound("nbt").getString("cc_role"));
            }
        }
        assertTrue(spawners == 0, "no spawner");
        assertEquals(1, campfires, "one signal campfire");
        for (int i = 0; i < 8; i++) {
            assertTrue(roles.contains("register_" + i), "Register sign " + i);
        }
        for (String side : List.of("n", "e", "s", "w")) {
            assertTrue(roles.contains("ways_" + side), "Ways sign " + side);
        }
        assertTrue(roles.containsAll(List.of("door", "batch", "shelf10")), roles.toString());
        assertTrue(blockIds.contains("secretroomsmod:ghost_block"), "the Shelf 10 panel");
        assertTrue(blockIds.contains("orespawn:chicken_spawn_block"), "the straw's larder placeholders");
        if (Files.exists(DUMP)) {
            Set<String> known = ids(JsonParser.parseString(Files.readString(DUMP)).getAsJsonObject(), "blocks");
            for (String id : blockIds) {
                assertTrue(known.contains(id), "unknown block " + id);
            }
        }
    }

    private static Set<String> ids(JsonObject dump, String registry) {
        Set<String> out = new HashSet<>();
        for (JsonElement e : dump.getAsJsonObject(registry).getAsJsonArray("entries")) {
            out.add(e.getAsJsonObject().get("id").getAsString());
        }
        return out;
    }
}
