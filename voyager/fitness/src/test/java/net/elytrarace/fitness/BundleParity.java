package net.elytrarace.fitness;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * The parity rules for one product's English and German bundles, as pure functions over tables that a caller has
 * already read. It reads no file and no classpath, so a test can prove each rule against an in-memory table.
 */
final class BundleParity {

    /** The tag of the second locale, and the only one this checker compares against English. */
    static final String GERMAN = "de_DE";

    /** An argument {@code <arg:N>} (or {@code <argument:N>}) or a palette tag {@code <c-*>} / {@code <m-*>}. */
    private static final Pattern PLACEHOLDER = Pattern.compile("<(?:arg|argument):\\d+>|<[cm]-[a-z-]+>");

    /** MessageFormat's placeholder syntax, which renders as literal text in this project and so must never appear. */
    private static final Pattern MESSAGE_FORMAT = Pattern.compile("\\{\\d+}");

    private BundleParity() {
    }

    /**
     * Every way the German table disagrees with the English one and with the keys the product sends.
     *
     * @param product a name for messages, such as {@code server} or {@code setup}
     * @param english the English table, key to MiniMessage source
     * @param german the German table, key to MiniMessage source
     * @param required the keys the product sends, which both tables must hold
     * @return one message per violation, each naming the key and the locale; empty when the pair is sound
     */
    static List<String> violations(String product, Map<String, String> english, Map<String, String> german,
            Set<String> required) {
        List<String> found = new ArrayList<>();
        for (String key : new TreeSet<>(required)) {
            if (!english.containsKey(key)) {
                found.add("%s: the English bundle has no line for the key '%s' that the product sends".formatted(product, key));
            }
            if (!german.containsKey(key)) {
                found.add("%s: the %s bundle has no line for the key '%s'".formatted(product, GERMAN, key));
            }
        }
        for (String key : new TreeSet<>(german.keySet())) {
            if (!english.containsKey(key)) {
                found.add("%s: the %s bundle defines the key '%s', which the English bundle does not".formatted(product, GERMAN, key));
            }
        }
        for (String key : new TreeSet<>(english.keySet())) {
            if (!german.containsKey(key)) {
                found.add("%s: the %s bundle has no line for the key '%s'".formatted(product, GERMAN, key));
            }
        }
        for (String key : new TreeSet<>(german.keySet())) {
            String value = german.get(key);
            if (MESSAGE_FORMAT.matcher(value).find()) {
                found.add("%s: the %s line for '%s' uses MessageFormat {N}, which renders as literal text".formatted(product, GERMAN, key));
            }
            String reference = english.get(key);
            if (reference != null && !placeholders(reference).equals(placeholders(value))) {
                found.add("%s: the %s line for '%s' has placeholders %s, the English line has %s".formatted(
                        product, GERMAN, key, placeholders(value), placeholders(reference)));
            }
        }
        for (String key : english.keySet()) {
            if (english.containsKey(key + ".one")) {
                for (String form : List.of(key + ".one", key + ".other")) {
                    if (!german.containsKey(form)) {
                        found.add("%s: the %s bundle has no plural form '%s' for '%s'".formatted(product, GERMAN, form, key));
                    }
                }
            }
        }
        return found;
    }

    /** The arguments and palette tags of a value, sorted: two values agree when these lists are equal. */
    static List<String> placeholders(String value) {
        List<String> found = new ArrayList<>();
        var matcher = PLACEHOLDER.matcher(value);
        while (matcher.find()) {
            found.add(matcher.group());
        }
        found.sort(null);
        return found;
    }
}
