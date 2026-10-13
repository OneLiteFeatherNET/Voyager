package net.elytrarace.voyager.platform.text;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LocaleChainTest {

    private static final Set<String> LOADED = Set.of("en_US", "de_DE");

    @Test
    void aRegionalTagIsTriedBeforeItsLanguage() {
        assertThat(LocaleChain.candidates(Locale.GERMANY, LOADED)).containsExactly("de_DE", "en_US");
        assertThat(LocaleChain.candidates(new Locale("de", "AT"), Set.of("en_US", "de_DE", "de_AT")))
                .containsExactly("de_AT", "de_DE", "en_US");
    }

    @Test
    void aRegionalTagWithNoBundleFallsToTheLoadedBundleOfItsLanguage() {
        assertThat(LocaleChain.candidates(new Locale("de", "AT"), LOADED)).containsExactly("de_DE", "en_US");
    }

    @Test
    void anUnsupportedLanguageFallsToEnglish() {
        assertThat(LocaleChain.candidates(Locale.FRANCE, LOADED)).containsExactly("en_US");
    }

    @Test
    void enUsIsTheOnlyCandidateForEnUs() {
        assertThat(LocaleChain.candidates(Locale.US, LOADED)).containsExactly("en_US");
    }

    @Test
    void anEnglishRegionFallsToTheEnglishBundle() {
        assertThat(LocaleChain.candidates(Locale.UK, LOADED)).containsExactly("en_US");
    }

    @Test
    void aLocaleWithNoLanguageFallsToEnglish() {
        assertThat(LocaleChain.candidates(Locale.ROOT, LOADED)).containsExactly("en_US");
    }

    @Test
    void theFallbackIsTriedExactlyOnce() {
        List<String> chain = LocaleChain.candidates(Locale.US, Set.of("en_US"));

        assertThat(chain).containsOnlyOnce("en_US");
    }
}
