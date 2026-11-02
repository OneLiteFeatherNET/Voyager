package net.elytrarace.voyager.server.config;

import net.elytrarace.voyager.api.config.ConfigProblem;
import net.elytrarace.voyager.api.config.ConfigProblem.Severity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two rotation keys, read from a value map as the loader hands them over. No test reads the environment or a system
 * property; each builds its own map and, where a directory matters, its own temporary one.
 */
class CupRotationSettingsTest {

    private static final String ROTATION = CupRotationSettings.ROTATION_PROPERTY;
    private static final String STATE = CupRotationSettings.STATE_PROPERTY;

    @TempDir
    Path root;

    @Test
    void theRotationIsTheListedCupsInOrderWithBlankEntriesAndSpacesDropped() {
        CupRotationSettings settings = CupRotationSettings.from(Map.of(ROTATION, " alpha_cup, ,legacy_cup ,"));

        assertThat(settings.cups()).containsExactly("alpha_cup", "legacy_cup");
        assertThat(settings.configured()).isTrue();
    }

    @Test
    void noRotationKeyMeansNoRotation() {
        CupRotationSettings settings = CupRotationSettings.from(Map.of());

        assertThat(settings.configured()).isFalse();
        assertThat(settings.stateFile()).isEmpty();
    }

    @Test
    void settingBothTheRotationAndVoyagerCupRefusesBootAndNamesBothKeys() {
        List<ConfigProblem> problems = CupRotationSettings.problems(
                Map.of(ServerSettings.CUP_PROPERTY, "alpha_cup", ROTATION, "alpha_cup,legacy_cup"),
                ConfigCheck.PROPERTY_SOURCE);

        assertThat(problems).singleElement().satisfies(problem -> {
            assertThat(problem.severity()).isEqualTo(Severity.ERROR);
            assertThat(problem.key()).isEqualTo(ROTATION);
            assertThat(problem.message()).contains(ServerSettings.CUP_PROPERTY).contains(ROTATION);
        });
    }

    @Test
    void aRotationThatNamesNoCupIsRefusedUnderItsKey() {
        List<ConfigProblem> problems = CupRotationSettings.problems(Map.of(ROTATION, " , "),
                ConfigCheck.PROPERTY_SOURCE);

        assertThat(problems).singleElement().satisfies(problem -> {
            assertThat(problem.severity()).isEqualTo(Severity.ERROR);
            assertThat(problem.key()).isEqualTo(ROTATION);
        });
    }

    @Test
    void aStateFileWhoseDirectoryIsMissingIsOneErrorThatNamesTheKeyAndThePath() {
        Path missing = root.resolve("no-such-directory").resolve("rotation");

        List<ConfigProblem> problems = CupRotationSettings.problems(
                Map.of(ROTATION, "alpha_cup,legacy_cup", STATE, missing.toString()),
                ConfigCheck.PROPERTY_SOURCE);

        assertThat(problems).singleElement().satisfies(problem -> {
            assertThat(problem.severity()).isEqualTo(Severity.ERROR);
            assertThat(problem.key()).isEqualTo(STATE);
            assertThat(problem.message()).contains(missing.getParent().toString());
        });
    }

    @Test
    void aStateFileInAnExistingDirectoryIsNoProblem() {
        Path state = root.resolve("rotation");

        List<ConfigProblem> problems = CupRotationSettings.problems(
                Map.of(ROTATION, "alpha_cup,legacy_cup", STATE, state.toString()),
                ConfigCheck.PROPERTY_SOURCE);

        assertThat(problems).isEmpty();
        assertThat(CupRotationSettings.from(Map.of(ROTATION, "alpha_cup,legacy_cup", STATE, state.toString()))
                .stateFile()).contains(state);
    }

    @Test
    void aStateFileWithoutARotationIsAWarningThatItIsNotUsed() {
        List<ConfigProblem> problems = CupRotationSettings.problems(
                Map.of(STATE, root.resolve("rotation").toString()), ConfigCheck.PROPERTY_SOURCE);

        assertThat(problems).singleElement().satisfies(problem -> assertThat(problem.severity())
                .isEqualTo(Severity.WARNING));
    }

    @Test
    void theRotationKeysAreReadWithTheOtherSettings() {
        assertThat(ServerSettings.KEYS).contains(ROTATION, STATE);
    }
}
