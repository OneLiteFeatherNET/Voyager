package net.elytrarace.voyager.server;

import net.elytrarace.voyager.api.config.ConfigProblem;
import net.elytrarace.voyager.api.config.ConfigProblem.Severity;
import net.elytrarace.voyager.server.config.ConfigCheck;
import net.elytrarace.voyager.server.config.ServerSettings;

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
 * The hologram setting in the configuration check: no hologram is no problem, a hologram without a lobby is an error that names
 * both settings, and a hologram that is not three numbers is an error that names the setting. Plain JUnit, because the settings
 * check runs before Minestom is initialised.
 */
class ConfigCheckHologramTest {

    @TempDir
    Path root;

    @Test
    void noHologramIsNoProblem() throws IOException {
        List<ConfigProblem> problems = ConfigCheck.settingsProblems(new String[0], properties(Map.of(
                ServerSettings.LOBBY_WORLD_PROPERTY, "ElytraLobby",
                ServerSettings.LOBBY_SPAWN_PROPERTY, "0,64,0")));

        assertThat(problems).noneMatch(problem -> problem.key().equals(ServerSettings.LOBBY_HOLOGRAM_PROPERTY));
    }

    @Test
    void aHologramWithoutALobbyIsAnErrorThatNamesBothSettings() throws IOException {
        List<ConfigProblem> problems = ConfigCheck.settingsProblems(new String[0], properties(Map.of(
                ServerSettings.LOBBY_HOLOGRAM_PROPERTY, "0,70,0")));

        assertThat(problems).anySatisfy(problem -> {
            assertThat(problem.severity()).isEqualTo(Severity.ERROR);
            assertThat(problem.key()).isEqualTo(ServerSettings.LOBBY_HOLOGRAM_PROPERTY);
            assertThat(problem.format()).contains(ServerSettings.LOBBY_HOLOGRAM_PROPERTY)
                    .contains(ServerSettings.LOBBY_WORLD_PROPERTY);
        });
    }

    @Test
    void aHologramOfTwoNumbersIsAnErrorThatNamesTheSetting() throws IOException {
        List<ConfigProblem> problems = ConfigCheck.settingsProblems(new String[0], properties(Map.of(
                ServerSettings.LOBBY_WORLD_PROPERTY, "ElytraLobby",
                ServerSettings.LOBBY_SPAWN_PROPERTY, "0,64,0",
                ServerSettings.LOBBY_HOLOGRAM_PROPERTY, "0,70")));

        assertThat(problems).anySatisfy(problem -> {
            assertThat(problem.severity()).isEqualTo(Severity.ERROR);
            assertThat(problem.key()).isEqualTo(ServerSettings.LOBBY_HOLOGRAM_PROPERTY);
            assertThat(problem.format()).contains(ServerSettings.LOBBY_HOLOGRAM_PROPERTY);
        });
    }

    private Map<String, String> properties(Map<String, String> extra) throws IOException {
        Path data = Files.createDirectories(root.resolve("catalogue"));
        Path worlds = Files.createDirectories(root.resolve("racetracks"));
        Map<String, String> properties = new HashMap<>(Map.of(
                ServerSettings.DATA_PATH_PROPERTY, data.toString(),
                ServerSettings.WORLDS_PATH_PROPERTY, worlds.toString()));
        properties.putAll(extra);
        return properties;
    }
}
