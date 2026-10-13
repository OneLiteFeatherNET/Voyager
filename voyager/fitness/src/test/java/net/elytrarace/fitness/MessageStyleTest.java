package net.elytrarace.fitness;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The colour rules of the message style guide, checked over the bundles themselves: a failure is warn, a refusal is
 * caution, no line has two accents, and red is never used. Each assertion names the key that breaks it.
 *
 * <p>It lives beside {@link MessageBundleParityTest} because only this module can read both products' bundles. Reads
 * classpath resources only; touches no clock and installs nothing.
 */
class MessageStyleTest {

    private static final Set<String> FAILURE_KEYS = Set.of(
            "voyager.command.reload.failed",
            "voyager.command.reload.denied",
            "voyager.command.denied",
            "voyager.setup.refused",
            "voyager.setup.save.failed");

    private static final Set<String> REFUSAL_KEYS = Set.of(
            "voyager.command.skip.refused",
            "voyager.setup.ring.none");

    /** A palette tag that is an accent: any colour tag except the dim structure tag. */
    private static final Pattern ACCENT = Pattern.compile("<(c-(?!dim>)[a-z]+)>");

    /** Red in any form: the named red tags, or a hex code. The course spends red on its own finish line. */
    private static final Pattern RED = Pattern.compile("<(c-|m-)?(dark_)?red>|<#[0-9a-fA-F]{6}>");

    private static final List<String> BUNDLES = List.of(
            "/voyager_en_US.properties",
            "/voyager_de_DE.properties",
            "/voyager_setup_en_US.properties",
            "/voyager_setup_de_DE.properties");

    @Test
    void aFailureKeyUsesTheWarnTag() {
        for (String resource : BUNDLES) {
            Map<String, String> bundle = table(resource);
            for (String key : FAILURE_KEYS) {
                if (bundle.containsKey(key)) {
                    assertThat(bundle.get(key))
                            .as("failure key %s in %s uses <c-warn>", key, resource)
                            .contains("<c-warn>");
                }
            }
        }
    }

    @Test
    void aRefusalKeyUsesTheCautionTag() {
        for (String resource : BUNDLES) {
            Map<String, String> bundle = table(resource);
            for (String key : REFUSAL_KEYS) {
                if (bundle.containsKey(key)) {
                    assertThat(bundle.get(key))
                            .as("refusal key %s in %s uses <c-caution>, not the dim tag", key, resource)
                            .contains("<c-caution>");
                }
            }
        }
    }

    @Test
    void noBundleValueUsesARedColour() {
        for (String resource : BUNDLES) {
            table(resource).forEach((key, value) ->
                    assertThat(RED.matcher(value).find())
                            .as("key %s in %s uses no red colour", key, resource)
                            .isFalse());
        }
    }

    @Test
    void noLineHasTwoAccents() {
        for (String resource : BUNDLES) {
            table(resource).forEach((key, value) -> {
                Set<String> accents = new java.util.HashSet<>();
                Matcher matcher = ACCENT.matcher(value);
                while (matcher.find()) {
                    accents.add(matcher.group(1));
                }
                assertThat(accents)
                        .as("key %s in %s has at most one accent besides the dim tag", key, resource)
                        .hasSizeLessThanOrEqualTo(1);
            });
        }
    }

    /** The checks above are not vacuous: the failure and refusal keys exist in the bundles they name. */
    @Test
    void theNamedFailureAndRefusalKeysExistInTheBundles() {
        Map<String, String> server = table("/voyager_en_US.properties");
        Map<String, String> setup = table("/voyager_setup_en_US.properties");

        assertThat(server).containsKeys("voyager.command.reload.failed", "voyager.command.skip.refused");
        assertThat(setup).containsKeys("voyager.setup.refused", "voyager.setup.ring.none");
    }

    private static Map<String, String> table(String resource) {
        try (InputStream stream = MessageStyleTest.class.getResourceAsStream(resource)) {
            assertThat(stream).as("the bundle %s is on the classpath", resource).isNotNull();
            Properties properties = new Properties();
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            Map<String, String> table = new LinkedHashMap<>();
            properties.forEach((key, value) -> table.put(String.valueOf(key), String.valueOf(value)));
            return table;
        } catch (java.io.IOException exception) {
            throw new AssertionError("cannot read the bundle %s".formatted(resource), exception);
        }
    }
}
