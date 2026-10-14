package net.elytrarace.voyager.server.config;

import net.elytrarace.voyager.api.config.ConfigProblem;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The persistence settings as the server reads them: from a map of the loader's keys, with every source handed in, so no
 * test reads the JVM's environment or system properties. Each test builds its own map and its own temporary directory.
 */
class PersistenceSettingsTest {

    private static final String SWITCH = "voyager.persistence";
    private static final String URL = "voyager.db.url";
    private static final String USER = "voyager.db.user";
    private static final String PASSWORD = "voyager.db.password";
    private static final String PASSWORD_FILE = "voyager.db.password.file";
    private static final String POOL_SIZE = "voyager.db.pool.size";
    private static final String URL_VALUE = "jdbc:postgresql://db.example.invalid:5432/voyager";

    @TempDir
    Path directory;

    @Test
    void unsetPersistenceIsOffWithOneWarning() {
        PersistenceSettings settings = PersistenceSettings.from(Map.of(), SOURCES);

        assertThat(settings.database().enabled()).isFalse();
        assertThat(settings.requested()).isFalse();
        assertThat(settings.warnings()).singleElement()
                .satisfies(problem -> assertThat(problem.message()).contains("no statistics are kept"));
    }

    @Test
    void offNeedsNoUrlAndReadsNoPasswordFile() {
        Map<String, String> values = Map.of(SWITCH, "off", PASSWORD_FILE, directory.resolve("missing").toString());

        PersistenceSettings settings = PersistenceSettings.from(values, SOURCES);

        assertThat(settings.database().enabled()).isFalse();
        assertThat(settings.warnings()).singleElement()
                .satisfies(problem -> assertThat(problem.message()).contains("no statistics are kept"));
    }

    @ParameterizedTest
    @ValueSource(strings = {URL, USER, PASSWORD})
    void onNeedsUrlUserAndPasswordAndNamesTheMissingOneInItsWarning(String missing) {
        Map<String, String> values = new HashMap<>(completeValues());
        values.remove(missing);

        PersistenceSettings settings = PersistenceSettings.from(values, SOURCES);

        assertThat(settings.database().enabled()).isFalse();
        assertThat(settings.requested()).isTrue();
        assertThat(settings.warnings()).singleElement()
                .satisfies(problem -> assertThat(problem.key()).isEqualTo(missing))
                .satisfies(problem -> assertThat(problem.message()).contains(environmentName(missing)))
                .satisfies(problem -> assertThat(problem.severity()).isEqualTo(ConfigProblem.Severity.WARNING));
    }

    @Test
    void theCompleteOnConfigurationIsEnabledWithoutAWarning() {
        PersistenceSettings settings = PersistenceSettings.from(completeValues(), SOURCES);

        assertThat(settings.database().enabled()).isTrue();
        assertThat(settings.warnings()).isEmpty();
    }

    @Test
    void thePasswordFileIsReadAndItsContentIsTheSecret() throws IOException {
        Path file = Files.writeString(directory.resolve("password"), "s3cret-value\n");
        Map<String, String> values = new HashMap<>(completeValues());
        values.remove(PASSWORD);
        values.put(PASSWORD_FILE, file.toString());

        PersistenceSettings settings = PersistenceSettings.from(values, SOURCES);

        assertThat(settings.database().enabled()).isTrue();
        assertThat(settings.database().password().value()).isEqualTo("s3cret-value");
        assertThat(settings.database().password().toString()).doesNotContain("s3cret-value");
    }

    @Test
    void aPasswordFileThatCannotBeReadIsOneWarningNamingTheFileAndStatsStayOff() {
        Path missing = directory.resolve("no-such-password");
        Map<String, String> values = new HashMap<>(completeValues());
        values.remove(PASSWORD);
        values.put(PASSWORD_FILE, missing.toString());

        PersistenceSettings settings = PersistenceSettings.from(values, SOURCES);

        assertThat(settings.database().enabled()).isFalse();
        assertThat(settings.warnings()).singleElement()
                .satisfies(problem -> assertThat(problem.key()).isEqualTo(PASSWORD_FILE))
                .satisfies(problem -> assertThat(problem.message()).contains("VOYAGER_DB_PASSWORD_FILE"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "21"})
    void aPoolSizeOutsideTwoToTwentyRunsWithStatsOffAndWarns(String poolSize) {
        Map<String, String> values = new HashMap<>(completeValues());
        values.put(POOL_SIZE, poolSize);

        PersistenceSettings settings = PersistenceSettings.from(values, SOURCES);

        assertThat(settings.database().enabled()).isFalse();
        assertThat(settings.warnings()).singleElement()
                .satisfies(problem -> assertThat(problem.key()).isEqualTo(POOL_SIZE))
                .satisfies(problem -> assertThat(problem.message()).contains("VOYAGER_DB_POOL_SIZE"));
    }

    @Test
    void anUnknownSwitchValueIsOneWarningNamingTheSwitchAndStatsStayOff() {
        PersistenceSettings settings = PersistenceSettings.from(Map.of(SWITCH, "yes"), SOURCES);

        assertThat(settings.database().enabled()).isFalse();
        assertThat(settings.requested()).isTrue();
        assertThat(settings.warnings()).singleElement()
                .satisfies(problem -> assertThat(problem.key()).isEqualTo(SWITCH))
                .satisfies(problem -> assertThat(problem.message()).contains("VOYAGER_PERSISTENCE"));
    }

    @Test
    void thePasswordIsNeverPrintedByTheSettings() {
        PersistenceSettings settings = PersistenceSettings.from(completeValues(), SOURCES);

        assertThat(settings.toString()).doesNotContain("s3cret-value");
    }

    /** A source label that names the key, so a test can tell which key a problem concerns. */
    private static final Function<String, String> SOURCES = key -> "test " + key;

    private static Map<String, String> completeValues() {
        return Map.of(SWITCH, "on", URL, URL_VALUE, USER, "voyager", PASSWORD, "s3cret-value", POOL_SIZE, "10");
    }

    private static String environmentName(String key) {
        return key.replace('.', '_').toUpperCase(Locale.ROOT);
    }
}
