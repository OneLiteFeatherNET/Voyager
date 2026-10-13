package net.elytrarace.voyager.platform.text;

import net.elytrarace.voyager.platform.text.exception.MalformedMessageBundleException;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageCatalogueTest {

    private static final Map<String, String> ENGLISH = Map.of(
            "voyager.lobby.waiting", "Waiting for racers: <arg:0> of <arg:1> online",
            "voyager.lobby.cancelled", "Start cancelled.");

    private static final Map<String, String> GERMAN = Map.of(
            "voyager.lobby.waiting", "Warte auf Racer: <arg:0> von <arg:1> online");

    private static final MessageCatalogue CATALOGUE = MessageCatalogue.of(Map.of(
            "en_US", ENGLISH,
            "de_DE", GERMAN));

    @Test
    void aGermanKeyAnswersInGerman() {
        MessageCatalogue.Answer answer = CATALOGUE.answer("voyager.lobby.waiting", Locale.GERMANY);

        assertThat(answer).isNotNull();
        assertThat(answer.tag()).isEqualTo("de_DE");
        assertThat(answer.source()).isEqualTo("Warte auf Racer: <arg:0> von <arg:1> online");
    }

    @Test
    void aKeyOnlyInEnglishAnswersInEnglishAndSaysSo() {
        MessageCatalogue.Answer answer = CATALOGUE.answer("voyager.lobby.cancelled", Locale.GERMANY);

        assertThat(answer).isNotNull();
        assertThat(answer.tag()).as("the tag that answered").isEqualTo("en_US");
        assertThat(answer.source()).isEqualTo("Start cancelled.");
    }

    @Test
    void aMissingKeyHasNoAnswerSoTheDottedKeyIsNeverInventedHere() {
        assertThat(CATALOGUE.answer("voyager.nowhere", Locale.GERMANY)).isNull();
        assertThat(CATALOGUE.contains("voyager.nowhere", Locale.US)).isFalse();
    }

    @Test
    void aCatalogueHoldsOneTablePerTag() {
        assertThat(CATALOGUE.tags()).containsExactlyInAnyOrder("en_US", "de_DE");
    }

    @Test
    void theUntranslatedKeysOfALocaleAreReportedPerLocale() {
        assertThat(CATALOGUE.untranslated()).containsOnlyKeys("de_DE");
        assertThat(CATALOGUE.untranslated().get("de_DE")).containsExactly("voyager.lobby.cancelled");
    }

    @Test
    void aCatalogueWithoutTheEnglishFallbackIsRefused() {
        assertThatThrownBy(() -> MessageCatalogue.of(Map.of("de_DE", GERMAN)))
                .isInstanceOf(MalformedMessageBundleException.class)
                .hasMessageContaining("en_US");
    }

    @Test
    void aGermanValueThatDropsAnArgumentIsRefusedNamingTheKeyAndTheLocale() {
        assertThatThrownBy(() -> MessageCatalogue.of(Map.of(
                "en_US", ENGLISH,
                "de_DE", Map.of("voyager.lobby.waiting", "Warte: <arg:0>"))))
                .isInstanceOf(MalformedMessageBundleException.class)
                .hasMessageContaining("voyager.lobby.waiting")
                .hasMessageContaining("de_DE");
    }

    @Test
    void aGermanValueWithAMessageFormatPlaceholderIsRefused() {
        assertThatThrownBy(() -> MessageCatalogue.of(Map.of(
                "en_US", ENGLISH,
                "de_DE", Map.of("voyager.lobby.cancelled", "Abbruch {0}"))))
                .isInstanceOf(MalformedMessageBundleException.class)
                .hasMessageContaining("voyager.lobby.cancelled");
    }

    @Test
    void theArgumentsAndPaletteTagsOfAValueAreListedSorted() {
        assertThat(MessageCatalogue.placeholders("<c-dim>a <arg:1> <argument:0> <m-gold>b<c-dim>"))
                .containsExactly("<arg:1>", "<argument:0>", "<c-dim>", "<c-dim>", "<m-gold>");
    }
}
