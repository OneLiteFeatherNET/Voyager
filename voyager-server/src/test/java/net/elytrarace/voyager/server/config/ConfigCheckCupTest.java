package net.elytrarace.voyager.server.config;

import net.elytrarace.voyager.api.config.ConfigProblem;
import net.elytrarace.voyager.api.config.ConfigProblem.Severity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The cup-selection check: a named cup must exist, and an unnamed one needs no check.
 */
class ConfigCheckCupTest {

    @TempDir
    Path data;

    @Test
    void reportsAMissingCupNamedBySystemPropertyWithTheSettingAsKey() throws IOException {
        writeCup("real");
        ServerSettings settings = settingsNaming("Missing");

        List<ConfigProblem> problems = ConfigCheck.cupSelectionProblems(settings);

        assertThat(problems).singleElement().satisfies(problem -> {
            assertThat(problem.key()).isEqualTo(ServerSettings.CUP_PROPERTY);
            assertThat(problem.severity()).isEqualTo(Severity.ERROR);
            assertThat(problem.message()).contains("'Missing'").contains("real");
        });
    }

    @Test
    void reportsNothingWhenNoCupIsNamed() throws IOException {
        writeCup("real");

        assertThat(ConfigCheck.cupSelectionProblems(settingsNaming(null))).isEmpty();
    }

    @Test
    void reportsNothingWhenTheNamedCupExists() throws IOException {
        writeCup("real");

        assertThat(ConfigCheck.cupSelectionProblems(settingsNaming("real"))).isEmpty();
    }

    private void writeCup(String name) throws IOException {
        Path cups = Files.createDirectories(data.resolve("cups"));
        Files.writeString(cups.resolve(name + ".json"), """
                {"name": "%s", "mode": "RACE", "mapNames": ["blue"]}
                """.formatted(name));
    }

    private ServerSettings settingsNaming(String cup) {
        Path worlds = data.resolve("worlds");
        try {
            Files.createDirectories(worlds);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
        return new ServerSettings("127.0.0.1", 25571, data, worlds, Optional.ofNullable(cup), false);
    }
}
