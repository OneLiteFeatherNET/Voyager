package net.elytrarace.voyager.server.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The bind address precedence of the game server: positional arguments, then the {@code service.bind.*} system
 * properties, then the defaults. Each directory is a fresh temporary one, so the settings constructor accepts them.
 */
class ServerSettingsBindTest {

    @TempDir
    Path tmp;

    private Map<String, String> properties() throws Exception {
        Path data = Files.createDirectories(tmp.resolve("data"));
        Path worlds = Files.createDirectories(tmp.resolve("worlds"));
        Map<String, String> properties = new HashMap<>();
        properties.put(ServerSettings.DATA_PATH_PROPERTY, data.toString());
        properties.put(ServerSettings.WORLDS_PATH_PROPERTY, worlds.toString());
        return properties;
    }

    @Test
    void theArgumentsWinOverTheBindProperties() throws Exception {
        Map<String, String> properties = properties();
        properties.put("service.bind.port", "25571");

        ServerSettings settings = ServerSettings.fromProperties(new String[] {"127.0.0.1", "25600"}, properties::get);

        assertThat(settings.host()).isEqualTo("127.0.0.1");
        assertThat(settings.port()).isEqualTo(25600);
    }

    @Test
    void theBindPropertiesAreUsedWhenNoArgumentIsGiven() throws Exception {
        Map<String, String> properties = properties();
        properties.put("service.bind.host", "10.0.0.5");
        properties.put("service.bind.port", "25571");

        ServerSettings settings = ServerSettings.fromProperties(new String[0], properties::get);

        assertThat(settings.host()).isEqualTo("10.0.0.5");
        assertThat(settings.port()).isEqualTo(25571);
    }

    @Test
    void theDefaultsAreUsedWhenNothingIsGiven() throws Exception {
        ServerSettings settings = ServerSettings.fromProperties(new String[0], properties()::get);

        assertThat(settings.host()).isEqualTo("0.0.0.0");
        assertThat(settings.port()).isEqualTo(25565);
    }

    @Test
    void aBindPortOutOfRangeIsRefusedNamingTheProperty() throws Exception {
        Map<String, String> properties = properties();
        properties.put("service.bind.port", "70000");

        assertThatThrownBy(() -> ServerSettings.fromProperties(new String[0], properties::get))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("service.bind.port");
    }
}
