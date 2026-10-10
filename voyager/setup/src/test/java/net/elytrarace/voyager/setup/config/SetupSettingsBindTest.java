package net.elytrarace.voyager.setup.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The bind address precedence of the setup server: positional arguments, then the {@code service.bind.*} system
 * properties, then the defaults.
 */
class SetupSettingsBindTest {

    @Test
    void theArgumentsWinOverTheBindProperties() {
        Map<String, String> properties = Map.of("service.bind.port", "25571");

        SetupSettings settings = SetupSettings.fromProperties(new String[] {"127.0.0.1", "25600"}, properties::get);

        assertThat(settings.host()).isEqualTo("127.0.0.1");
        assertThat(settings.port()).isEqualTo(25600);
    }

    @Test
    void theBindPropertiesAreUsedWhenNoArgumentIsGiven() {
        Map<String, String> properties = Map.of("service.bind.host", "10.0.0.5", "service.bind.port", "25571");

        SetupSettings settings = SetupSettings.fromProperties(new String[0], properties::get);

        assertThat(settings.host()).isEqualTo("10.0.0.5");
        assertThat(settings.port()).isEqualTo(25571);
    }

    @Test
    void theDefaultsAreUsedWhenNothingIsGiven() {
        SetupSettings settings = SetupSettings.fromProperties(new String[0], name -> null);

        assertThat(settings.host()).isEqualTo("0.0.0.0");
        assertThat(settings.port()).isEqualTo(25566);
    }

    @Test
    void aBindPortOutOfRangeIsRefusedNamingTheProperty() {
        Map<String, String> properties = Map.of("service.bind.port", "70000");

        assertThatThrownBy(() -> SetupSettings.fromProperties(new String[0], properties::get))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("service.bind.port");
    }
}
