package net.elytrarace.voyager.api.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseConfigTest {

    private static final Map<String, String> VALID = Map.of(
            "VOYAGER_PERSISTENCE", "on",
            "VOYAGER_DB_URL", "jdbc:postgresql://db.example.invalid:5432/voyager",
            "VOYAGER_DB_USER", "voyager",
            "VOYAGER_DB_PASSWORD", "hunter2");

    @Test
    @DisplayName("an on database without a URL logs one WARN, names the URL and runs with stats off")
    void anOnDatabaseWithoutAUrlWarnsNamingTheUrlAndRunsWithStatsOff() {
        Map<String, String> settings = settingsWithout("VOYAGER_DB_URL");

        DatabaseConfig config = DatabaseConfig.from(settings);

        assertThat(config.enabled())
                .as("statistics are off when the URL is missing")
                .isFalse();
        assertThat(config.warning())
                .as("the single warning names the URL")
                .isNotNull()
                .contains("VOYAGER_DB_URL");
    }

    @Test
    @DisplayName("a secret prints as masked")
    void aSecretPrintsAsMasked() {
        DatabaseConfig config = DatabaseConfig.from(VALID);

        assertThat(config.enabled())
                .as("the valid settings enable the database")
                .isTrue();
        assertThat(new Secret("hunter2").toString())
                .as("the secret is masked wherever it is printed")
                .isEqualTo("Secret[masked]");
        assertThat(config.toString())
                .as("the password never appears in the configuration's text")
                .doesNotContain("hunter2");
    }

    @Test
    @DisplayName("a pool size outside two to twenty logs a WARN and the config is reported with stats off")
    void aPoolSizeOutsideTwoToTwentyWarnsAndRunsWithStatsOff() {
        for (String size : List.of("1", "21")) {
            Map<String, String> settings = settingsWith("VOYAGER_DB_POOL_SIZE", size);

            DatabaseConfig config = DatabaseConfig.from(settings);

            assertThat(config.enabled())
                    .as("pool size %s is refused and statistics are off", size)
                    .isFalse();
            assertThat(config.warning())
                    .as("the warning names the pool size setting for %s", size)
                    .isNotNull()
                    .contains("VOYAGER_DB_POOL_SIZE");
        }
    }

    private static Map<String, String> settingsWithout(String key) {
        Map<String, String> settings = new HashMap<>(VALID);
        settings.remove(key);
        return settings;
    }

    private static Map<String, String> settingsWith(String key, String value) {
        Map<String, String> settings = new HashMap<>(VALID);
        settings.put(key, value);
        return settings;
    }
}
