package net.elytrarace.voyager.server;

import net.elytrarace.voyager.platform.proxy.ProxyForwardingSettings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the game server writes to its log about the forwarding secret. The secret's value must appear in no captured line,
 * whichever source it came from.
 */
class VoyagerServerForwardingLogTest {

    private static final String CANARY = "s3cret-canary-value";
    private static final String FILE_ENVIRONMENT = "VOYAGER_VELOCITY_SECRET_FILE";

    @TempDir
    Path directory;

    @Test
    void theBootLogsThePropertyWarningAndNoLineCarriesTheSecret() {
        try (LogCapture logs = LogCapture.of(VoyagerServer.class)) {
            VoyagerServer.logForwarding(ProxyForwardingSettings.from(Map.of(), Map.of("voyager.velocity.secret", CANARY)));

            assertThat(logs.warnings()).singleElement().satisfies(line -> assertThat(line).contains(FILE_ENVIRONMENT));
            assertThat(logs.warnings()).allSatisfy(line -> assertThat(line).doesNotContain(CANARY));
        }
    }

    @Test
    void theBootLogsNothingForAFileSecretAndNoLineCarriesItsContent() throws IOException {
        Path file = Files.writeString(directory.resolve("secret"), CANARY + "\n");

        try (LogCapture logs = LogCapture.of(VoyagerServer.class)) {
            VoyagerServer.logForwarding(ProxyForwardingSettings.from(Map.of(FILE_ENVIRONMENT, file.toString()), Map.of()));

            assertThat(logs.warnings()).isEmpty();
            assertThat(logs.infos()).allSatisfy(line -> assertThat(line).doesNotContain(CANARY));
        }
    }
}
