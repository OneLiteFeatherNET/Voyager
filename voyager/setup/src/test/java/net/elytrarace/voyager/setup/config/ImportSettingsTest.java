package net.elytrarace.voyager.setup.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The import keys, read through a lookup the way the composition root hands over {@code ConfigLoader::get}. */
class ImportSettingsTest {

    @Test
    void theDefaultsAreTheImportsFolderAndTheLimitsOfTheSpec() {
        ImportSettings settings = ImportSettings.from(key -> null);

        assertThat(settings.root()).isEqualTo(Path.of("imports"));
        assertThat(settings.maxBytes()).isEqualTo(268_435_456L);
        assertThat(settings.maxRegions()).isEqualTo(1024);
    }

    @Test
    void theKeysOverrideTheDefaults() {
        Map<String, String> values = Map.of(
                "VOYAGER_IMPORT_PATH", "/srv/worlds",
                "VOYAGER_IMPORT_MAX_BYTES", "1048576",
                "VOYAGER_IMPORT_MAX_REGIONS", "64");

        ImportSettings settings = ImportSettings.from(values::get);

        assertThat(settings.root()).isEqualTo(Path.of("/srv/worlds"));
        assertThat(settings.maxBytes()).isEqualTo(1_048_576L);
        assertThat(settings.maxRegions()).isEqualTo(64);
    }

    @Test
    void aLimitThatIsNotANumberIsRefusedNamingTheKey() {
        Map<String, String> values = Map.of("VOYAGER_IMPORT_MAX_REGIONS", "many");

        assertThatThrownBy(() -> ImportSettings.from(values::get))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("VOYAGER_IMPORT_MAX_REGIONS");
    }

    @Test
    void aZeroLimitIsRefusedNamingTheKey() {
        Map<String, String> values = Map.of("VOYAGER_IMPORT_MAX_BYTES", "0");

        assertThatThrownBy(() -> ImportSettings.from(values::get))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("VOYAGER_IMPORT_MAX_BYTES");
    }
}
