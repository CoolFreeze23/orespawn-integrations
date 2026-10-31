package danger.orespawn.integrations.gametest;

import danger.orespawn.integrations.world.sightings.SightingNames;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Map pins are literal text the server writes, so each player gets the pin name in the language their client reports.
 * A pt-BR player never sees an English name; any other language gets English.
 */
@GameTestHolder("orespawn_integrations_tests")
@PrefixGameTestTemplate(false)
public final class SightingNameTests {

    private static final String PIN = "orespawn_integrations.avistamento.pin.simpleluckyblock.lucky_block";

    private SightingNameTests() {
    }

    private static ServerPlayer playerIn(GameTestHelper helper, String language) {
        ServerPlayer player = MockPlayers.inLevel(helper);
        ClientInformation d = ClientInformation.createDefault();
        player.updateOptions(new ClientInformation(language, d.viewDistance(), d.chatVisibility(), d.chatColors(),
                d.modelCustomisation(), d.mainHand(), d.textFilteringEnabled(), d.allowsListing()));
        return player;
    }

    @GameTest(template = "empty8")
    public static void ptBrPlayerGetsPortuguesePinName(GameTestHelper helper) {
        helper.assertTrue("Arca Lacrada".equals(SightingNames.resolve(playerIn(helper, "pt_br"), PIN)),
                "pt_br pin name: " + SightingNames.resolve(playerIn(helper, "pt_br"), PIN));
        helper.assertTrue("Sealed Casket".equals(SightingNames.resolve(playerIn(helper, "en_us"), PIN)), "en_us pin name");
        helper.assertTrue("Sealed Casket".equals(SightingNames.resolve(playerIn(helper, "de_de"), PIN)), "other languages fall back to English");
        helper.succeed();
    }
}
