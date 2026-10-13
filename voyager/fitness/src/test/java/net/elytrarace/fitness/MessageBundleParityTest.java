package net.elytrarace.fitness;

import net.elytrarace.voyager.platform.text.Messages;
import net.elytrarace.voyager.platform.text.SetupMessages;
import net.kyori.adventure.text.TranslatableComponent;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shipped German bundles are complete: every key the server and the setup server send exists in both locales,
 * with the placeholders of its English line. Reads classpath resources only; touches no clock and installs nothing
 * into Adventure's global translator.
 */
class MessageBundleParityTest {

    private static final String PREFIX = "voyager.";

    @Test
    void theServerBundlesHoldEveryKeyTheServerSendsInBothLocales() {
        Map<String, String> english = table("/voyager_en_US.properties");
        Map<String, String> german = table("/voyager_de_DE.properties");

        assertThat(BundleParity.violations("server", english, german, keysOf(Messages.class)))
                .as("the server bundles must be complete and must agree on placeholders")
                .isEmpty();
    }

    @Test
    void theSetupBundlesHoldEveryKeyTheSetupServerSendsInBothLocales() {
        Map<String, String> english = table("/voyager_setup_en_US.properties");
        Map<String, String> german = table("/voyager_setup_de_DE.properties");
        Set<String> sent = keysOf(SetupMessages.class);
        // The setup server also sends the shared refusal, Messages.commandDenied(), so its key is required here too.
        sent.add(((TranslatableComponent) Messages.commandDenied()).key());

        assertThat(BundleParity.violations("setup", english, german, sent))
                .as("the setup bundles must be complete and must agree on placeholders")
                .isEmpty();
    }

    /** The keys a factory class names, read from its constants, so that a new key cannot be sent without a line. */
    private static Set<String> keysOf(Class<?> factory) {
        Set<String> keys = new TreeSet<>();
        for (Field field : factory.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(null);
                    if (value instanceof String key && key.startsWith(PREFIX)) {
                        keys.add(key);
                    }
                } catch (IllegalAccessException exception) {
                    throw new AssertionError("cannot read the key constant %s".formatted(field.getName()), exception);
                }
            }
        }
        return keys;
    }

    private static Map<String, String> table(String resource) {
        try (InputStream stream = MessageBundleParityTest.class.getResourceAsStream(resource)) {
            assertThat(stream).as("the bundle %s is on the classpath", resource).isNotNull();
            Properties properties = new Properties();
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            Map<String, String> table = new LinkedHashMap<>();
            properties.forEach((key, value) -> table.put(String.valueOf(key), String.valueOf(value)));
            return table;
        } catch (IOException exception) {
            throw new AssertionError("cannot read the bundle %s".formatted(resource), exception);
        }
    }
}
