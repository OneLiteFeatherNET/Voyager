package net.elytrarace.voyager.server.config;

import net.elytrarace.voyager.api.config.ConfigProblem;
import net.elytrarace.voyager.api.config.ConfigProblem.Severity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The settings half of the configuration check: every bad setting is reported, none is thrown.
 * Each test passes its own properties map, so nothing reads the JVM's system properties.
 */
class ConfigCheckSettingsTest {

    @TempDir
    Path root;

    @Test
    void reportsEveryMissingDirectoryWithItsAbsolutePathInsteadOfStoppingAtTheFirst() {
        Path data = root.resolve("no-catalogue");
        Path worlds = root.resolve("no-racetracks");

        List<ConfigProblem> problems = ConfigCheck.settingsProblems(new String[0], Map.of(
                ServerSettings.DATA_PATH_PROPERTY, data.toString(),
                ServerSettings.WORLDS_PATH_PROPERTY, worlds.toString()));

        assertThat(problems).hasSize(2);
        assertThat(problems).allSatisfy(problem -> assertThat(problem.severity()).isEqualTo(Severity.ERROR));
        assertThat(problems.get(0).source()).isEqualTo(data.toAbsolutePath().toString());
        assertThat(problems.get(0).key()).isEqualTo(ServerSettings.DATA_PATH_PROPERTY);
        assertThat(problems.get(1).source()).isEqualTo(worlds.toAbsolutePath().toString());
        assertThat(problems.get(1).key()).isEqualTo(ServerSettings.WORLDS_PATH_PROPERTY);
    }

    @Test
    void reportsANonNumericPortWithTheKeyPort() throws Exception {
        List<ConfigProblem> problems = ConfigCheck.settingsProblems(
                new String[] {"0.0.0.0", "abc"}, validDirectories());

        assertThat(problems).singleElement().satisfies(problem -> {
            assertThat(problem.key()).isEqualTo("port");
            assertThat(problem.message()).contains("abc");
        });
    }

    @Test
    void reportsAPortOutOfRange() throws Exception {
        List<ConfigProblem> problems = ConfigCheck.settingsProblems(
                new String[] {"0.0.0.0", "70000"}, validDirectories());

        assertThat(problems).singleElement().satisfies(problem -> {
            assertThat(problem.key()).isEqualTo("port");
            assertThat(problem.message()).contains("70000");
        });
    }

    @Test
    void reportsNothingForValidSettings() throws Exception {
        assertThat(ConfigCheck.settingsProblems(new String[] {"0.0.0.0", "25571"}, validDirectories())).isEmpty();
    }

    /** Two real directories under the temp root, so only the settings under test can be wrong. */
    private Map<String, String> validDirectories() throws Exception {
        Path data = Files.createDirectories(root.resolve("catalogue"));
        Path worlds = Files.createDirectories(root.resolve("racetracks"));
        return Map.of(ServerSettings.DATA_PATH_PROPERTY, data.toString(),
                ServerSettings.WORLDS_PATH_PROPERTY, worlds.toString());
    }
}
