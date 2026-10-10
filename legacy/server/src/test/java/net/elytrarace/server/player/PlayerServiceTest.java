package net.elytrarace.server.player;

import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@EnvTest
class PlayerServiceTest {

    @Test
    void getPlayerReturnsEmptyForUnknownUuid(Env env) {
        var instance = (InstanceContainer) env.createFlatInstance();
        var playerService = new PlayerServiceImpl(instance);

        var result = playerService.getPlayer(java.util.UUID.randomUUID());
        assertThat(result).isEmpty();
    }

    @Test
    void lobbyInstanceIsAccessible(Env env) {
        var instance = (InstanceContainer) env.createFlatInstance();
        var playerService = new PlayerServiceImpl(instance);

        assertThat(playerService.getLobbyInstance()).isSameAs(instance);
    }
}
