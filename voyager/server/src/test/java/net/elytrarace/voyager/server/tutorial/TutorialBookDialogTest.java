package net.elytrarace.voyager.server.tutorial;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.GameMode;
import net.elytrarace.voyager.api.race.GuideLine;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;
import net.elytrarace.voyager.platform.catalog.CatalogHolder;
import net.elytrarace.voyager.platform.catalog.CatalogSnapshot;
import net.elytrarace.voyager.platform.catalog.LoadedCatalog;
import net.elytrarace.voyager.platform.text.VoyagerTranslator;
import net.elytrarace.voyager.platform.tutorial.TutorialBook;
import net.elytrarace.voyager.platform.tutorial.TutorialPages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgument;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.translation.GlobalTranslator;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.dialog.Dialog;
import net.minestom.server.dialog.DialogBody;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.network.packet.server.common.ShowDialogPacket;
import net.minestom.testing.Collector;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import net.minestom.testing.TestConnection;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The tutorial book's dialogs as the packet carries them, read without any rendering in the test.
 *
 * <h2>Why the test does not render the packet itself</h2>
 *
 * <p>Minestom translates the components of an outgoing packet only when the packet implements
 * {@code ServerPacket.ComponentHolding}, and {@code ShowDialogPacket} does not. A translation key in a dialog therefore
 * reaches the client as the key itself, because the client's own language files do not know the game's keys. So the
 * text has to be rendered by the server, for each racer, before the packet is written. These tests read what the packet
 * holds and fail on any translation key left in it.
 *
 * <p>The shipped bundle is installed for each test and removed after it, so no test sees another's translator.
 */
@EnvTest
class TutorialBookDialogTest {

    private static final Pos AT = new Pos(0, 42, 0);

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

    @Test
    void theFirstPageTitleIsSentAsRealTextForAnEnglishRacer(Env env) {
        Shown shown = openBook(env, Locale.US);

        assertThat(plainText(shown.title()))
                .as("the first page's title, as the packet carries it, for an English racer")
                .isEqualTo("How to play");
    }

    @Test
    void theFirstPageTitleIsSentAsRealTextForAGermanRacer(Env env) {
        Shown shown = openBook(env, Locale.GERMANY);

        assertThat(plainText(shown.title()))
                .as("the first page's title, as the packet carries it, for a German racer")
                .isEqualTo("So spielst du");
    }

    @Test
    void theFirstPageButtonsAreSentAsRealTextForAGermanRacer(Env env) {
        Shown shown = openBook(env, Locale.GERMANY);

        assertThat(shown.buttons().stream().map(TutorialBookDialogTest::plainText).toList())
                .as("the first page's buttons for a German racer: Next, then Close")
                .containsExactly("Weiter →", "Schließen");
    }

    @Test
    void theSentDialogHoldsNoTranslationKeyForTheClientToShowAsIs(Env env) {
        Shown shown = openBook(env, Locale.GERMANY);

        assertThat(shown.translationKeysLeft())
                .as("translation keys the client would show as raw text")
                .isEmpty();
    }

    @Test
    void pressingNextSendsTheSecondPageAsRealTextInTheRacersLanguage(Env env) {
        Instance instance = env.createFlatInstance();
        TestConnection connection = env.createConnection();
        Collector<ShowDialogPacket> dialogs = connection.trackIncoming(ShowDialogPacket.class);
        Player racer = connection.connect(instance, AT);
        racer.setLocale(Locale.GERMANY);
        TutorialBook book = bookWithOneCup();

        book.open(racer);
        book.click(racer, TutorialPages.keyOf(2));

        List<Shown> shown = dialogs.collect().stream().map(TutorialBookDialogTest::shownBy).toList();
        assertThat(shown).as("the book shows the first page, then the second").hasSize(2);
        assertThat(plainText(shown.get(1).title()))
                .as("the second page's title for a German racer")
                .isEqualTo("Raketen");
    }

    @Test
    void twoRacersOnOneServerEachReceiveTheirOwnLanguage(Env env) {
        Instance instance = env.createFlatInstance();
        TutorialBook book = bookWithOneCup();

        Shown english = openFor(env, instance, book, Locale.US);
        Shown german = openFor(env, instance, book, Locale.GERMANY);

        assertThat(plainText(english.title())).as("the English racer's title").isEqualTo("How to play");
        assertThat(plainText(german.title())).as("the German racer's title").isEqualTo("So spielst du");
    }

    @Test
    void theCupPageIsSentInTheRacersLanguageWithItsMaps(Env env) {
        Instance instance = env.createFlatInstance();
        TestConnection connection = env.createConnection();
        Collector<ShowDialogPacket> dialogs = connection.trackIncoming(ShowDialogPacket.class);
        Player racer = connection.connect(instance, AT);
        racer.setLocale(Locale.GERMANY);

        bookWithOneCup().click(racer, TutorialPages.keyOf(TutorialPages.COUNT));

        Shown cupPage = shownBy(dialogs.collect().getFirst());
        assertThat(plainText(cupPage.title())).as("the cup page's title for a German racer").isEqualTo("Dein Cup: Tour");
        assertThat(plainText(cupPage.body().getFirst())).as("the cup page's body lists its map in order").contains("1. Ridge");
        assertThat(cupPage.translationKeysLeft()).as("translation keys left on the cup page").isEmpty();
    }

    /** Connects one racer on a new instance, opens the book for them, and returns the one dialog they were sent. */
    private Shown openBook(Env env, Locale locale) {
        return openFor(env, env.createFlatInstance(), bookWithOneCup(), locale);
    }

    /** Connects one racer with the given client locale, opens the book for them, and returns the one dialog sent. */
    private static Shown openFor(Env env, Instance instance, TutorialBook book, Locale locale) {
        TestConnection connection = env.createConnection();
        Collector<ShowDialogPacket> dialogs = connection.trackIncoming(ShowDialogPacket.class);
        Player racer = connection.connect(instance, AT);
        racer.setLocale(locale);

        book.open(racer);

        List<ShowDialogPacket> shown = dialogs.collect();
        assertThat(shown).as("the book shows exactly one dialog").hasSize(1);
        return shownBy(shown.getFirst());
    }

    private static TutorialBook bookWithOneCup() {
        MapDefinition ridge = new MapDefinition("ridge", "ridge-world", new Vec3(0, 64, 0),
                List.of(new Ring(0, new Vec3(0, 64, 10), new Vec3(0, 0, 1), 3, 10, RingType.STANDARD)),
                Duration.ofSeconds(60), new BoostConfig(12, 25), new GuideLine(List.of(), 2, 1.0));
        CupDefinition cup = new CupDefinition("tour", List.of("ridge"), GameMode.RACE);
        CatalogHolder catalog = new CatalogHolder(new LoadedCatalog(
                new CatalogSnapshot(Map.of("ridge", ridge), Map.of("tour", cup)), cup, Instant.EPOCH));
        return new TutorialBook(catalog);
    }

    private static Shown shownBy(ShowDialogPacket packet) {
        Dialog dialog = packet.dialog().asValue();
        assertThat(dialog).as("the dialog is a multi-action dialog with buttons").isInstanceOf(Dialog.MultiAction.class);
        Dialog.MultiAction multiAction = (Dialog.MultiAction) dialog;
        List<Component> buttons = new ArrayList<>();
        multiAction.actions().forEach(action -> buttons.add(action.label()));
        if (multiAction.exitAction() != null) {
            buttons.add(multiAction.exitAction().label());
        }
        List<Component> body = new ArrayList<>();
        multiAction.metadata().body().forEach(part -> {
            if (part instanceof DialogBody.PlainMessage message) {
                body.add(message.contents());
            }
        });
        return new Shown(multiAction.metadata().title(), body, buttons);
    }

    private static String plainText(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    /** The dialog a racer was sent: its title, its body, and its buttons, exactly as the packet holds them. */
    private record Shown(Component title, List<Component> body, List<Component> buttons) {

        /** Every translation key in the title, the body and the buttons, which the client would show as raw text. */
        List<String> translationKeysLeft() {
            List<String> keys = new ArrayList<>();
            List<Component> everything = new ArrayList<>(body);
            everything.add(title);
            everything.addAll(buttons);
            everything.forEach(component -> collectKeys(component, keys));
            return keys;
        }

        private static void collectKeys(Component component, List<String> keys) {
            if (component instanceof TranslatableComponent translatable) {
                keys.add(translatable.key());
                for (TranslationArgument argument : translatable.arguments()) {
                    if (argument.value() instanceof ComponentLike like) {
                        collectKeys(like.asComponent(), keys);
                    }
                }
            }
            component.children().forEach(child -> collectKeys(child, keys));
        }
    }
}
