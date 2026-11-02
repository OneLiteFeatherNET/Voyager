package net.elytrarace.voyager.platform.proxy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The WARN lines the composition roots log for the forwarding settings. No line may carry the secret's value. */
class ProxyForwardingWarningsTest {

    private static final String FILE_ENVIRONMENT = "VOYAGER_VELOCITY_SECRET_FILE";
    private static final String PROPERTY = "voyager.velocity.secret";
    private static final String CANARY = "s3cret-canary-value";

    @TempDir
    Path directory;

    @Test
    void aSecretFromThePropertyYieldsOneWarningThatNamesTheFileSettingAndNeverTheValue() {
        ProxyForwardingSettings settings = ProxyForwardingSettings.from(Map.of(), Map.of(PROPERTY, CANARY));

        assertThat(settings.warnings()).singleElement()
                .satisfies(line -> assertThat(line).contains(FILE_ENVIRONMENT).contains(PROPERTY))
                .satisfies(line -> assertThat(line).doesNotContain(CANARY));
    }

    @Test
    void aSecretFromAFileYieldsNoWarning() throws IOException {
        Path file = Files.writeString(directory.resolve("secret"), CANARY);

        ProxyForwardingSettings settings = ProxyForwardingSettings.from(Map.of(FILE_ENVIRONMENT, file.toString()), Map.of());

        assertThat(settings.warnings()).isEmpty();
    }

    @Test
    void aSecretFromTheEnvironmentYieldsNoWarning() {
        ProxyForwardingSettings settings = ProxyForwardingSettings.from(Map.of("VOYAGER_VELOCITY_SECRET", CANARY), Map.of());

        assertThat(settings.warnings()).isEmpty();
    }

    @Test
    void noSecretYieldsTheOfflineWarningThatNamesTheFileSetting() {
        ProxyForwardingSettings settings = ProxyForwardingSettings.from(Map.of(), Map.of());

        assertThat(settings.warnings()).singleElement()
                .satisfies(line -> assertThat(line).contains("No Velocity secret is set").contains(FILE_ENVIRONMENT));
    }
}
