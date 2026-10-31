package danger.orespawn.integrations.story.deed;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The deed log's pure rules (system-deed-log test lines) and its templates' text rules. */
class DeedRulesTest {

    private static final UUID ANA = UUID.randomUUID();
    private static final Path FRAGMENTS = Path.of("_lang_fragments/0.10.1");

    private static DeedRules.Entry line(String key, long day, DeedRules.Kind kind) {
        return new DeedRules.Entry(ANA, key, List.of("Ana"), day, kind, List.of());
    }

    /** A 16 KB cap drops the oldest line first. */
    @Test
    void capDropsOldest() {
        List<DeedRules.Entry> lines = new ArrayList<>();
        String big = "x".repeat(1000);
        for (int i = 0; i < 20; i++) {
            lines.add(new DeedRules.Entry(ANA, "orespawn_integrations.deed.k" + i, List.of(big), i, DeedRules.Kind.PLAIN, List.of()));
        }
        int dropped = DeedRules.trim(lines);
        assertTrue(dropped > 0);
        assertEquals("orespawn_integrations.deed.k" + dropped, lines.get(0).key);
        int total = lines.stream().mapToInt(DeedRules.Entry::bytes).sum();
        assertTrue(total <= DeedRules.CAP_BYTES);
    }

    /** A deed retold five Saturdays sits at rung 4 and stays; a true causo never moves. */
    @Test
    void ladderTopsOutAtFour() {
        DeedRules.Entry e = line("orespawn_integrations.deed.boss_kill", 1, DeedRules.Kind.WEAPON);
        for (int i = 0; i < 5; i++) {
            DeedRules.retell(e);
        }
        assertEquals(4, e.rung);
        DeedRules.Entry dare = line("orespawn_integrations.deed.dare", 1, DeedRules.Kind.TRUE);
        DeedRules.retell(dare);
        assertEquals(0, dare.rung);
    }

    /** A gift line climbs down: dragon egg, duck egg, chicken egg, and stays there. */
    @Test
    void giftClimbsDown() {
        assertEquals(0, DeedRules.giftStep(0));
        assertEquals(1, DeedRules.giftStep(1));
        assertEquals(2, DeedRules.giftStep(2));
        assertEquals(2, DeedRules.giftStep(4));
    }

    /** Saturday: this week's unretold lines newest first, then the best older line, at most four. */
    @Test
    void saturdayPick() {
        List<DeedRules.Entry> own = new ArrayList<>();
        DeedRules.Entry old = line("orespawn_integrations.deed.old", 2, DeedRules.Kind.WEAPON);
        old.tellings = 3;
        own.add(old);
        own.add(line("orespawn_integrations.deed.a", 8, DeedRules.Kind.PLAIN));
        own.add(line("orespawn_integrations.deed.b", 9, DeedRules.Kind.PLAIN));
        List<DeedRules.Entry> pick = DeedRules.saturdayPick(own, 7);
        assertEquals(3, pick.size());
        assertEquals("orespawn_integrations.deed.b", pick.get(0).key);
        assertSame(old, pick.get(2));
        for (int i = 0; i < 6; i++) {
            own.add(line("orespawn_integrations.deed.n" + i, 10, DeedRules.Kind.PLAIN));
        }
        assertEquals(DeedRules.SATURDAY_LINES, DeedRules.saturdayPick(own, 7).size());
    }

    /** The epithet follows the most-retold weapon deed; no weapon deed, no epithet. */
    @Test
    void epithetFollowsMostRetoldWeapon() {
        List<DeedRules.Entry> own = new ArrayList<>();
        own.add(line("orespawn_integrations.deed.plain", 1, DeedRules.Kind.PLAIN));
        assertEquals(0, DeedRules.epithetRung(own));
        DeedRules.Entry a = line("orespawn_integrations.deed.boss_kill", 1, DeedRules.Kind.WEAPON);
        DeedRules.retell(a);
        DeedRules.retell(a);
        DeedRules.retell(a);
        DeedRules.Entry b = line("orespawn_integrations.deed.boss_kill", 2, DeedRules.Kind.WEAPON);
        DeedRules.retell(b);
        own.add(a);
        own.add(b);
        assertEquals(3, DeedRules.epithetRung(own));
    }

    /** The Gazette picks yesterday's biggest: a boss kill over anything else. */
    @Test
    void headlinePrefersABossKill() {
        DeedRules.Entry plain = line("orespawn_integrations.deed.market", 5, DeedRules.Kind.PLAIN);
        DeedRules.Entry boss = line("orespawn_integrations.deed.boss_kill", 5, DeedRules.Kind.WEAPON);
        assertSame(boss, DeedRules.headline(List.of(plain, boss)));
        assertNull(DeedRules.headline(List.of()));
    }

    /**
     * No pt-BR deed template puts an article before a name argument, and none of ours uses a gendered word on the
     * player (spine 3.2): "de %s", never "da %s" or "a %s".
     */
    @Test
    void ptBrTemplatesPutNoArticleBeforeAName() throws IOException {
        JsonObject pt = JsonParser.parseString(Files.readString(FRAGMENTS.resolve("ranks.pt_br.json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
        Pattern article = Pattern.compile("(?i)(^|[ ])(o|a|os|as|do|da|dos|das|no|na|pelo|pela|ao|à)[ ]+%([0-9]+[$])?s");
        for (Map.Entry<String, com.google.gson.JsonElement> e : pt.entrySet()) {
            if (e.getKey().contains(".deed.") || e.getKey().contains(".truco.")) {
                assertFalse(article.matcher(e.getValue().getAsString()).find(), e.getKey());
            }
        }
    }
}
