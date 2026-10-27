package net.elytrarace.voyager.platform.tutorial;

import net.elytrarace.voyager.platform.text.Messages;
import net.kyori.adventure.text.TranslatableComponent;

import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The tutorial's page model: five pages in a fixed order, Back on every page but the first, Next on every page but the
 * last, and a page that starts at page one every time it opens. Pure values, so no server is needed.
 */
class TutorialPagesTest {

    @Test
    void thereAreFivePagesInTheStatedOrder() {
        assertThat(TutorialPages.COUNT).isEqualTo(5);
        assertThat(titleKeys())
                .containsExactly("voyager.tutorial.page.1.title", "voyager.tutorial.page.2.title",
                        "voyager.tutorial.page.3.title", "voyager.tutorial.page.4.title",
                        "voyager.tutorial.page.5.title");
    }

    @Test
    void theFirstPageIsHowToPlay() {
        assertThat(TutorialPages.FIRST_PAGE).isEqualTo(1);
        assertThat(((TranslatableComponent) Messages.tutorialPageTitle(TutorialPages.FIRST_PAGE)).key())
                .isEqualTo("voyager.tutorial.page.1.title");
    }

    @Test
    void theFirstPageHasNoBackButton() {
        assertThat(TutorialPages.hasBack(TutorialPages.FIRST_PAGE)).isFalse();
        assertThat(TutorialPages.hasNext(TutorialPages.FIRST_PAGE)).isTrue();
    }

    @Test
    void theLastPageHasNoNextButton() {
        assertThat(TutorialPages.hasNext(TutorialPages.COUNT)).isFalse();
        assertThat(TutorialPages.hasBack(TutorialPages.COUNT)).isTrue();
    }

    @Test
    void aPageKeyNamesItsPageAndTheCloseKeyNamesNone() {
        for (int page = 1; page <= TutorialPages.COUNT; page++) {
            assertThat(TutorialPages.indexOf(TutorialPages.keyOf(page))).isEqualTo(OptionalInt.of(page));
        }
        assertThat(TutorialPages.indexOf(TutorialPages.CLOSE_KEY)).isEqualTo(OptionalInt.empty());
    }

    private static java.util.List<String> titleKeys() {
        return java.util.stream.IntStream.rangeClosed(1, TutorialPages.COUNT)
                .mapToObj(page -> ((TranslatableComponent) Messages.tutorialPageTitle(page)).key())
                .toList();
    }
}
