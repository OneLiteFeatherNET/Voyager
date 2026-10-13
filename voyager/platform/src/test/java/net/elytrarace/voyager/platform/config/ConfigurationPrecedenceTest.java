package net.elytrarace.voyager.platform.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static net.elytrarace.voyager.platform.config.ConfigProbe.ABSENT;
import static net.elytrarace.voyager.platform.config.ConfigProbe.LINE_PREFIX;
import static net.elytrarace.voyager.platform.config.ConfigProbe.VELOCITY;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The precedence of the real JVM: the environment, the system properties, the working directory and {@code CONFIG_FILE}.
 *
 * <p>Each test starts a child JVM with a cleared environment, its own working directory and its own arguments, because
 * the environment of a running JVM cannot be changed from Java and the system properties would leak between tests. The
 * child prints what {@link ConfigProbe} reads; nothing waits on a clock, and the result depends only on the inputs of
 * that test.
 */
class ConfigurationPrecedenceTest {

    @TempDir
    Path workingDirectory;

    @Test
    void aSystemPropertyBeatsTheEnvironment() throws Exception {
        Probed cup = probe(Map.of("VOYAGER_CUP", "alpha"), List.of("-DVOYAGER_CUP=beta"), "VOYAGER_CUP")
                .get("VOYAGER_CUP");

        assertThat(cup.value()).as("the cup the server plays").isEqualTo("beta");
        assertThat(cup.source()).isEqualTo("system property VOYAGER_CUP");
    }

    @Test
    void theEnvironmentBeatsAWorkingDirectoryFile() throws Exception {
        writeFile(workingDirectory.resolve("application.yaml"), "VOYAGER_MIN_PLAYERS: 4\n");

        Probed minimum = probe(Map.of("VOYAGER_MIN_PLAYERS", "2"), List.of(), "VOYAGER_MIN_PLAYERS")
                .get("VOYAGER_MIN_PLAYERS");

        assertThat(minimum.value()).as("the minimum racer count").isEqualTo("2");
        assertThat(minimum.source()).isEqualTo("environment variable VOYAGER_MIN_PLAYERS");
    }

    @Test
    void aWorkingDirectoryFileSetsAValueWhenNothingElseDoes() throws Exception {
        writeFile(workingDirectory.resolve("application.yaml"), "VOYAGER_MAX_PLAYERS: 12\n");

        Probed maximum = probe(Map.of(), List.of(), "VOYAGER_MAX_PLAYERS").get("VOYAGER_MAX_PLAYERS");

        assertThat(maximum.value()).as("the maximum racer count").isEqualTo("12");
        assertThat(maximum.source()).contains("application.yaml");
    }

    @Test
    void theFileNamedByConfigFileBeatsTheWorkingDirectoryFile() throws Exception {
        writeFile(workingDirectory.resolve("application.yaml"), "VOYAGER_DATA_PATH: from-working-directory\n");
        Path configFile = writeFile(workingDirectory.resolve("extra.properties"),
                "VOYAGER_DATA_PATH=from-config-file\n");

        Probed data = probe(Map.of("CONFIG_FILE", configFile.toString()), List.of(), "VOYAGER_DATA_PATH")
                .get("VOYAGER_DATA_PATH");

        assertThat(data.value()).as("the data directory").isEqualTo("from-config-file");
    }

    @Test
    void aNewEnvironmentNameReachesTheBindPort() throws Exception {
        Probed port = probe(Map.of("SERVICE_BIND_PORT", "25570"), List.of(), "service.bind.port")
                .get("service.bind.port");

        assertThat(port.value()).as("the bind port read from the environment").isEqualTo("25570");
        assertThat(port.source()).isEqualTo("environment variable SERVICE_BIND_PORT");
    }

    @Test
    void theVelocitySecretTakesTheEnvironmentBeforeTheProperty() throws Exception {
        Probed secret = probe(Map.of("VOYAGER_VELOCITY_SECRET", "from-environment"),
                List.of("-Dvoyager.velocity.secret=from-property"), VELOCITY).get(VELOCITY);

        assertThat(secret.value()).as("the forwarding secret").isEqualTo("from-environment");
    }

    @Test
    void anAbsentKeyIsReportedAsAbsent() throws Exception {
        Probed cup = probe(Map.of(), List.of(), "VOYAGER_CUP").get("VOYAGER_CUP");

        assertThat(cup.value()).isEqualTo(ABSENT);
    }

    private Map<String, Probed> probe(Map<String, String> environment, List<String> properties, String... keys)
            throws IOException, InterruptedException {
        String javaBinary = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        List<String> command = new ArrayList<>();
        command.add(javaBinary);
        command.addAll(properties);
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(ConfigProbe.class.getName());
        command.addAll(List.of(keys));

        ProcessBuilder builder = new ProcessBuilder(command).directory(workingDirectory.toFile());
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.environment().clear();
        builder.environment().putAll(environment);
        Process process = builder.start();
        String stdout = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        boolean finished = process.waitFor(2, TimeUnit.MINUTES);
        assertThat(finished).as("the probe JVM finishes").isTrue();
        assertThat(process.exitValue()).as("the probe JVM exits cleanly").isZero();

        Map<String, Probed> read = new HashMap<>();
        stdout.lines()
                .filter(line -> line.startsWith(LINE_PREFIX))
                .map(line -> line.substring(LINE_PREFIX.length()).split("\t", -1))
                .forEach(fields -> read.put(fields[0], new Probed(fields[1], fields[2])));
        return read;
    }

    private static Path writeFile(Path file, String content) throws IOException {
        return Files.writeString(file, content);
    }

    /** What the probe printed for one key: the value, or {@link ConfigProbe#ABSENT}, and where it came from. */
    private record Probed(String value, String source) {
    }
}
