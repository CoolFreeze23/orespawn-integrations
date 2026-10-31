package danger.orespawn.integrations.story.chaos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

/**
 * Static checks on the chaos group's data (no Minecraft classes): the casket functions never test rank 0 as
 * "matches 0" (an unset score fails it), every function they call is ours or lullaby's Lucky Blocks' (read from the
 * pack's jar when it is installed), the rank-0 functions never name a whole boss egg or a big summon outside the
 * reroll lists, and the tags hold the cards' counts.
 */
class ChaosDataTest {

    private static final Path DATA = Path.of("src/main/resources/data");
    private static final Path FUNCTIONS = DATA.resolve("orespawn_integrations/function");
    private static final Path LY_JAR = Path.of(System.getProperty("user.home"),
            "AppData/Roaming/PrismLauncher/instances/CrazyCraft 5.0/minecraft/mods/ly-lucky-blocks-v1.3.3.jar");
    private static final Pattern CALL = Pattern.compile("function ((?:lucky_blocks|orespawn_integrations):[a-z0-9_/]+)");

    private static String read(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    private static List<Path> casketFunctions() throws IOException {
        List<Path> files = new ArrayList<>();
        files.add(DATA.resolve("lucky_blocks/function/random/random.mcfunction"));
        try (Stream<Path> s = Files.walk(FUNCTIONS.resolve("lucky"))) {
            s.filter(p -> p.toString().endsWith(".mcfunction")).forEach(files::add);
        }
        return files;
    }

    private static String id(Path file) {
        Path rel = FUNCTIONS.relativize(file);
        return "orespawn_integrations:" + rel.toString().replace('\\', '/').replace(".mcfunction", "");
    }

    @Test
    void rankZeroIsTheFallThrough() throws IOException {
        for (Path f : casketFunctions()) {
            for (String line : read(f).split("\n")) {
                if (line.strip().startsWith("#")) {
                    continue;
                }
                assertTrue(!line.matches(".*matches 0(\\s.*)?$"), f + ": rank 0 written as 'matches 0': " + line);
            }
        }
        String read = read(FUNCTIONS.resolve("lucky/rank/read.mcfunction"));
        assertTrue(read.contains("unless score #oi_rank cc_lucky matches 1.."), "rank 0 is not the fall-through");
        assertTrue(read.contains("@p[distance=..6"), "the opener is not read as @p[distance=..6]");
        assertTrue(read.contains("apotheosis:progression/summit"), "0.10.0 reads Summit for Knight and up");
    }

    @Test
    void everyCalledFunctionExists() throws IOException {
        Set<String> known = new HashSet<>();
        for (Path f : casketFunctions()) {
            known.add(id(f));
        }
        try (Stream<Path> s = Files.walk(FUNCTIONS)) {
            s.filter(p -> p.toString().endsWith(".mcfunction")).forEach(p -> known.add(id(p)));
        }
        boolean haveLy = Files.exists(LY_JAR);
        if (haveLy) {
            try (ZipFile jar = new ZipFile(LY_JAR.toFile())) {
                jar.stream().map(e -> e.getName())
                        .filter(n -> n.startsWith("data/lucky_blocks/function/") && n.endsWith(".mcfunction"))
                        .forEach(n -> known.add("lucky_blocks:" + n.substring("data/lucky_blocks/function/".length(),
                                n.length() - ".mcfunction".length())));
            }
        }
        List<String> missing = new ArrayList<>();
        for (Path f : casketFunctions()) {
            Matcher m = CALL.matcher(read(f));
            while (m.find()) {
                String called = m.group(1);
                if (called.startsWith("lucky_blocks:") && !haveLy) {
                    continue;
                }
                if (!known.contains(called) && !called.equals("lucky_blocks:random/random")) {
                    missing.add(f.getFileName() + " -> " + called);
                }
            }
        }
        assertTrue(missing.isEmpty(), "calls to missing functions:\n" + String.join("\n", missing));
    }

    @Test
    void hatchlingTableNamesNoWholeEggOrBigSummon() throws IOException {
        Set<String> rerollLists = Set.of("item_banned.mcfunction", "entity_banned.mcfunction", "block_banned.mcfunction");
        try (Stream<Path> s = Files.walk(FUNCTIONS.resolve("lucky/hatchling"))) {
            for (Path f : s.filter(p -> p.toString().endsWith(".mcfunction")).toList()) {
                if (rerollLists.contains(f.getFileName().toString())) {
                    continue;
                }
                String text = read(f);
                for (String banned : new String[] {"the_king_spawn_egg", "the_queen_spawn_egg", "godzilla_spawn_egg",
                        "wither_spawn_egg", "ender_dragon_spawn_egg", "minecraft:wither ", "ender_dragon\"", "minecraft:lava ",
                        "lucky_blocks:random/trap/random", "lucky_blocks:random/effect/random",
                        "lucky_blocks:random/summon/custom/random"}) {
                    assertTrue(!text.contains(banned), f + " names " + banned);
                }
            }
        }
    }

    @Test
    void hatchlingTrapsKeepEighteenSlots() throws IOException {
        String trap = read(FUNCTIONS.resolve("lucky/hatchling/trap.mcfunction"));
        assertTrue(trap.contains("random value 1..18"), "the rank-0 trap table lost ly's 18 slots");
        String custom = read(FUNCTIONS.resolve("lucky/hatchling/custom.mcfunction"));
        assertTrue(custom.contains("random value 1..35") && !custom.contains("/op/enemy/"), "the custom branch keeps OP enemies");
    }

    private static int count(String rel) throws IOException {
        JsonObject tag = JsonParser.parseString(read(DATA.resolve(rel))).getAsJsonObject();
        assertTrue(!tag.has("replace") || !tag.get("replace").getAsBoolean(), rel + " replaces instead of adding");
        return tag.getAsJsonArray("values").size();
    }

    @Test
    void tagCountsMatchTheCards() throws IOException {
        assertEquals(105, count("orespawn_integrations/tags/item/ovos_da_despensa.json"), "larder outputs");
        assertEquals(17, count("orespawn_integrations/tags/item/royal_seal_eggs.json"), "sealed eggs");
        assertEquals(18, count("orespawn_integrations/tags/item/royal_eggs.json"), "royal egg blocks");
        assertEquals(8, count("orespawn_integrations/tags/item/hbm_hazard_exempt.json"), "HBM exemptions");
        assertEquals(7, count("orespawn_integrations/tags/item/royal_diet.json"), "royal diet");
        assertEquals(46, count("orespawn_integrations/tags/entity_type/heavyweights.json"), "heavyweights");
        assertEquals(46, count("apothic_spawners/tags/entity_type/blacklisted_from_spawners.json"), "Apothic blacklist merge");
        assertEquals(3, count("orespawn_integrations/tags/item/statues/no_spawner.json"), "statue no-spawner list");
    }

    @Test
    void refusedGiftsAreChanceZero() throws IOException {
        Path holders = DATA.resolve("orespawn_integrations/pandorasbox/pandora_effect_holders");
        for (String h : new String[] {"mobzilla_apocalypse", "grande_recusa/void_30", "grande_recusa/wither_army"}) {
            JsonObject j = JsonParser.parseString(read(holders.resolve(h + ".json"))).getAsJsonObject();
            assertEquals("minecraft:fixed_chance", j.get("type").getAsString(), h);
            assertEquals(0.0, j.get("chance").getAsDouble(), h + " can roll at random");
        }
    }

    @Test
    void joinDayDatesVeteransBack() {
        assertEquals(40, CasketCore.joinDay(40, 0));
        assertEquals(37, CasketCore.joinDay(40, 3 * 24000));
    }

    @Test
    void sealRule() {
        var mob = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("orespawn", "godzilla_spawn_egg");
        assertEquals(RoyalSeal.Verdict.OPEN, RoyalSeal.rule(mob, false, true, false, false));
        assertEquals(RoyalSeal.Verdict.RANK, RoyalSeal.rule(mob, false, false, false, true));
        assertEquals(RoyalSeal.Verdict.SPAWNER, RoyalSeal.rule(mob, true, false, true, true));
    }
}
