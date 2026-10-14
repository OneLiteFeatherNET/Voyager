package net.elytrarace.voyager.server.config;

import net.elytrarace.voyager.api.config.ConfigProblem;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The database keys in the configuration check. A database problem is a warning that runs with statistics off; it is never
 * one of the problems that refuse the start, and it never reaches the settings check.
 */
class ConfigCheckPersistenceTest {

    @TempDir
    Path root;

    @Test
    void aDatabaseProblemNeverRefusesTheStart() throws IOException {
        Map<String, String> properties = directories();
        properties.put("voyager.persistence", "on");

        assertThat(ConfigCheck.settingsProblems(new String[0], properties)).isEmpty();
    }

    @Test
    void theCheckReportsAStatsProblemAsAWarningNamingTheSetting() throws IOException {
        Map<String, String> properties = directories();
        properties.put("voyager.persistence", "on");

        List<ConfigProblem> problems = ConfigCheck.persistenceProblems(properties, ConfigCheck.PROPERTY_SOURCE);

        assertThat(problems).singleElement()
                .satisfies(problem -> assertThat(problem.severity()).isEqualTo(ConfigProblem.Severity.WARNING))
                .satisfies(problem -> assertThat(problem.message()).contains("VOYAGER_DB_URL"));
    }

    @Test
    void theCheckSaysNothingAboutAnUnsetSwitch() throws IOException {
        assertThat(ConfigCheck.persistenceProblems(directories(), ConfigCheck.PROPERTY_SOURCE)).isEmpty();
    }

    private Map<String, String> directories() throws IOException {
        Path data = Files.createDirectories(root.resolve("data"));
        Path worlds = Files.createDirectories(root.resolve("worlds"));
        Map<String, String> properties = new HashMap<>();
        properties.put(ServerSettings.DATA_PATH_PROPERTY, data.toString());
        properties.put(ServerSettings.WORLDS_PATH_PROPERTY, worlds.toString());
        return properties;
    }
}
