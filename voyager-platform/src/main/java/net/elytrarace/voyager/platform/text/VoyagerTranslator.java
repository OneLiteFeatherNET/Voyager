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
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * The rebuild's message bundle, as an Adventure {@link Translator}: keys in, MiniMessage out.
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
 * store here is the raw MiniMessage source per key, parsed at render time with the arguments bound.
 * That is the same shape the tree being replaced arrived at, for the same reason, and it is the
 * reason {@link #translate(String, Locale)} returns {@code null}: there is no MessageFormat to give.
 *
 * <h2>{@code {0}} is refused at load, not discovered in a screenshot</h2>
 *
 * <p>Because the MessageFormat path is disabled, a value written with {@code {0}} does not fail — it
 * renders the four characters {@code {0}} into the middle of a sentence, in production, forever.
 * {@link #of} rejects such a value with the key and the value named. That check is the reason this
 * class validates at all.
 *
 * <h2>One language</h2>
 *
 * <p>English, and every locale gets it. A second language is a second file and a locale-keyed map;
 * nothing here has to change shape for that, and until somebody writes one, pretending to choose
 * between bundles would be a mechanism with no second case to prove it works.
 */
public final class VoyagerTranslator implements Translator {

    /** The bundle on the classpath, shipped by {@code voyager-server}. */
    public static final String BUNDLE_RESOURCE = "/voyager_en_US.properties";

    /**
     * The translator's own name, namespaced {@code voyager} rather than {@code elytrarace}.
     *
     * <p>The two trees share a classpath until the old one is cut, and two translation sources under
     * one name is the same collision the {@code net.elytrarace.voyager..} package sub-root exists to
     * avoid.
     */
    private static final Key NAME = Key.key("voyager", "messages");

    /** MessageFormat's placeholder syntax, which must never appear in a value. */
    private static final Pattern MESSAGE_FORMAT_PLACEHOLDER = Pattern.compile("\\{\\d+}");

    private final Map<String, String> sources;

    private VoyagerTranslator(Map<String, String> sources) {
        this.sources = sources;
    }

    /**
     * A translator over the given key-to-MiniMessage table.
     *
     * @throws MalformedMessageBundleException if a value uses {@code {0}} instead of {@code <arg:0>}
     */
    public static VoyagerTranslator of(Map<String, String> sources) {
        Map<String, String> copy = new HashMap<>(sources);
        copy.forEach((key, value) -> {
            if (MESSAGE_FORMAT_PLACEHOLDER.matcher(value).find()) {
                throw MalformedMessageBundleException.messageFormatPlaceholder(key, value);
            }
        });
        return new VoyagerTranslator(Map.copyOf(copy));
    }

    /**
     * The shipped bundle, read off the classpath.
     *
     * <p>Read as one resource rather than through {@code ResourceBundle.getBundle}: that call
     * resolves a candidate chain against the JVM's default locale and throws
     * {@code MissingResourceException} for a locale the chain does not reach, so which file a server
     * loads would depend on the machine it was started on. One file, named once, loads the same on
     * every machine.
     *
     * @throws MissingMessageBundleException if the bundle is not on the classpath
     * @throws MalformedMessageBundleException if it cannot be read, or a value uses {@code {0}}
     */
    public static VoyagerTranslator fromClasspath() {
        try (InputStream bundle = VoyagerTranslator.class.getResourceAsStream(BUNDLE_RESOURCE)) {
            if (bundle == null) {
                throw new MissingMessageBundleException(BUNDLE_RESOURCE);
            }
            Properties properties = new Properties();
            properties.load(new java.io.InputStreamReader(bundle, StandardCharsets.UTF_8));
            Map<String, String> sources = new LinkedHashMap<>();
            properties.forEach((key, value) -> sources.put(String.valueOf(key), String.valueOf(value)));
            return of(sources);
        } catch (IOException exception) {
            throw MalformedMessageBundleException.unreadable(BUNDLE_RESOURCE, exception);
        }
    }

    /** Registers this translator with Adventure's global translator, which is what Minestom renders through. */
    public void install() {
        GlobalTranslator.translator().addSource(this);
    }

    /** How many keys the bundle holds. For a boot log line, and for a test that the file was read at all. */
    public int size() {
        return sources.size();
    }

    @Override
    public Key name() {
        return NAME;
    }

    @Override
    public TriState hasAnyTranslations() {
        return sources.isEmpty() ? TriState.FALSE : TriState.TRUE;
    }

    /**
     * Whether this bundle holds {@code key}, for any locale.
     *
     * <p>Overridden rather than inherited, and it is load-bearing: Adventure's renderer asks this
     * <em>before</em> it asks for a component, and the inherited default answers by rendering the key
     * once with no arguments and throwing the result away. That is a MiniMessage parse per line per
     * tick, for an answer a map lookup already has.
     */
    @Override
    public boolean canTranslate(String key, Locale locale) {
        return sources.containsKey(key);
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
        String source = sources.get(component.key());
        if (source == null) {
            return null;
        }
        List<TranslationArgument> arguments = component.arguments();
        Component rendered = MiniMessage.miniMessage().deserialize(
                source,
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
