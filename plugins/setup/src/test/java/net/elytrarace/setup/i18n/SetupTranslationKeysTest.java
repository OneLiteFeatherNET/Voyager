package net.elytrarace.setup.i18n;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class SetupTranslationKeysTest {

    static Stream<String[]> allSetupKeys() throws IOException {
        Properties properties = new Properties();
        try (InputStream stream = SetupTranslationKeysTest.class
                .getClassLoader()
                .getResourceAsStream("elytrarace.properties")) {
            assertThat(stream).as("elytrarace.properties must exist on the classpath").isNotNull();
            properties.load(stream);
        }
        return properties.stringPropertyNames().stream()
                .map(key -> new String[]{key, properties.getProperty(key)});
    }

    @ParameterizedTest(name = "key ''{0}'' must not use MessageFormat syntax")
    @MethodSource("allSetupKeys")
    @DisplayName("Setup translation values must not contain MessageFormat placeholders")
    void setupKey_usesArgSyntax_notMessageFormat(String key, String value) {
        assertThat(value)
                .as("Key '%s' must not use {0} MessageFormat syntax — use <arg:N> instead", key)
                .doesNotContain("{0}")
                .doesNotContain("{1}")
                .doesNotContain("{2}")
                .doesNotContain("{3}")
                .doesNotContain("{4}");
    }

    @ParameterizedTest(name = "key ''{0}'' must not have empty value")
    @MethodSource("allSetupKeys")
    @DisplayName("Setup translation values must not be blank")
    void setupKey_hasNonBlankValue(String key, String value) {
        assertThat(value)
                .as("Key '%s' must not have a blank translation value", key)
                .isNotBlank();
    }

    @ParameterizedTest(name = "key ''{0}'' arg tags must use valid numeric index syntax")
    @MethodSource("allSetupKeys")
    @DisplayName("Setup translation arg tags use valid <arg:N> syntax")
    void setupKey_argTagsSyntaxIsValid(String key, String value) {
        if (!value.contains("<arg:")) {
            return;
        }
        int searchFrom = 0;
        while (true) {
            int argStart = value.indexOf("<arg:", searchFrom);
            if (argStart == -1) {
                break;
            }
            int argEnd = value.indexOf(">", argStart);
            assertThat(argEnd)
                    .as("Key '%s': unclosed <arg: tag in value: %s", key, value)
                    .isGreaterThan(argStart);
            String indexPart = value.substring(argStart + "<arg:".length(), argEnd);
            assertThat(indexPart)
                    .as("Key '%s': <arg: tag index must be a non-negative integer, got '%s'", key, indexPart)
                    .matches("\\d+");
            searchFrom = argEnd + 1;
        }
    }
}
