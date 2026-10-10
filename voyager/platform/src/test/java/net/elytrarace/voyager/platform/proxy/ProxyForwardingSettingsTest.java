package net.elytrarace.voyager.platform.proxy;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProxyForwardingSettingsTest {

    private static final String ENVIRONMENT_NAME = "VOYAGER_VELOCITY_SECRET";
    private static final String PROPERTY_NAME = "voyager.velocity.secret";

    @Test
    void theEnvironmentValueWinsOverTheProperty() {
        ProxyForwardingSettings settings = ProxyForwardingSettings.from(
                Map.of(ENVIRONMENT_NAME, "from-environment"),
                Map.of(PROPERTY_NAME, "from-property"));

        assertThat(settings.secret()).isEqualTo("from-environment");
    }

    @Test
    void thePropertyIsUsedWhenTheEnvironmentIsAbsent() {
        ProxyForwardingSettings settings = ProxyForwardingSettings.from(Map.of(), Map.of(PROPERTY_NAME, "from-property"));

        assertThat(settings.secret()).isEqualTo("from-property");
    }

    @Test
    void aBlankSecretIsRefusedNamingTheEnvironmentVariable() {
        assertThatThrownBy(() -> ProxyForwardingSettings.from(Map.of(ENVIRONMENT_NAME, "   "), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ENVIRONMENT_NAME);
    }

    @Test
    void aBlankPropertyIsRefusedNamingTheProperty() {
        assertThatThrownBy(() -> ProxyForwardingSettings.from(Map.of(), Map.of(PROPERTY_NAME, "")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(PROPERTY_NAME);
    }

    @Test
    void withNoSecretTheServerRunsOffline() {
        ProxyForwardingSettings settings = ProxyForwardingSettings.from(Map.of(), Map.of());

        assertThat(settings.forwarding()).isFalse();
        assertThat(settings.secret()).isNull();
    }

    @Test
    void withASecretTheServerForwardsPlayers() {
        ProxyForwardingSettings settings = ProxyForwardingSettings.from(Map.of(ENVIRONMENT_NAME, "s3cret"), Map.of());

        assertThat(settings.forwarding()).isTrue();
    }

    @Test
    void theStringFormContainsNoPartOfTheSecret() {
        ProxyForwardingSettings settings = ProxyForwardingSettings.from(Map.of(ENVIRONMENT_NAME, "s3cret-value"), Map.of());

        assertThat(settings.toString()).doesNotContain("s3cret").doesNotContain("value");
    }
}
