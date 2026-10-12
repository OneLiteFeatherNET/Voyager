package net.elytrarace.voyager.server.config;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.LobbyDefinition;
import net.elytrarace.voyager.server.config.exception.InvalidSettingException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The lobby and maximum settings as the server reads them. Each test reads its own properties map and points the two
 * directories at its own temp folders, so no test depends on another or on the JVM's properties.
 */
class ServerSettingsLobbyTest {

    @TempDir
    Path root;

    @Test
    void bothLobbySettingsAreRead() throws IOException {
        ServerSettings settings = ServerSettings.fromProperties(new String[0], propertiesWith(Map.of(
                ServerSettings.LOBBY_WORLD_PROPERTY, "ElytraLobby",
                ServerSettings.LOBBY_SPAWN_PROPERTY, "0,64,0")));

        assertThat(settings.lobby()).contains(new LobbyDefinition("ElytraLobby", new Vec3(0.0, 64.0, 0.0)));
    }

    @Test
    void noLobbyIsTheDefault() throws IOException {
        ServerSettings settings = ServerSettings.fromProperties(new String[0], propertiesWith(Map.of()));

        assertThat(settings.lobby()).isEmpty();
    }

    @Test
    void aWorldWithoutASpawnIsRefusedAndNamesTheSpawn() throws IOException {
        assertThatThrownBy(() -> ServerSettings.fromProperties(new String[0], propertiesWith(Map.of(
                ServerSettings.LOBBY_WORLD_PROPERTY, "ElytraLobby"))))
                .isInstanceOf(InvalidSettingException.class)
                .hasMessageContaining(ServerSettings.LOBBY_SPAWN_PROPERTY);
    }

    @Test
    void aSpawnWithoutAWorldIsRefusedAndNamesTheWorld() throws IOException {
        assertThatThrownBy(() -> ServerSettings.fromProperties(new String[0], propertiesWith(Map.of(
                ServerSettings.LOBBY_SPAWN_PROPERTY, "0,64,0"))))
                .isInstanceOf(InvalidSettingException.class)
                .hasMessageContaining(ServerSettings.LOBBY_WORLD_PROPERTY);
    }

    @Test
    void aSpawnThatIsNotThreeNumbersIsRefusedAndNamesTheSettingAndTheText() throws IOException {
        assertThatThrownBy(() -> ServerSettings.fromProperties(new String[0], propertiesWith(Map.of(
                ServerSettings.LOBBY_WORLD_PROPERTY, "ElytraLobby",
                ServerSettings.LOBBY_SPAWN_PROPERTY, "0,64"))))
                .isInstanceOf(InvalidSettingException.class)
                .hasMessageContaining(ServerSettings.LOBBY_SPAWN_PROPERTY)
                .hasMessageContaining("0,64");
    }

    @Test
    void theMaximumDefaultsToSixteenInProductionAndInDev() throws IOException {
        assertThat(ServerSettings.fromProperties(new String[0], propertiesWith(Map.of())).maximumRacers()).isEqualTo(16);
        assertThat(ServerSettings.fromProperties(new String[0], propertiesWith(Map.of(
                ServerSettings.DEV_MODE_PROPERTY, "true"))).maximumRacers()).isEqualTo(16);
    }

    @Test
    void aMaximumBelowTheMinimumIsRefusedAndNamesBothSettings() throws IOException {
        assertThatThrownBy(() -> ServerSettings.fromProperties(new String[0], propertiesWith(Map.of(
                ServerSettings.MIN_RACERS_PROPERTY, "3",
                ServerSettings.MAX_PLAYERS_PROPERTY, "2"))))
                .isInstanceOf(InvalidSettingException.class)
                .hasMessageContaining(ServerSettings.MIN_RACERS_PROPERTY)
                .hasMessageContaining(ServerSettings.MAX_PLAYERS_PROPERTY);
    }

    @Test
    void aMaximumThatIsNotANumberIsRefusedAndNamesTheSetting() throws IOException {
        assertThatThrownBy(() -> ServerSettings.fromProperties(new String[0], propertiesWith(Map.of(
                ServerSettings.MAX_PLAYERS_PROPERTY, "many"))))
                .isInstanceOf(InvalidSettingException.class)
                .hasMessageContaining(ServerSettings.MAX_PLAYERS_PROPERTY);
    }

    /** The properties the settings read, with both directories under the temp root. */
    private Function<String, String> propertiesWith(Map<String, String> extra) throws IOException {
        Path data = Files.createDirectories(root.resolve("catalogue"));
        Path worlds = Files.createDirectories(root.resolve("racetracks"));
        Map<String, String> properties = new HashMap<>(Map.of(
                ServerSettings.DATA_PATH_PROPERTY, data.toString(),
                ServerSettings.WORLDS_PATH_PROPERTY, worlds.toString()));
        properties.putAll(extra);
        return properties::get;
    }
}
