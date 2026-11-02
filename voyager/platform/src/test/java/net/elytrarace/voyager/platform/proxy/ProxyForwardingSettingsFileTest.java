package net.elytrarace.voyager.platform.proxy;

import net.elytrarace.voyager.platform.proxy.exception.ProxySecretException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The secret file as the settings read it. Sources are maps, so no test changes the process environment or a system property;
 * files live in a fresh temporary directory per test.
 */
class ProxyForwardingSettingsFileTest {

    private static final String FILE_ENVIRONMENT = "VOYAGER_VELOCITY_SECRET_FILE";
    private static final String FILE_PROPERTY = "voyager.velocity.secret.file";
    private static final String ENVIRONMENT = "VOYAGER_VELOCITY_SECRET";
    private static final String PROPERTY = "voyager.velocity.secret";

    @TempDir
    Path directory;

    @Test
    void aFilesTrimmedContentIsTheSecretAndBeatsTheEnvironmentAndTheProperty() throws IOException {
        Path file = write("secret", "  s3cret-file\n");

        ProxyForwardingSettings settings = ProxyForwardingSettings.from(
                Map.of(FILE_ENVIRONMENT, file.toString(), ENVIRONMENT, "from-environment"),
                Map.of(PROPERTY, "from-property"));

        assertThat(settings.secret()).isEqualTo("s3cret-file");
    }

    @Test
    void aFilePropertyIsReadWhenTheEnvironmentNamesNoFile() throws IOException {
        Path file = write("secret", "from-file-property\n");

        ProxyForwardingSettings settings = ProxyForwardingSettings.from(Map.of(), Map.of(FILE_PROPERTY, file.toString()));

        assertThat(settings.secret()).isEqualTo("from-file-property");
    }

    @Test
    void theEnvironmentsFileKeyBeatsThePropertysFileKey() throws IOException {
        Path environmentFile = write("environment-secret", "from-environment-file");
        Path propertyFile = write("property-secret", "from-property-file");

        ProxyForwardingSettings settings = ProxyForwardingSettings.from(
                Map.of(FILE_ENVIRONMENT, environmentFile.toString()),
                Map.of(FILE_PROPERTY, propertyFile.toString()));

        assertThat(settings.secret()).isEqualTo("from-environment-file");
    }

    @Test
    void anEmptyFileRefusesTheStartAndNamesTheSettingAndThePath() throws IOException {
        Path file = write("secret", " \n\t ");

        assertThatThrownBy(() -> ProxyForwardingSettings.from(Map.of(FILE_ENVIRONMENT, file.toString()), Map.of()))
                .isInstanceOf(ProxySecretException.class)
                .satisfies(failure -> assertThat(((ProxySecretException) failure).setting()).isEqualTo(FILE_ENVIRONMENT))
                .hasMessageContaining(FILE_ENVIRONMENT)
                .hasMessageContaining(file.toString());
    }

    @Test
    void aMissingFileRefusesTheStartAndNamesThePath() {
        Path missing = directory.resolve("no-such-secret");

        assertThatThrownBy(() -> ProxyForwardingSettings.from(Map.of(FILE_ENVIRONMENT, missing.toString()), Map.of()))
                .isInstanceOf(ProxySecretException.class)
                .hasMessageContaining(FILE_ENVIRONMENT)
                .hasMessageContaining(missing.toString());
    }

    @Test
    void aDirectoryRefusesTheStartAndNamesThePath() {
        assertThatThrownBy(() -> ProxyForwardingSettings.from(Map.of(FILE_ENVIRONMENT, directory.toString()), Map.of()))
                .isInstanceOf(ProxySecretException.class)
                .hasMessageContaining(directory.toString());
    }

    @Test
    void aFileThatIsNotUtf8RefusesTheStartWithoutItsContent() throws IOException {
        Path file = directory.resolve("secret");
        Files.write(file, new byte[] {'s', '3', 'c', 'r', 'e', 't', (byte) 0xFF});

        assertThatThrownBy(() -> ProxyForwardingSettings.from(Map.of(FILE_ENVIRONMENT, file.toString()), Map.of()))
                .isInstanceOf(ProxySecretException.class)
                .hasMessageContaining(file.toString())
                .hasMessageNotContaining("s3cret");
    }

    @Test
    void aBlankFilePathRefusesTheStartAndNamesTheSetting() {
        assertThatThrownBy(() -> ProxyForwardingSettings.from(Map.of(FILE_ENVIRONMENT, "   "), Map.of()))
                .isInstanceOf(ProxySecretException.class)
                .hasMessageContaining(FILE_ENVIRONMENT);
    }

    @Test
    void aBrokenFileIsNotBypassedByAValidEnvironmentSecret() throws IOException {
        Path empty = write("secret", "");

        assertThatThrownBy(() -> ProxyForwardingSettings.from(
                Map.of(FILE_ENVIRONMENT, empty.toString(), ENVIRONMENT, "s3cret"), Map.of()))
                .isInstanceOf(ProxySecretException.class);
    }

    @Test
    void theRecordNamesTheSourceTheSecretCameFrom() throws IOException {
        Path file = write("secret", "s3cret");

        assertThat(ProxyForwardingSettings.from(Map.of(), Map.of()).source())
                .isEqualTo(ProxyForwardingSettings.Source.NONE);
        assertThat(ProxyForwardingSettings.from(Map.of(ENVIRONMENT, "s3cret"), Map.of()).source())
                .isEqualTo(ProxyForwardingSettings.Source.ENVIRONMENT);
        assertThat(ProxyForwardingSettings.from(Map.of(), Map.of(PROPERTY, "s3cret")).source())
                .isEqualTo(ProxyForwardingSettings.Source.PROPERTY);
        assertThat(ProxyForwardingSettings.from(Map.of(FILE_ENVIRONMENT, file.toString()), Map.of()).source())
                .isEqualTo(ProxyForwardingSettings.Source.FILE);
    }

    private Path write(String name, String content) throws IOException {
        return Files.writeString(directory.resolve(name), content);
    }
}
