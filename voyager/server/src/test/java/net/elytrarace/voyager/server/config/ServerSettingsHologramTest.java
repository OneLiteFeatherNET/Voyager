package net.elytrarace.voyager.server.config;

import net.elytrarace.voyager.api.math.Vec3;
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
 * The lobby hologram's position as the server reads it. Each test reads its own properties map and points the two directories
 * at its own temp folders, so no test depends on another or on the JVM's properties.
 */
class ServerSettingsHologramTest {

    private static final String LOBBY_WORLD = "ElytraLobby";
    private static final String LOBBY_SPAWN = "0,64,0";

    @TempDir
    Path root;

    @Test
    void theHologramPositionIsRead() throws IOException {
        ServerSettings settings = ServerSettings.fromProperties(new String[0], propertiesWith(Map.of(
                ServerSettings.LOBBY_WORLD_PROPERTY, LOBBY_WORLD,
                ServerSettings.LOBBY_SPAWN_PROPERTY, LOBBY_SPAWN,
                ServerSettings.LOBBY_HOLOGRAM_PROPERTY, "0,70,0")));

        assertThat(settings.lobbyHologram()).contains(new Vec3(0.0, 70.0, 0.0));
    }

    @Test
    void noHologramPositionIsNoHologram() throws IOException {
        ServerSettings settings = ServerSettings.fromProperties(new String[0], propertiesWith(Map.of(
                ServerSettings.LOBBY_WORLD_PROPERTY, LOBBY_WORLD,
                ServerSettings.LOBBY_SPAWN_PROPERTY, LOBBY_SPAWN)));

        assertThat(settings.lobbyHologram()).isEmpty();
    }

    @Test
    void aPositionThatIsNotThreeNumbersIsRefusedAndNamesTheSettingAndTheText() throws IOException {
        assertThatThrownBy(() -> ServerSettings.fromProperties(new String[0], propertiesWith(Map.of(
                ServerSettings.LOBBY_WORLD_PROPERTY, LOBBY_WORLD,
                ServerSettings.LOBBY_SPAWN_PROPERTY, LOBBY_SPAWN,
                ServerSettings.LOBBY_HOLOGRAM_PROPERTY, "0,70"))))
                .isInstanceOf(InvalidSettingException.class)
                .hasMessageContaining(ServerSettings.LOBBY_HOLOGRAM_PROPERTY)
                .hasMessageContaining("0,70");
    }

    @Test
    void aPositionWithoutALobbyWorldIsRefusedAndNamesBothSettings() throws IOException {
        assertThatThrownBy(() -> ServerSettings.fromProperties(new String[0], propertiesWith(Map.of(
                ServerSettings.LOBBY_HOLOGRAM_PROPERTY, "0,70,0"))))
                .isInstanceOf(InvalidSettingException.class)
                .hasMessageContaining(ServerSettings.LOBBY_HOLOGRAM_PROPERTY)
                .hasMessageContaining(ServerSettings.LOBBY_WORLD_PROPERTY);
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
