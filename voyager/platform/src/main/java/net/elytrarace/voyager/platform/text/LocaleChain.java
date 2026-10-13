package net.elytrarace.voyager.platform.text;

import org.jetbrains.annotations.Contract;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/**
 * The order in which the bundles are asked for a player's message: the player's own tag, the loaded bundles of their
 * language, then {@value #FALLBACK}, which every server ships and which is never skipped.
 *
 * <p>Pure: it knows only the tags it is given, so it runs in a unit test in milliseconds with no I/O and no Minestom
 * type. A {@code de_AT} client gets {@code de_DE} because that is the German bundle that exists: the language is the
 * only thing the two share, and a German line is a better answer to a German player than an English one.
 */
public final class LocaleChain {

    /** The tag of the bundle every other bundle falls back to. */
    public static final String FALLBACK = "en_US";

    private LocaleChain() {
    }

    /**
     * The tags to try, in order, for a player whose client sent {@code requested}.
     *
     * @param requested the client's locale; a locale without a language gets {@link #FALLBACK} alone
     * @param loaded the tags of the bundles this server holds, such as {@code de_DE}
     * @return the candidate tags, each at most once, always ending with {@link #FALLBACK}
     */
    @Contract(pure = true)
    public static List<String> candidates(Locale requested, Collection<String> loaded) {
        List<String> chain = new ArrayList<>(4);
        String language = requested.getLanguage();
        String country = requested.getCountry();
        if (!language.isEmpty() && !country.isEmpty()) {
            String exact = language + "_" + country;
            if (loaded.contains(exact)) {
                chain.add(exact);
            }
        }
        if (!language.isEmpty()) {
            for (String tag : new TreeSet<>(loaded)) {
                if (tag.startsWith(language + "_") && !chain.contains(tag)) {
                    chain.add(tag);
                }
            }
        }
        if (!chain.contains(FALLBACK)) {
            chain.add(FALLBACK);
        }
        return List.copyOf(chain);
    }
}
