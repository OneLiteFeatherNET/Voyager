package net.elytrarace.voyager.platform.text;

import net.elytrarace.voyager.platform.text.exception.MalformedMessageBundleException;
import net.elytrarace.voyager.platform.text.exception.MissingMessageBundleException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.translation.GlobalTranslator;
import net.kyori.adventure.util.TriState;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The translator, tested against tables built in code rather than against the shipped bundle.
 *
 * <p>The bundle lives in {@code voyager-server} and is rendered end to end by that module's own
 * test. What matters here is the machinery: that {@code <arg:0>} binds, that {@code {0}} is refused
 * at load instead of reaching a player's screen, that a palette tag colours, and that an argument
 * does not drag its own style over the rest of the line.
 */
class VoyagerTranslatorTest {

    private static Component translate(VoyagerTranslator translator, Component component) {
        Component rendered = translator.translate((TranslatableComponent) component, Locale.US);
        assertThat(rendered).isNotNull();
        return rendered;
    }

    private static String render(VoyagerTranslator translator, Component component) {
        return PlainTextComponentSerializer.plainText().serialize(translate(translator, component));
    }

    /** One entry per visible run of text, with the colour that run actually inherits. */
    private record Run(String text, TextColor colour) {
    }

    private static List<Run> runs(Component component, TextColor inherited) {
        TextColor effective = component.color() == null ? inherited : component.color();
        List<Run> out = new ArrayList<>();
        if (component instanceof TextComponent text && !text.content().isEmpty()) {
            out.add(new Run(text.content(), effective));
        }
        for (Component child : component.children()) {
            out.addAll(runs(child, effective));
        }
        return out;
    }

    @Test
    void bindsArgumentsByZeroBasedIndex() {
        VoyagerTranslator translator = VoyagerTranslator.of(
                Map.of("greeting", "Hello <arg:0>, you are <arg:1>"));

        String text = render(translator, Component.translatable("greeting",
                Component.text("Bob"), Component.text(2)));

        assertThat(text).isEqualTo("Hello Bob, you are 2");
    }

    @Test
    void alsoAnswersToAdventuresOwnLongerTagName() {
        VoyagerTranslator translator = VoyagerTranslator.of(Map.of("k", "[<argument:0>]"));

        assertThat(render(translator, Component.translatable("k", Component.text("x")))).isEqualTo("[x]");
    }

    /**
     * Arguments out of order and reused, which a positional formatter that simply consumed them
     * left to right would get wrong without anything else in the suite noticing.
     */
    @Test
    void argumentsMayBeUsedOutOfOrderAndMoreThanOnce() {
        VoyagerTranslator translator = VoyagerTranslator.of(Map.of("k", "<arg:1>-<arg:0>-<arg:1>"));

        String text = render(translator, Component.translatable("k",
                Component.text("first"), Component.text("second")));

        assertThat(text).isEqualTo("second-first-second");
    }

    /**
     * The reason arguments are inserted self-closing. A non-self-closing insert leaves the tag open
     * to the end of the line, so every word after a white player name would be white too — which is
     * invisible in a one-argument test and wrong in every real line.
     */
    @Test
    void anArgumentDoesNotDragItsOwnColourOverTheRestOfTheLine() {
        VoyagerTranslator translator = VoyagerTranslator.of(Map.of("k", "<c-dim>before <arg:0> after"));

        Component rendered = translate(translator,
                Component.translatable("k", Component.text("NAME", Palette.OK)));

        assertThat(PlainTextComponentSerializer.plainText().serialize(rendered))
                .isEqualTo("before NAME after");
        assertThat(runs(rendered, null))
                .containsExactly(
                        new Run("before ", Palette.DIM),
                        new Run("NAME", Palette.OK),
                        new Run(" after", Palette.DIM));
    }

    @Test
    void paletteTagsAreAvailableInsideAValue() {
        VoyagerTranslator translator = VoyagerTranslator.of(Map.of("k", "<c-speed>GO"));

        Component rendered = translate(translator, Component.translatable("k"));

        assertThat(PlainTextComponentSerializer.plainText().serialize(rendered)).isEqualTo("GO");
        assertThat(runs(rendered, null)).containsExactly(new Run("GO", Palette.SPEED));
    }

    /**
     * The check the whole class exists for. {@code {0}} does not fail at render time — it renders as
     * three literal characters in the middle of a sentence, forever — so it has to fail at load.
     */
    @Test
    void aValueWritingMessageFormatPlaceholdersIsRefusedAtLoad() {
        assertThatThrownBy(() -> VoyagerTranslator.of(Map.of("bad.key", "Hello {0}!")))
                .isInstanceOf(MalformedMessageBundleException.class)
                .hasMessageContaining("bad.key")
                .hasMessageContaining("<arg:0>");
    }

    @Test
    void aBraceThatIsNotAPlaceholderIsLeftAlone() {
        VoyagerTranslator translator = VoyagerTranslator.of(Map.of("k", "a {curly} brace"));

        assertThat(render(translator, Component.translatable("k"))).isEqualTo("a {curly} brace");
    }

    @Test
    void anUnknownKeyIsNotTranslatedSoAnotherSourceStillCan() {
        VoyagerTranslator translator = VoyagerTranslator.of(Map.of("known", "yes"));

        assertThat(translator.canTranslate("known", Locale.US)).isTrue();
        assertThat(translator.canTranslate("unknown", Locale.US)).isFalse();
        assertThat(translator.translate(
                (TranslatableComponent) Component.translatable("unknown"), Locale.US)).isNull();
    }

    /**
     * One language, every locale. A racer whose client is set to German gets English rather than a
     * raw key, which is the only behaviour worth having until somebody writes a second bundle.
     */
    @Test
    void everyLocaleGetsTheOneBundleThereIs() {
        VoyagerTranslator translator = VoyagerTranslator.of(Map.of("k", "English"));

        assertThat(translator.canTranslate("k", Locale.GERMANY)).isTrue();
        assertThat(render(translator, Component.translatable("k"))).isEqualTo("English");
    }

    /**
     * {@code null} from the MessageFormat overload is what disables the {@code {0}} path. Adventure
     * falls through to the component overload, which is the one that parses MiniMessage.
     */
    @Test
    void thereIsNoMessageFormatFormToHandOut() {
        VoyagerTranslator translator = VoyagerTranslator.of(Map.of("k", "text"));

        assertThat(translator.translate("k", Locale.US)).isNull();
    }

    @Test
    void anEmptyBundleTellsAdventureItHasNothingRatherThanLettingItAsk() {
        assertThat(VoyagerTranslator.of(Map.of()).hasAnyTranslations()).isEqualTo(TriState.FALSE);
        assertThat(VoyagerTranslator.of(Map.of("k", "v")).hasAnyTranslations()).isEqualTo(TriState.TRUE);
    }

    /**
     * The refusal names the resource it looked for. A server missing its bundle does not crash on a
     * later line — it would speak to players in dotted translation keys — so the failure has to say
     * what was not found.
     */
    @Test
    void aBundleThatIsNotOnTheClasspathIsARefusalNamingWhatWasLookedFor() {
        assertThat(new MissingMessageBundleException(VoyagerTranslator.BUNDLE_RESOURCE))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining(VoyagerTranslator.BUNDLE_RESOURCE);
    }

    @Test
    void installingAddsExactlyOneSourceToTheGlobalTranslator() {
        VoyagerTranslator translator = VoyagerTranslator.of(Map.of("install.probe", "installed"));
        try {
            translator.install();

            assertThat(PlainTextComponentSerializer.plainText().serialize(
                    GlobalTranslator.render(Component.translatable("install.probe"), Locale.US)))
                    .isEqualTo("installed");
        } finally {
            GlobalTranslator.translator().removeSource(translator);
        }
    }

    @Test
    void reportsHowManyMessagesItHolds() {
        assertThat(VoyagerTranslator.of(Map.of("a", "1", "b", "2")).size()).isEqualTo(2);
    }
}
