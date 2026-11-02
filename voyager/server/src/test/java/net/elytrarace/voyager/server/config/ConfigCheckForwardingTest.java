package net.elytrarace.voyager.server.config;

import net.elytrarace.voyager.api.config.ConfigProblem;
import net.elytrarace.voyager.api.config.ConfigProblem.Severity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The forwarding secret in the configuration check. Each test passes its sources as maps and reads the report from a buffer,
 * so the check's output is what an operator would see, and no test touches the JVM's environment or properties.
 */
class ConfigCheckForwardingTest {

    private static final String FILE_ENVIRONMENT = "VOYAGER_VELOCITY_SECRET_FILE";
    private static final String PROPERTY = "voyager.velocity.secret";
    private static final String CANARY = "s3cret-canary-value";

    @TempDir
    Path directory;

    @Test
    void aSecretFromThePropertyIsOneWarningAndTheRunStillExitsZero() {
        List<ConfigProblem> problems = ConfigCheck.forwardingProblems(Map.of(), Map.of(PROPERTY, CANARY),
                ConfigCheck.PROPERTY_SOURCE);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        int exit = ConfigCheck.report(problems, new PrintStream(buffer, true, StandardCharsets.UTF_8));

        assertThat(problems).singleElement()
                .satisfies(problem -> assertThat(problem.severity()).isEqualTo(Severity.WARNING))
                .satisfies(problem -> assertThat(problem.key()).isEqualTo(PROPERTY))
                .satisfies(problem -> assertThat(problem.message()).contains(FILE_ENVIRONMENT));
        assertThat(exit).isZero();
        assertThat(output(buffer)).doesNotContain(CANARY);
    }

    @Test
    void anUnreadableSecretFileIsOneErrorUnderTheFileSettingAndTheRunExitsOne() {
        Path missing = directory.resolve("no-such-secret");
        List<ConfigProblem> problems = ConfigCheck.forwardingProblems(Map.of(FILE_ENVIRONMENT, missing.toString()), Map.of(),
                ConfigCheck.PROPERTY_SOURCE);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        int exit = ConfigCheck.report(problems, new PrintStream(buffer, true, StandardCharsets.UTF_8));

        assertThat(problems).singleElement()
                .satisfies(problem -> assertThat(problem.severity()).isEqualTo(Severity.ERROR))
                .satisfies(problem -> assertThat(problem.key()).isEqualTo(FILE_ENVIRONMENT))
                .satisfies(problem -> assertThat(problem.message()).contains(missing.toString()));
        assertThat(exit).isEqualTo(1);
    }

    @Test
    void theReportForABrokenFileHoldsNoPartOfItsContent() throws IOException {
        Path file = directory.resolve("secret");
        Files.write(file, new byte[] {'s', '3', 'c', 'r', 'e', 't', '-', 'c', 'a', 'n', 'a', 'r', 'y', (byte) 0xFF});
        List<ConfigProblem> problems = ConfigCheck.forwardingProblems(Map.of(FILE_ENVIRONMENT, file.toString()), Map.of(),
                ConfigCheck.PROPERTY_SOURCE);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        ConfigCheck.report(problems, new PrintStream(buffer, true, StandardCharsets.UTF_8));

        assertThat(output(buffer)).doesNotContain("canary").doesNotContain("s3cret");
    }

    @Test
    void aSecretFromAFileProducesNoForwardingProblem() throws IOException {
        Path file = Files.writeString(directory.resolve("secret"), CANARY);

        assertThat(ConfigCheck.forwardingProblems(Map.of(FILE_ENVIRONMENT, file.toString()), Map.of(),
                ConfigCheck.PROPERTY_SOURCE)).isEmpty();
    }

    private static String output(ByteArrayOutputStream buffer) {
        return buffer.toString(StandardCharsets.UTF_8);
    }
}
