package net.elytrarace.voyager.platform.text;

import net.elytrarace.voyager.platform.text.exception.MalformedMessageBundleException;
import net.elytrarace.voyager.platform.text.exception.MissingMessageBundleException;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgument;
import net.kyori.adventure.text.minimessage.Context;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.ParsingException;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.translation.GlobalTranslator;
import net.kyori.adventure.translation.Translator;
import net.kyori.adventure.util.TriState;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * The rebuild's message bundles, as an Adventure {@link Translator}: keys in, MiniMessage out, in the player's locale.
 *
 * <h2>What it is for</h2>
 *
 * <p>Every user-facing string leaves the code. A call site builds a {@code Component.translatable}
 * (through {@link Messages}, which is the only place that names a key) and Minestom renders it on the
 * way out, per connection, in that player's locale. {@link #install()} is only half the wiring:
 * {@code PlayerSocketConnection.writePacketSync} gates the whole translation path on
 * {@code ServerFlag.AUTOMATIC_COMPONENT_TRANSLATION}, whose system property
 * {@code minestom.automatic-component-translation} defaults to <em>false</em> on 26.2. With it off
 * this class is installed, correct, and completely bypassed — players read dotted keys. The server's
 * bootstrap refuses to start without it. No caller ever renders by hand.
 *
 * <h2>Why not {@code TranslationStore}</h2>
 *
 * <p>Adventure's own stores hold either a {@link MessageFormat} or a finished {@link Component}. The
 * first is the wrong format — {@code {0}} is MessageFormat's placeholder syntax and this project
 * writes {@code <arg:0>}, which is MiniMessage's — and the second is a component that was parsed
 * before its arguments existed, so a line could never interleave an argument with colour. So the
 * store here is the raw MiniMessage source per key and locale, parsed at render time with the arguments bound.
 *
 * <h2>{@code {0}} is refused at load, not discovered in a screenshot</h2>
 *
 * <p>Because the MessageFormat path is disabled, a value written with {@code {0}} does not fail — it
 * renders the four characters {@code {0}} into the middle of a sentence, in production, forever.
 * {@link MessageCatalogue} rejects such a value, in every locale, with the key and the value named.
 *
 * <h2>Locales</h2>
 *
 * <p>Each shipped bundle is one file per locale, read as UTF-8. The locale of the player picks the
 * bundle along a {@link LocaleChain}; a key missing from that bundle is shown in English. A translation
 * must keep the arguments and palette tags of its English line, and the catalogue refuses it otherwise.
 */
public final class VoyagerTranslator implements Translator {

    /** The base name of the game server's bundles: {@code voyager_en_US.properties} and so on. */
    public static final String BUNDLE = "voyager";

    /** Every locale tag a server may ship a bundle for. {@link LocaleChain#FALLBACK} is required. */
    public static final List<String> SHIPPED_TAGS = List.of(LocaleChain.FALLBACK, "de_DE");

    /**
     * The translator's own name, namespaced {@code voyager} rather than {@code elytrarace}.
     *
     * <p>The two trees share a classpath until the old one is cut, and two translation sources under
     * one name is the same collision the {@code net.elytrarace.voyager..} package sub-root exists to
     * avoid.
     */
    private static final Key NAME = Key.key("voyager", "messages");

    private final MessageCatalogue catalogue;

    private VoyagerTranslator(MessageCatalogue catalogue) {
        this.catalogue = catalogue;
    }

    /**
     * A translator over one English key-to-MiniMessage table, for tests and for a single-language server.
     *
     * @throws MalformedMessageBundleException if a value uses {@code {0}} instead of {@code <arg:0>}
     */
    public static VoyagerTranslator of(Map<String, String> sources) {
        return ofLocales(Map.of(LocaleChain.FALLBACK, sources));
    }

    /**
     * A translator over several locales' tables, validated by {@link MessageCatalogue#of(Map)}.
     *
     * @param tables locale tag to key to MiniMessage source
     * @throws MalformedMessageBundleException as {@link MessageCatalogue#of(Map)} does
     */
    public static VoyagerTranslator ofLocales(Map<String, Map<String, String>> tables) {
        return new VoyagerTranslator(MessageCatalogue.of(tables));
    }

    /**
     * The shipped bundles of one product, read off the classpath: {@code /<bundle>_<tag>.properties} for every tag in
     * {@link #SHIPPED_TAGS} that is present. The English file is required; a missing other locale is not an error
     * here, because the build's parity test already refuses it and the fallback still answers.
     *
     * <p>Read one file per tag by name rather than through {@code ResourceBundle.getBundle}: that call resolves a
     * candidate chain against the JVM's default locale, so which file a server loads would depend on the machine it was
     * started on.
     *
     * @param bundle the base name, such as {@link #BUNDLE} or {@code voyager_setup}
     * @throws MissingMessageBundleException if the English bundle is not on the classpath
     * @throws MalformedMessageBundleException if a bundle cannot be read, or a value is refused
     */
    public static VoyagerTranslator fromClasspath(String bundle) {
        Map<String, Map<String, String>> tables = new LinkedHashMap<>();
        for (String tag : SHIPPED_TAGS) {
            String resource = "/%s_%s.properties".formatted(bundle, tag);
            Map<String, String> table = readTable(resource);
            if (table == null) {
                if (tag.equals(LocaleChain.FALLBACK)) {
                    throw new MissingMessageBundleException(resource);
                }
                continue;
            }
            tables.put(tag, table);
        }
        return ofLocales(tables);
    }

    private static @Nullable Map<String, String> readTable(String resource) {
        try (InputStream stream = VoyagerTranslator.class.getResourceAsStream(resource)) {
            if (stream == null) {
                return null;
            }
            Properties properties = new Properties();
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            Map<String, String> table = new LinkedHashMap<>();
            properties.forEach((key, value) -> table.put(String.valueOf(key), String.valueOf(value)));
            return table;
        } catch (IOException exception) {
            throw MalformedMessageBundleException.unreadable(resource, exception);
        }
    }

    /** Registers this translator with Adventure's global translator, which is what Minestom renders through. */
    public void install() {
        GlobalTranslator.translator().addSource(this);
    }

    /** How many keys the English bundle holds. For a boot log line, and for a test that the file was read at all. */
    public int size() {
        return catalogue.size();
    }

    /** The locale tags this translator holds, sorted. */
    public Set<String> tags() {
        return catalogue.tags();
    }

    /** The English keys each other locale does not translate, by locale tag, for one warning per key at boot. */
    public Map<String, Set<String>> untranslated() {
        return catalogue.untranslated();
    }

    @Override
    public Key name() {
        return NAME;
    }

    @Override
    public TriState hasAnyTranslations() {
        return catalogue.size() == 0 ? TriState.FALSE : TriState.TRUE;
    }

    /**
     * Whether a bundle along the locale's chain holds {@code key}.
     *
     * <p>Overridden rather than inherited, and it is load-bearing: Adventure's renderer asks this
     * <em>before</em> it asks for a component, and the inherited default answers by rendering the key
     * once with no arguments and throwing the result away. That is a MiniMessage parse per line per
     * tick, for an answer a map lookup already has.
     */
    @Override
    public boolean canTranslate(String key, Locale locale) {
        return catalogue.contains(key, locale);
    }

    /**
     * Always {@code null}: this bundle has no {@link MessageFormat} form, which is what keeps
     * {@code {0}} from ever being interpreted as a placeholder. See the class javadoc.
     */
    @Override
    public @Nullable MessageFormat translate(String key, Locale locale) {
        return null;
    }

    @Override
    public @Nullable Component translate(TranslatableComponent component, Locale locale) {
        MessageCatalogue.Answer answer = catalogue.answer(component.key(), locale);
        if (answer == null) {
            return null;
        }
        List<TranslationArgument> arguments = component.arguments();
        Component rendered = MiniMessage.miniMessage().deserialize(
                answer.source(),
                Palette.tagResolver(),
                new ArgumentTag(arguments));
        return component.children().isEmpty() ? rendered : rendered.children(component.children());
    }

    /**
     * Binds {@code <arg:0>} — and {@code <argument:0>}, the name Adventure's own examples use — to the
     * translatable component's arguments, zero-indexed.
     *
     * <p>Self-closing, so an argument inserts its value and nothing after it becomes its child. A
     * non-self-closing insert leaves the tag open to the end of the line and makes every later word
     * inherit the argument's style, which turns a white player name into a white rest-of-sentence.
     */
    private record ArgumentTag(List<TranslationArgument> arguments) implements TagResolver {

        private static final String LONG_NAME = "argument";
        private static final String SHORT_NAME = "arg";

        @Override
        public Tag resolve(String name, ArgumentQueue queue, Context context) throws ParsingException {
            int index = queue.popOr("no argument number given").asInt()
                    .orElseThrow(() -> context.newException("argument number must be a number", queue));
            if (index < 0 || index >= arguments.size()) {
                throw context.newException(
                        "argument %d is outside the %d argument(s) this message was given"
                                .formatted(index, arguments.size()), queue);
            }
            Object value = arguments.get(index).value();
            return Tag.selfClosingInserting(value instanceof ComponentLike like
                    ? like
                    : Component.text(String.valueOf(value)));
        }

        @Override
        public boolean has(String name) {
            return LONG_NAME.equals(name) || SHORT_NAME.equals(name);
        }
    }
}
