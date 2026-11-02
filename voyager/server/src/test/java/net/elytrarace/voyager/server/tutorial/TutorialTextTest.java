package net.elytrarace.voyager.server.tutorial;

import net.elytrarace.voyager.platform.text.Messages;
import net.elytrarace.voyager.platform.text.VoyagerTranslator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.translation.GlobalTranslator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every text the tutorial dialogs show, rendered in each shipped locale, is real text.
 *
 * <p>A translation key that has no value in a locale renders as the dotted key itself, and a value that still holds
 * MiniMessage or MessageFormat syntax renders the syntax. Both reach a racer as visible junk, so each text is rendered
 * through {@link Messages} with the arguments a page really gets, and read back. The shipped bundle is installed for
 * each test and removed after it.
 */
class TutorialTextTest {

    private static final Component CUP_NAME = Component.text("Alpha Cup");
    private static final List<Component> CUP_LINES = List.of(Component.text("1. Ridge"));

    private VoyagerTranslator translator;

    @BeforeEach
    void installTheShippedBundle() {
        translator = VoyagerTranslator.fromClasspath(VoyagerTranslator.BUNDLE);
        translator.install();
    }

    @AfterEach
    void removeIt() {
        GlobalTranslator.translator().removeSource(translator);
    }

    @ParameterizedTest
    @ValueSource(strings = {"en_US", "de_DE"})
    void everyTutorialTextIsRealTextInEveryShippedLocale(String tag) {
        Locale locale = Locale.forLanguageTag(tag.replace('_', '-'));
        Map<String, Component> texts = tutorialTexts();
        List<String> problems = new ArrayList<>();
        texts.forEach((name, component) -> {
            String rendered = PlainTextComponentSerializer.plainText()
                    .serialize(GlobalTranslator.render(component, locale));
            String problem = problemWith(rendered);
            if (problem != null) {
                problems.add("%s in %s: %s, was '%s'".formatted(name, tag, problem, rendered));
            }
        });

        assertThat(problems).as("tutorial texts that are not real text").isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"en_US", "de_DE"})
    void theCupPageNamesTheCupInEveryShippedLocale(String tag) {
        Locale locale = Locale.forLanguageTag(tag.replace('_', '-'));

        String title = PlainTextComponentSerializer.plainText()
                .serialize(GlobalTranslator.render(Messages.tutorialCupTitle(CUP_NAME), locale));

        assertThat(title).as("the cup page's title in %s".formatted(tag)).contains("Alpha Cup");
    }

    /** Every tutorial text, by the name a failure message can point at. */
    private static Map<String, Component> tutorialTexts() {
        Map<String, Component> texts = new LinkedHashMap<>();
        for (int page = 1; page <= 5; page++) {
            texts.put("page %d title".formatted(page), Messages.tutorialPageTitle(page));
            texts.put("page %d body".formatted(page), Messages.tutorialPageBody(page));
        }
        texts.put("page 6 title", Messages.tutorialCupTitle(CUP_NAME));
        texts.put("page 6 body", Messages.tutorialCupBody(CUP_LINES));
        texts.put("page 6 authors", Messages.tutorialCupAuthors("Ann"));
        texts.put("Back button", Messages.tutorialBack());
        texts.put("Next button", Messages.tutorialNext());
        texts.put("Close button", Messages.tutorialClose());
        return texts;
    }

    /** Why a rendered text is not real text, or {@code null} when it is. */
    private static String problemWith(String rendered) {
        if (rendered.isBlank()) {
            return "it is blank";
        }
        if (rendered.startsWith("voyager.")) {
            return "it is a translation key with no value in this locale";
        }
        if (rendered.contains("<") || rendered.contains("{") || rendered.contains("}")) {
            return "it still holds markup or a placeholder";
        }
        return null;
    }
}
