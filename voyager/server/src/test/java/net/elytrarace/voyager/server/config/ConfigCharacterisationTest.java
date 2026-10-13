package net.elytrarace.voyager.server.config;

import net.elytrarace.voyager.platform.proxy.ProxyForwardingSettings;
import net.elytrarace.voyager.server.config.exception.InvalidSettingException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the configuration behaviour that the adopt-avaje-config change must keep, against the code as it is before the
 * change. Each value is handed in as a map, so the test never reads or writes the JVM.
 */
class ConfigCharacterisationTest {

    private static final String VELOCITY_SECRET_ENVIRONMENT = "VOYAGER_VELOCITY_SECRET";

    @TempDir
    Path root;

    @Test
    void aBindPortThatIsNotANumberIsRefusedAndNamed() throws IOException {
        assertThatThrownBy(() -> settings(Map.of(ServerSettings.BIND_PORT_PROPERTY, "abc")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ServerSettings.BIND_PORT_PROPERTY)
                .hasMessageContaining("abc");
    }

    @Test
    void aMinimumRacerCountOfZeroIsRefused() throws IOException {
        assertThatThrownBy(() -> settings(Map.of(ServerSettings.MIN_RACERS_PROPERTY, "0")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ServerSettings.MIN_RACERS_PROPERTY);
    }

    @Test
    void aLobbyWorldWithoutASpawnIsRefusedAndNamesTheSpawn() throws IOException {
        assertThatThrownBy(() -> settings(Map.of(ServerSettings.LOBBY_WORLD_PROPERTY, "LobbyTest")))
                .isInstanceOf(InvalidSettingException.class)
                .hasMessageContaining(ServerSettings.LOBBY_SPAWN_PROPERTY);
    }

    @Test
    void theVelocitySecretPrefersTheEnvironment() {
        ProxyForwardingSettings settings = ProxyForwardingSettings.from(
                Map.of(VELOCITY_SECRET_ENVIRONMENT, "s3cret"),
                Map.of(ProxyForwardingSettings.PROPERTY_NAME, "other"));

        assertThat(settings.secret()).isEqualTo("s3cret");
    }

    private ServerSettings settings(Map<String, String> values) throws IOException {
        Path data = Files.createDirectories(root.resolve("catalogue"));
        Path worlds = Files.createDirectories(root.resolve("racetracks"));
        Map<String, String> all = new HashMap<>();
        all.put(ServerSettings.DATA_PATH_PROPERTY, data.toString());
        all.put(ServerSettings.WORLDS_PATH_PROPERTY, worlds.toString());
        all.putAll(values);
        return ServerSettings.fromProperties(new String[0], all::get);
    }
}
