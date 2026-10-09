package net.elytrarace.voyager.server.config;

import net.elytrarace.voyager.race.flow.RaceTimings;
import net.elytrarace.voyager.server.config.exception.MissingServerDirectoryException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Configuration resolution, which is the one part of a composition root that carries decisions
 * rather than wiring.
 *
 * <p>The fixture never uses the same value twice where the code could confuse two of them: the data
 * and worlds directories are different names under the temp root, the host is not the default host,
 * and the port is not the default port. A settings object that read the worlds path into the data
 * slot, or ignored an argument and took a default, would otherwise still line up.
 */
class ServerSettingsTest {

    @TempDir
    Path root;

    private Path data;
    private Path worlds;

    private Path data() {
        if (data == null) {
            data = createDirectory("catalogue");
        }
        return data;
    }

    private Path worlds() {
        if (worlds == null) {
            worlds = createDirectory("racetracks");
        }
        return worlds;
    }

    private Path createDirectory(String name) {
        try {
            return Files.createDirectory(root.resolve(name));
        } catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }

    /**
     * {@code fromEnvironment} reads real system properties, so every test that sets one has to put
     * the JVM back the way it found it — the whole module's tests share one.
     */
    @AfterEach
    void clearProperties() {
        System.clearProperty(ServerSettings.DATA_PATH_PROPERTY);
        System.clearProperty(ServerSettings.WORLDS_PATH_PROPERTY);
        System.clearProperty(ServerSettings.CUP_PROPERTY);
        System.clearProperty(ServerSettings.DEV_MODE_PROPERTY);
    }

    // ------------------------------------------------------------------------------------------
    // The two directories
    // ------------------------------------------------------------------------------------------

    @Test
    void refusesADataDirectoryThatIsNotThereAndNamesTheAbsolutePath() {
        Path missing = root.resolve("no-catalogue-here");

        assertThatThrownBy(() -> new ServerSettings("0.0.0.0", 25565, missing, worlds(), Optional.empty(), false))
                .isInstanceOf(MissingServerDirectoryException.class)
                .hasMessageContaining(missing.toAbsolutePath().toString())
                .hasMessageContaining(MissingServerDirectoryException.DATA)
                .hasMessageContaining("VOYAGER_DATA_PATH");
    }

    @Test
    void refusesAWorldsDirectoryThatIsNotThereAndNamesTheAbsolutePath() {
        Path missing = root.resolve("no-racetracks-here");

        assertThatThrownBy(() -> new ServerSettings("0.0.0.0", 25565, data(), missing, Optional.empty(), false))
                .isInstanceOf(MissingServerDirectoryException.class)
                .hasMessageContaining(missing.toAbsolutePath().toString())
                .hasMessageContaining(MissingServerDirectoryException.WORLDS);
    }

    /**
     * A file is not a directory. The catalogue reads a directory of {@code .json} definitions, so a
     * path pointing at one of those definitions rather than at the folder holding them is the
     * plausible mistake this rules out — {@code Files.exists} would accept it.
     */
    @Test
    void refusesAPathThatExistsButIsAFile() throws java.io.IOException {
        Path file = Files.writeString(root.resolve("maps.json"), "{}");

        assertThatThrownBy(() -> new ServerSettings("0.0.0.0", 25565, file, worlds(), Optional.empty(), false))
                .isInstanceOf(MissingServerDirectoryException.class)
                .hasMessageContaining(file.toAbsolutePath().toString());
    }

    @Test
    void reportsWhichDirectoryWasMissingOnTheException() {
        Path missing = root.resolve("gone");

        MissingServerDirectoryException thrown = org.assertj.core.api.Assertions.catchThrowableOfType(
                MissingServerDirectoryException.class,
                () -> new ServerSettings("0.0.0.0", 25565, data(), missing, Optional.empty(), false));

        assertThat(thrown.purpose()).isEqualTo(MissingServerDirectoryException.WORLDS);
        assertThat(thrown.path()).isEqualTo(missing);
    }

    // ------------------------------------------------------------------------------------------
    // The command line and the properties
    // ------------------------------------------------------------------------------------------

    @Test
    void takesTheHostAndPortFromTheArguments() {
        System.setProperty(ServerSettings.DATA_PATH_PROPERTY, data().toString());
        System.setProperty(ServerSettings.WORLDS_PATH_PROPERTY, worlds().toString());

        ServerSettings settings = ServerSettings.fromEnvironment(new String[] {"127.0.0.1", "25599"});

        assertThat(settings.host()).isEqualTo("127.0.0.1");
        assertThat(settings.port()).isEqualTo(25599);
    }

    @Test
    void defaultsTheHostAndPortWhenNoArgumentsAreGiven() {
        System.setProperty(ServerSettings.DATA_PATH_PROPERTY, data().toString());
        System.setProperty(ServerSettings.WORLDS_PATH_PROPERTY, worlds().toString());

        ServerSettings settings = ServerSettings.fromEnvironment(new String[0]);

        assertThat(settings.host()).isEqualTo("0.0.0.0");
        assertThat(settings.port()).isEqualTo(25565);
    }

    /**
     * A port that is not a number is a refusal, not a warning and a fallback. The tree being replaced
     * logs and binds 25565 instead, which is a server two people are about to fight over.
     */
    @Test
    void refusesAPortThatIsNotANumberRatherThanFallingBackToTheDefault() {
        System.setProperty(ServerSettings.DATA_PATH_PROPERTY, data().toString());
        System.setProperty(ServerSettings.WORLDS_PATH_PROPERTY, worlds().toString());

        assertThatThrownBy(() -> ServerSettings.fromEnvironment(new String[] {"127.0.0.1", "twenty-five-thousand"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("twenty-five-thousand");
    }

    @Test
    void refusesAPortOutsideTheRange() {
        assertThatThrownBy(() -> new ServerSettings("0.0.0.0", 70000, data(), worlds(), Optional.empty(), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("70000");
    }

    @Test
    void readsTheDataAndWorldsPathsFromTheirOwnProperties() {
        System.setProperty(ServerSettings.DATA_PATH_PROPERTY, data().toString());
        System.setProperty(ServerSettings.WORLDS_PATH_PROPERTY, worlds().toString());

        ServerSettings settings = ServerSettings.fromEnvironment(new String[0]);

        assertThat(settings.dataPath()).isEqualTo(data());
        assertThat(settings.worldsPath()).isEqualTo(worlds());
    }

    @Test
    void readsTheChosenCupFromItsProperty() {
        System.setProperty(ServerSettings.DATA_PATH_PROPERTY, data().toString());
        System.setProperty(ServerSettings.WORLDS_PATH_PROPERTY, worlds().toString());
        System.setProperty(ServerSettings.CUP_PROPERTY, "winter_series");

        assertThat(ServerSettings.fromEnvironment(new String[0]).cupName()).contains("winter_series");
    }

    /**
     * An empty property is the same as no property. {@code -DVOYAGER_CUP=} is what a shell script
     * with an unset variable produces, and a cup named "" resolves to nothing at all.
     */
    @Test
    void treatsABlankCupPropertyAsNoChoiceRatherThanACupNamedNothing() {
        System.setProperty(ServerSettings.DATA_PATH_PROPERTY, data().toString());
        System.setProperty(ServerSettings.WORLDS_PATH_PROPERTY, worlds().toString());
        System.setProperty(ServerSettings.CUP_PROPERTY, "   ");

        assertThat(ServerSettings.fromEnvironment(new String[0]).cupName()).isEmpty();
    }

    @Test
    void readsDevModeFromItsProperty() {
        System.setProperty(ServerSettings.DATA_PATH_PROPERTY, data().toString());
        System.setProperty(ServerSettings.WORLDS_PATH_PROPERTY, worlds().toString());
        System.setProperty(ServerSettings.DEV_MODE_PROPERTY, "true");

        assertThat(ServerSettings.fromEnvironment(new String[0]).devMode()).isTrue();
    }

    // ------------------------------------------------------------------------------------------
    // Timings
    // ------------------------------------------------------------------------------------------

    @Test
    void aProductionRunUsesTheSharedDefaultTimings() {
        ServerSettings settings = new ServerSettings("0.0.0.0", 25565, data(), worlds(), Optional.empty(), false);

        assertThat(settings.timings()).isEqualTo(RaceTimings.DEFAULT);
    }

    /**
     * Dev mode shortens the lobby and <em>both</em> results screens and leaves the race length
     * alone. Both, because one of them being shortened is not evidence about the other and a dev run
     * that still sat through twenty seconds after the last map would be the wait this flag exists to
     * remove. That last
     * part is the assertion worth having: a shortened race length is not a faster test, it is a
     * different one — every {@code DNF} is scored on the phase length, and the committed course has a
     * 60 s reference time that a 30 s phase could never reach.
     */
    @Test
    void aDevRunShortensTheLobbyAndTheResultsScreenButNotTheRace() {
        ServerSettings dev = new ServerSettings("0.0.0.0", 25565, data(), worlds(), Optional.empty(), true);

        assertThat(dev.timings().lobby()).isLessThan(RaceTimings.DEFAULT.lobby());
        assertThat(dev.timings().endBetweenMaps()).isLessThan(RaceTimings.DEFAULT.endBetweenMaps());
        assertThat(dev.timings().endAfterLastMap()).isLessThan(RaceTimings.DEFAULT.endAfterLastMap());
        assertThat(dev.timings().race()).isEqualTo(RaceTimings.DEFAULT.race());
    }

    @Test
    void describesEveryResolvedValueWithAbsolutePaths() {
        ServerSettings settings = new ServerSettings("127.0.0.1", 25599, data(), worlds(), Optional.of("cup_x"), true);

        assertThat(settings.describe())
                .contains("127.0.0.1")
                .contains("25599")
                .contains(data().toAbsolutePath().toString())
                .contains(worlds().toAbsolutePath().toString())
                .contains("cup_x")
                .contains("dev=true");
    }
}
