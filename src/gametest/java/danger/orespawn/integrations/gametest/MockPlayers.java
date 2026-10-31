package danger.orespawn.integrations.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import java.util.UUID;

/**
 * Vanilla's {@code makeMockServerPlayerInLevel}, plus NeoForge's {@code NetworkRegistry.configureMockConnection}: the
 * mock connection then takes every mod's payloads. Without it, the first mod that sends a player data at login (Apothic
 * Attributes' config, Neo Origins' sync) throws "may not be sent to the client" and fails the test before it starts.
 * Unlike vanilla's, this player is in survival, as the players these tests describe are: in creative, using a scrap
 * would never spend it. Each one also has a name of its own: scores are kept by name, so under vanilla's shared
 * "test-mock-player" one test's cc_gemeo or cc_ovo_semana was every later test's too.
 */
final class MockPlayers {

    private MockPlayers() {
    }

    static ServerPlayer inLevel(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                new GameProfile(id, "mock-" + id.toString().substring(0, 8)), false);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(),
                cookie.clientInformation()) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        // the GameTest world's default mode is creative; these tests describe survival play
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }
}
