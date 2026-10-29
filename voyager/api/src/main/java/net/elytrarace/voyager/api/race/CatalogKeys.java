package net.elytrarace.voyager.api.race;

import net.elytrarace.voyager.api.race.exception.InvalidCatalogKeyException;

import net.kyori.adventure.key.Key;

import java.util.regex.Pattern;

/**
 * The identifiers of maps and cups: Adventure keys in the {@code elytrarace} namespace (ADR-0040).
 *
 * <p>A flat id such as {@code blue_and_red} is read as {@code elytrarace:blue_and_red}. The flat form is accepted only
 * where an operator types a name, during the migration; a file states its key in full. Declared as an interface, which
 * is the one shape an API type of static helpers may take here.
 */
public interface CatalogKeys {

    /** The namespace every map and cup key is in. */
    String NAMESPACE = "elytrarace";

    /** The rule a key's value follows: lower case letters, digits, underscores and hyphens, at most 32 characters. */
    String VALUE_PATTERN = "[a-z0-9][a-z0-9_-]{0,31}";

    /**
     * The key of a flat id.
     *
     * @param value the id, {@code [a-z0-9][a-z0-9_-]{0,31}}
     * @return the key {@code elytrarace:<value>}
     * @throws InvalidCatalogKeyException if the value does not match the rule
     */
    static Key of(String value) {
        if (value == null || !Pattern.matches(VALUE_PATTERN, value)) {
            throw InvalidCatalogKeyException.badValue(value);
        }
        return Key.key(NAMESPACE, value);
    }

    /**
     * Checks a key stated in full: its namespace must be {@code elytrarace} and its value must match the rule.
     *
     * @return the key, unchanged
     * @throws InvalidCatalogKeyException if the namespace or the value is refused
     */
    static Key checked(Key key) {
        if (!NAMESPACE.equals(key.namespace())) {
            throw InvalidCatalogKeyException.foreignNamespace(key.asString());
        }
        if (!Pattern.matches(VALUE_PATTERN, key.value())) {
            throw InvalidCatalogKeyException.badValue(key.value());
        }
        return key;
    }

    /**
     * The key string an operator's input names: a key stays as it is, and a flat id gains the {@code elytrarace:}
     * namespace. Whether the string names a catalogue entry is answered by the catalogue, not here.
     */
    static String normalise(String text) {
        return text.contains(":") ? text : NAMESPACE + ":" + text;
    }

    /**
     * The name a key shows when its file gives no display name: its value with each underscore or hyphen replaced by a
     * space, and each word capitalised. {@code elytrarace:dragon_lobby} reads "Dragon Lobby", never the raw key.
     *
     * @return the title-cased value of the key
     */
    static String titleCase(Key key) {
        StringBuilder title = new StringBuilder();
        for (String word : key.value().split("[_-]")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!title.isEmpty()) {
                title.append(' ');
            }
            title.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return title.toString();
    }
}
