package net.elytrarace.api.database.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class DatabaseConfigTest {

    @AfterEach
    void cleanSystemProps() {
        System.clearProperty("VOYAGER_DB_URL");
        System.clearProperty("VOYAGER_DB_USER");
        System.clearProperty("VOYAGER_DB_PASSWORD");
        System.clearProperty("VOYAGER_DB_POOL_SIZE");
        System.clearProperty("VOYAGER_DB_HBM2DDL");
        System.clearProperty("VOYAGER_DB_SHOW_SQL");
    }

    @Test
    @DisabledIfEnvironmentVariable(named = "VOYAGER_DB_URL", matches = ".+")
    @DisabledIfEnvironmentVariable(named = "VOYAGER_DB_USER", matches = ".+")
    @DisabledIfEnvironmentVariable(named = "VOYAGER_DB_PASSWORD", matches = ".+")
    void defaultsMatchLocalDockerCompose() {
        DatabaseConfig config = DatabaseConfig.fromEnvironment();
        assertThat(config.jdbcUrl()).isEqualTo("jdbc:mariadb://localhost:3306/voyager-project");
        assertThat(config.username()).isEqualTo("voyager-project");
        assertThat(config.password()).isEqualTo("voyager-project");
        assertThat(config.poolSize()).isEqualTo(10);
        assertThat(config.hbm2ddlAction()).isEqualTo("update");
        assertThat(config.showSql()).isFalse();
    }

    @Test
    void systemPropertiesOverrideDefaults() {
        System.setProperty("VOYAGER_DB_URL", "jdbc:mariadb://db.prod:3306/voyager");
        System.setProperty("VOYAGER_DB_USER", "prod-user");
        System.setProperty("VOYAGER_DB_PASSWORD", "s3cret");
        System.setProperty("VOYAGER_DB_POOL_SIZE", "25");
        System.setProperty("VOYAGER_DB_HBM2DDL", "validate");
        System.setProperty("VOYAGER_DB_SHOW_SQL", "true");

        DatabaseConfig config = DatabaseConfig.fromEnvironment();
        assertThat(config.jdbcUrl()).isEqualTo("jdbc:mariadb://db.prod:3306/voyager");
        assertThat(config.username()).isEqualTo("prod-user");
        assertThat(config.password()).isEqualTo("s3cret");
        assertThat(config.poolSize()).isEqualTo(25);
        assertThat(config.hbm2ddlAction()).isEqualTo("validate");
        assertThat(config.showSql()).isTrue();
    }

    @Test
    void invalidPoolSizeFallsBackToDefault() {
        System.setProperty("VOYAGER_DB_POOL_SIZE", "not-a-number");
        DatabaseConfig config = DatabaseConfig.fromEnvironment();
        assertThat(config.poolSize()).isEqualTo(DatabaseConfig.DEFAULT_POOL_SIZE);
    }

    @Test
    void zeroPoolSizeIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DatabaseConfig("url", "user", "pass", 0, "update", false))
                .withMessageContaining("poolSize");
    }

    @Test
    void nullFieldsAreRejected() {
        assertThatNullPointerException()
                .isThrownBy(() -> new DatabaseConfig(null, "user", "pass", 1, "update", false));
        assertThatNullPointerException()
                .isThrownBy(() -> new DatabaseConfig("url", null, "pass", 1, "update", false));
        assertThatNullPointerException()
                .isThrownBy(() -> new DatabaseConfig("url", "user", null, 1, "update", false));
        assertThatNullPointerException()
                .isThrownBy(() -> new DatabaseConfig("url", "user", "pass", 1, null, false));
    }
}
