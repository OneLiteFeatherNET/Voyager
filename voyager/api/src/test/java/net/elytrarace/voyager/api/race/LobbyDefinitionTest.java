package net.elytrarace.voyager.api.race;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.exception.InvalidLobbyException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LobbyDefinitionTest {

    private static final Vec3 SPAWN = new Vec3(0.5, 64.0, 0.5);

    @Test
    void aBlankWorldNameIsRefused() {
        assertThatThrownBy(() -> new LobbyDefinition("  ", SPAWN))
                .isInstanceOf(InvalidLobbyException.class)
                .hasMessageContaining("world");
    }

    @Test
    void aLobbyKeepsItsWorldAndSpawn() {
        LobbyDefinition lobby = new LobbyDefinition("ElytraLobby", SPAWN);

        assertThat(lobby.world()).isEqualTo("ElytraLobby");
        assertThat(lobby.spawn()).isEqualTo(SPAWN);
    }

    @Test
    void twoDefinitionsWithTheSameWorldAndSpawnAreEqual() {
        assertThat(new LobbyDefinition("ElytraLobby", SPAWN))
                .isEqualTo(new LobbyDefinition("ElytraLobby", new Vec3(0.5, 64.0, 0.5)));
    }
}
