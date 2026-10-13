package net.elytrarace.voyager.platform.text;

import net.elytrarace.voyager.platform.text.exception.MalformedMessageBundleException;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One MiniMessage table per locale tag, and the answer for a key along a player's {@link LocaleChain}.
 *
 * <p>Pure: no I/O and no Minestom type. The tables arrive as maps, read by the caller, so a test builds its own in
 * memory and never touches the classpath. A value is validated when the catalogue is built: no {@code {N}}
 * MessageFormat placeholder in any locale, and no value in a locale other than {@link LocaleChain#FALLBACK} may use a
 * different set of arguments or palette tags from the English value of the same key. A translator can reword a line;
 * they cannot drop an argument or recolour it.
 */
public final class MessageCatalogue {

    /** An argument {@code <arg:N>} (or Adventure's {@code <argument:N>}) or a palette tag {@code <c-*>} / {@code <m-*>}. */
    private static final Pattern PLACEHOLDER = Pattern.compile("<(?:arg|argument):\\d+>|<[cm]-[a-z-]+>");

    /** MessageFormat's placeholder syntax, which must never appear in a value. */
    private static final Pattern MESSAGE_FORMAT_PLACEHOLDER = Pattern.compile("\\{\\d+}");

    /** The key a player sees, and the locale tag whose table answered it. */
    public record Answer(String tag, String source) {
    }

    private final Map<String, Map<String, String>> tables;
    private final Map<Locale, List<String>> chains = new ConcurrentHashMap<>();

    private MessageCatalogue(Map<String, Map<String, String>> tables) {
        this.tables = tables;
    }

    /**
     * A catalogue over the given tables, validated.
     *
     * @param tables locale tag (such as {@code de_DE}) to key to MiniMessage source; must hold {@link LocaleChain#FALLBACK}
     * @throws MalformedMessageBundleException if the fallback table is missing, a value uses MessageFormat placeholders,
     *     or a value disagrees with English about its arguments or palette tags
     */
    @Contract(pure = true)
    public static MessageCatalogue of(Map<String, Map<String, String>> tables) {
        Map<String, String> english = tables.get(LocaleChain.FALLBACK);
        if (english == null) {
            throw MalformedMessageBundleException.missingFallback(LocaleChain.FALLBACK);
        }
        Map<String, Map<String, String>> copy = new TreeMap<>();
        tables.forEach((tag, table) -> {
            Map<String, String> validated = new TreeMap<>();
            table.forEach((key, value) -> {
                if (MESSAGE_FORMAT_PLACEHOLDER.matcher(value).find()) {
                    throw MalformedMessageBundleException.messageFormatPlaceholder(key, value);
                }
                String reference = english.get(key);
                if (!tag.equals(LocaleChain.FALLBACK) && reference != null
                        && !placeholders(reference).equals(placeholders(value))) {
                    throw MalformedMessageBundleException.placeholdersDiffer(key, tag, reference, value);
                }
                validated.put(key, value);
            });
            copy.put(tag, Collections.unmodifiableMap(validated));
        });
        return new MessageCatalogue(Collections.unmodifiableMap(copy));
    }

    /**
     * The arguments and palette tags of a value, sorted. Two values are interchangeable for a player
     * only when these lists are equal, which is why they are sorted.
     */
    @Contract(pure = true)
    public static List<String> placeholders(String value) {
        List<String> found = new ArrayList<>();
        Matcher matcher = PLACEHOLDER.matcher(value);
        while (matcher.find()) {
            found.add(matcher.group());
        }
        Collections.sort(found);
        return found;
    }

    /**
     * The first table along the player's chain that holds {@code key}, or {@code null} when no table holds it at all.
     */
    public @Nullable Answer answer(String key, Locale locale) {
        for (String tag : chains.computeIfAbsent(locale, this::chainFor)) {
            Map<String, String> table = tables.get(tag);
            if (table != null && table.containsKey(key)) {
                return new Answer(tag, table.get(key));
            }
        }
        return null;
    }

    /** Whether any table along the chain holds {@code key}. */
    public boolean contains(String key, Locale locale) {
        return answer(key, locale) != null;
    }

    /** How many keys the fallback table holds. */
    public int size() {
        return tables.get(LocaleChain.FALLBACK).size();
    }

    /** The locale tags this catalogue holds, sorted. */
    public Set<String> tags() {
        return Collections.unmodifiableSet(new TreeSet<>(tables.keySet()));
    }

    /**
     * For every locale other than the fallback, the English keys it does not translate, so that the composition root
     * can say once at boot which lines a German player will see in English.
     */
    public Map<String, Set<String>> untranslated() {
        Set<String> english = tables.getOrDefault(LocaleChain.FALLBACK, Map.of()).keySet();
        Map<String, Set<String>> missing = new TreeMap<>();
        for (Map.Entry<String, Map<String, String>> table : tables.entrySet()) {
            if (table.getKey().equals(LocaleChain.FALLBACK)) {
                continue;
            }
            Set<String> absent = new TreeSet<>(english);
            absent.removeAll(table.getValue().keySet());
            if (!absent.isEmpty()) {
                missing.put(table.getKey(), absent);
            }
        }
        return missing;
    }

    private List<String> chainFor(Locale locale) {
        return LocaleChain.candidates(locale, tables.keySet());
    }
}
