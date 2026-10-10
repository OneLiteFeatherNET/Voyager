package net.elytrarace.voyager.setup.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SetupSettingsTest {

    @Test
    void theDefaultDirectoriesAreTheSetupServersOwnUnderTheProjectRoot() {
        SetupSettings settings = SetupSettings.fromProperties(new String[0], name -> null);

        assertThat(settings.dataPath()).isEqualTo(Path.of("run-setup/data"));
        assertThat(settings.worldsPath()).isEqualTo(Path.of("run-setup/worlds"));
        assertThat(settings.host()).isEqualTo("0.0.0.0");
        assertThat(settings.port()).isEqualTo(25566);
    }

    @Test
    void theSystemPropertiesOverrideTheDirectories() {
        Map<String, String> properties = Map.of(
                "VOYAGER_DATA_PATH", "/srv/setup/data",
                "VOYAGER_WORLDS_PATH", "/srv/setup/worlds");

        SetupSettings settings = SetupSettings.fromProperties(new String[0], properties::get);

        assertThat(settings.dataPath()).isEqualTo(Path.of("/srv/setup/data"));
        assertThat(settings.worldsPath()).isEqualTo(Path.of("/srv/setup/worlds"));
    }

    @Test
    void theCommandLineGivesTheHostAndThePort() {
        SetupSettings settings = SetupSettings.fromProperties(new String[] {"127.0.0.1", "25570"}, name -> null);

        assertThat(settings.host()).isEqualTo("127.0.0.1");
        assertThat(settings.port()).isEqualTo(25570);
    }

    @Test
    void aPortOutOfRangeIsRefused() {
        assertThatThrownBy(() -> SetupSettings.fromProperties(new String[] {"0.0.0.0", "70000"}, name -> null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
