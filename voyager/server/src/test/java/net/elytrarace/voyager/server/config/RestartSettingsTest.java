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
 * The restart key, read from a value map as the loader hands it over. Each test builds its own map and its own temporary
 * directories; none reads the environment or a system property.
 */
class RestartSettingsTest {

    private static final String RESTART = RestartSettings.PROPERTY;
    private static final String ROTATION = CupRotationSettings.ROTATION_PROPERTY;
    private static final String STATE = CupRotationSettings.STATE_PROPERTY;
    private static final String TWO_CUPS = "alpha_cup,legacy_cup";

    @TempDir
    Path root;

    @Test
    void theRestartIsOffWhenTheKeyIsUnsetOrBlank() {
        assertThat(RestartSettings.from(Map.of()).afterCup()).isFalse();
        assertThat(RestartSettings.from(Map.of(RESTART, "  ")).afterCup()).isFalse();
    }

    @Test
    void trueInAnyCaseTurnsTheRestartOnAndFalseKeepsItOff() {
        assertThat(RestartSettings.from(Map.of(RESTART, "true")).afterCup()).isTrue();
        assertThat(RestartSettings.from(Map.of(RESTART, "TRUE")).afterCup()).isTrue();
        assertThat(RestartSettings.from(Map.of(RESTART, "false")).afterCup()).isFalse();
    }

    @Test
    void anyOtherValueIsOneErrorThatNamesTheKey() {
        List<ConfigProblem> problems = RestartSettings.problems(Map.of(RESTART, "ture"), ConfigCheck.PROPERTY_SOURCE);

        assertThat(problems).singleElement().satisfies(problem -> {
            assertThat(problem.severity()).isEqualTo(Severity.ERROR);
            assertThat(problem.key()).isEqualTo(RESTART);
            assertThat(problem.message()).contains(RESTART).contains("true or false");
        });
    }

    @Test
    void theRestartWithTwoCupsAndNoStateFileIsOneErrorThatNamesBothKeys() {
        List<ConfigProblem> problems = RestartSettings.problems(
                Map.of(RESTART, "true", ROTATION, TWO_CUPS), ConfigCheck.PROPERTY_SOURCE);

        assertThat(problems).singleElement().satisfies(problem -> {
            assertThat(problem.severity()).isEqualTo(Severity.ERROR);
            assertThat(problem.key()).isEqualTo(RESTART);
            assertThat(problem.message()).contains(RESTART).contains(STATE);
        });
    }

    @Test
    void theRestartWithTwoCupsAndAStateFileInAnExistingDirectoryIsAccepted() {
        Path state = root.resolve("rotation");

        List<ConfigProblem> problems = RestartSettings.problems(
                Map.of(RESTART, "true", ROTATION, TWO_CUPS, STATE, state.toString()), ConfigCheck.PROPERTY_SOURCE);

        assertThat(problems).isEmpty();
    }

    @Test
    void theRestartWithOneCupNeedsNoStateFile() {
        List<ConfigProblem> problems = RestartSettings.problems(
                Map.of(RESTART, "true", ServerSettings.CUP_PROPERTY, "alpha_cup"), ConfigCheck.PROPERTY_SOURCE);

        assertThat(problems).isEmpty();
    }

    @Test
    void theOffRestartNeverRequiresAStateFile() {
        List<ConfigProblem> problems = RestartSettings.problems(
                Map.of(RESTART, "false", ROTATION, TWO_CUPS), ConfigCheck.PROPERTY_SOURCE);

        assertThat(problems).isEmpty();
    }

    @Test
    void theKeyIsListedWithTheServerKeysSoTheLoaderViewAndTheCheckReadIt() {
        assertThat(ServerSettings.KEYS).contains(RESTART);
    }

    @Test
    void theConfigurationCheckReportsTheRestartProblemsUnderTheSameKeys() throws Exception {
        Path data = Files.createDirectories(root.resolve("data"));
        Path worlds = Files.createDirectories(root.resolve("worlds"));
        Map<String, String> properties = Map.of(
                ServerSettings.DATA_PATH_PROPERTY, data.toString(),
                ServerSettings.WORLDS_PATH_PROPERTY, worlds.toString(),
                RESTART, "true",
                ROTATION, TWO_CUPS);

        List<ConfigProblem> problems = ConfigCheck.settingsProblems(new String[0], properties, ConfigCheck.PROPERTY_SOURCE);

        assertThat(problems).singleElement().satisfies(problem -> assertThat(problem.key()).isEqualTo(RESTART));
    }
}
