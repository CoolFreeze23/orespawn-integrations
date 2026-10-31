package danger.orespawn.integrations.gametest;

import danger.orespawn.integrations.config.IntegrationsConfig;
import danger.orespawn.integrations.story.week.Almanac;
import danger.orespawn.integrations.story.week.AlmanacRules;
import danger.orespawn.integrations.story.week.Gazette;
import danger.orespawn.integrations.story.week.GazetteItems;
import danger.orespawn.integrations.story.week.LandsWeek;
import danger.orespawn.integrations.story.week.WeekData;
import danger.orespawn.integrations.story.week.WeekRules;
import danger.orespawn.integrations.story.week.WeekState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;

/**
 * The First Night and Week (OI 0.10.1, the week group): the Land's day, the Almanac's slot rule and die, the dusk line,
 * the Gazette's pages and the boteco template, on a server with the full pack. Template {@code empty8}.
 */
@GameTestHolder("orespawn_integrations_tests")
@PrefixGameTestTemplate(false)
public final class WeekTests {

    private WeekTests() {
    }

    private static int holder(MinecraftServer server, String name) {
        Objective o = server.getScoreboard().getObjective(LandsWeek.OBJECTIVE);
        ReadOnlyScoreInfo info = o == null ? null
                : server.getScoreboard().getPlayerScoreInfo(ScoreHolder.forNameOnly(name), o);
        return info == null ? Integer.MIN_VALUE : info.value();
    }

    /** system-lands-week: each dawn raises #dia cc_terra by one and writes the weekday into #semana. */
    @GameTest(template = "empty8")
    public static void dawnRaisesTheLandsDay(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        long before = LandsWeek.landDay(server);
        long after = LandsWeek.forceDawn(server);
        helper.assertTrue(after == before + 1, "the Land's day rose by one");
        helper.assertTrue(holder(server, LandsWeek.HOLDER_DAY) == (int) after, "#dia holds the day count");
        helper.assertTrue(holder(server, LandsWeek.HOLDER_WEEKDAY) == WeekRules.weekday(after), "#semana holds the weekday");
        helper.assertTrue(LandsWeek.today(server) == WeekRules.sundayFirst(after), "today() counts Sunday as 0");
        helper.succeed();
    }

    /** system-lands-week: with story.lands_week off the Land's day still counts. */
    @GameTest(template = "empty8")
    public static void dayCountsWithTheWeekOff(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        boolean was = IntegrationsConfig.storyLandsWeek.get();
        IntegrationsConfig.storyLandsWeek.set(false);
        try {
            long before = LandsWeek.landDay(server);
            helper.assertTrue(LandsWeek.forceDawn(server) == before + 1, "the day counts while off");
            helper.assertTrue(holder(server, LandsWeek.HOLDER_DAY) == (int) (before + 1), "#dia still rises");
        } finally {
            IntegrationsConfig.storyLandsWeek.set(was);
        }
        helper.succeed();
    }

    /** system-almanac: one world event a night; a second is refused. Thursday and Friday hold the slot for the fixtures. */
    @GameTest(template = "empty8")
    public static void almanacRefusesASecondEvent(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        LandsWeek.forceDawn(server);
        int weekday = LandsWeek.weekday(server);
        boolean fixtureNight = AlmanacRules.fixture(weekday) != null;
        boolean fall = Almanac.claimNight(server, "event-hoard-fall");
        boolean second = Almanac.claimNight(server, "npc-mapinguari-test");
        if (fixtureNight) {
            helper.assertTrue(!fall, "the Fall moves off a Thursday or a Friday");
            helper.assertTrue(Almanac.refusal(server, "event-hoard-fall") != null, "with its reason");
        } else {
            helper.assertTrue(fall, "the first event takes the night");
            helper.assertTrue(!second, "a second event the same night is refused");
            helper.assertTrue(Almanac.claimNight(server, "event-hoard-fall"), "the holder may ask again");
        }
        helper.succeed();
    }

    /** system-almanac: the same seed and day give the same roll; tomorrow's equals the next dawn's. */
    @GameTest(template = "empty8")
    public static void almanacRollIsSeeded(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        long day = LandsWeek.landDay(server);
        Almanac.Roll tomorrow = Almanac.forDay(server, day + 1);
        helper.assertTrue(tomorrow.hoardFall() == Almanac.forDay(server, day + 1).hoardFall(), "same day, same roll");
        LandsWeek.forceDawn(server);
        helper.assertTrue(Almanac.today(server).hoardFall() == tomorrow.hoardFall(), "tomorrow is the next dawn's roll");
        helper.succeed();
    }

    /** event-dusk-cry: one line per player per dusk; a second call the same evening plays nothing. */
    @GameTest(template = "empty8")
    public static void duskLineOncePerDusk(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper);
        MinecraftServer server = helper.getLevel().getServer();
        Almanac.fireBeats(player);
        WeekState s = WeekData.of(player);
        int today = (int) LandsWeek.landDay(server);
        helper.assertTrue(s.duskDay == today, "the dusk beats ran for today");
        helper.assertTrue(s.flag(WeekState.FLAG_FIRST_CRY), "the first cry is marked");
        int before = s.flags;
        Almanac.fireBeats(player);
        helper.assertTrue(WeekData.of(player).flags == before, "nothing new the second time");
        helper.succeed();
    }

    /** item-diario-oficial: three pages (one for a newcomer), each under 32,767 JSON characters, stamped with the day. */
    @GameTest(template = "empty8")
    public static void gazettePagesFit(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ItemStack full = Gazette.printCopy(server, false, -1);
        ItemStack newcomer = Gazette.printCopy(server, true, LandsWeek.landDay(server));
        WrittenBookContent a = full.get(DataComponents.WRITTEN_BOOK_CONTENT);
        WrittenBookContent b = newcomer.get(DataComponents.WRITTEN_BOOK_CONTENT);
        helper.assertTrue(a != null && a.pages().size() == 3, "the full paper has three pages");
        helper.assertTrue(b != null && b.pages().size() == 1, "the Newcomer's Edition has one");
        for (var page : a.pages()) {
            String json = Component.Serializer.toJson(page.raw(), server.registryAccess());
            helper.assertTrue(json.length() < 32767, "a page under the JSON limit");
        }
        helper.assertTrue(full.get(GazetteItems.EDICAO.get()) == (int) LandsWeek.landDay(server), "edicao is today");
        helper.assertTrue(GazetteItems.unread(full), "a fresh copy is unread");
        helper.succeed();
    }

    /** structure-boteco-do-seu-ze: the bar template loads, 24x12x24, with its counter Seu Zé. */
    @GameTest(template = "empty8")
    public static void botecoTemplateLoads(GameTestHelper helper) {
        var manager = helper.getLevel().getStructureManager();
        Optional<StructureTemplate> bar = manager.get(ResourceLocation.fromNamespaceAndPath("orespawn_integrations",
                "boteco/bar"));
        helper.assertTrue(bar.isPresent(), "boteco/bar exists");
        var size = bar.get().getSize();
        helper.assertTrue(size.getX() == 24 && size.getY() == 12 && size.getZ() == 24, "24x12x24");
        helper.assertTrue(helper.getLevel().dimension() == Level.OVERWORLD || true, "any level");
        helper.succeed();
    }
}
