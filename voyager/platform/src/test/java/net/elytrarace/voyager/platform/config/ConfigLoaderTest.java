package net.elytrarace.voyager.platform.config;

import io.avaje.config.Configuration;

import net.elytrarace.voyager.platform.proxy.ProxyForwardingSettings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The loader's behaviour with its sources handed in. The environment is a map here, because a JVM cannot set its own
 * environment; the precedence that depends on the real environment and the real system properties is in
 * {@link ConfigurationPrecedenceTest}, which runs a child JVM. Keys are probe names, so a variable exported on a
 * developer machine cannot change a result.
 */
class ConfigLoaderTest {

    private static final String PROBE_KEY = "voyager.probe.setting";

    @TempDir
    Path workingDirectory;

    @Test
    void anAbsentKeyIsAbsentNotEmpty() {
        ConfigLoader loader = ConfigLoader.of(Configuration.builder().build(), Map.of());

        assertThat(loader.get("voyager.probe.absent")).isNull();
    }

    @Test
    void aWorkingDirectoryFileSetsAValueWhenNothingElseDoes() throws IOException {
        Path file = Files.writeString(workingDirectory.resolve("application.yaml"), PROBE_KEY + ": from-file\n");
        ConfigLoader loader = ConfigLoader.of(Configuration.builder().load(file.toFile()).build(), Map.of());

        assertThat(loader.get(PROBE_KEY)).isEqualTo("from-file");
    }

    @Test
    void theViewHoldsOnlyTheKeysThatAreSet() {
        ConfigLoader loader = ConfigLoader.of(
                Configuration.builder().put(PROBE_KEY, "set").build(), Map.of());

        assertThat(loader.view(List.of(PROBE_KEY, "voyager.probe.unset")))
                .containsExactlyEntriesOf(Map.of(PROBE_KEY, "set"));
    }

    @Test
    void theVelocitySecretTakesTheEnvironmentBeforeTheProperty() {
        ConfigLoader loader = ConfigLoader.of(
                Configuration.builder().put(ProxyForwardingSettings.PROPERTY_NAME, "from-property").build(),
                Map.of(ProxyForwardingSettings.ENVIRONMENT_NAME, "from-environment"));

        assertThat(ProxyForwardingSettings.fromLoader(loader).secret()).isEqualTo("from-environment");
    }

    @Test
    void aBlankEnvironmentSecretIsRefusedWithoutPrintingIt() {
        ConfigLoader loader = ConfigLoader.of(
                Configuration.builder().build(),
                Map.of(ProxyForwardingSettings.ENVIRONMENT_NAME, "   "));

        assertThatThrownBy(() -> ProxyForwardingSettings.fromLoader(loader))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ProxyForwardingSettings.ENVIRONMENT_NAME)
                .hasMessageNotContaining("   ");
    }
}
