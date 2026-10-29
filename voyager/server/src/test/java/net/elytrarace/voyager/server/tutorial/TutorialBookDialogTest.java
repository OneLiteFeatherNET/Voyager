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
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import net.elytrarace.voyager.platform.text.VoyagerTranslator;
import net.elytrarace.voyager.platform.tutorial.TutorialBook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.translation.GlobalTranslator;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.dialog.Dialog;
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

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The tutorial book opens its first page as a dialog, in the racer's own language. The shipped bundle is installed for each
 * test and removed after it, so no test sees another's translator.
 *
 * <p>Minestom applies the racer's locale to a packet only when its automatic translation flag is on, and that flag is read once
 * per JVM. So each test renders the dialog the packet carries in the locale of the racer, the way the client shows it: in
 * English for an English racer, in German for a German one.
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
    void theBookOpensTheFirstPageInEnglishForAnEnglishRacer(Env env) {
        Shown shown = openBook(env);

        assertThat(renderIn(shown.title(), Locale.US))
                .as("the first page's title for an English racer")
                .isEqualTo("How to play");
    }

    @Test
    void theBookOpensTheFirstPageInGermanForAGermanRacer(Env env) {
        Shown shown = openBook(env);

        assertThat(renderIn(shown.title(), Locale.GERMANY))
                .as("the first page's title for a German racer")
                .isEqualTo("So spielst du");
    }

    /** Connects a racer, opens the book for them, and returns the one dialog the racer was shown. */
    private static Shown openBook(Env env) {
        Instance instance = env.createFlatInstance();
        TestConnection connection = env.createConnection();
        Collector<ShowDialogPacket> dialogs = connection.trackIncoming(ShowDialogPacket.class);
        Player racer = connection.connect(instance, AT);

        MapDefinition ridge = new MapDefinition("ridge", "ridge-world", new Vec3(0, 64, 0),
                List.of(new Ring(0, new Vec3(0, 64, 10), new Vec3(0, 0, 1), 3, 10, RingType.STANDARD)),
                Duration.ofSeconds(60), new BoostConfig(12, 25), new GuideLine(List.of(), 2, 1.0));
        CupDefinition cup = new CupDefinition("tour", List.of("ridge"), GameMode.RACE);
        CatalogHolder catalog = new CatalogHolder(new LoadedCatalog(
                new CatalogSnapshot(Map.of("ridge", ridge), Map.of("tour", cup)), cup, Instant.EPOCH));
        new TutorialBook(catalog).open(racer);

        List<ShowDialogPacket> shown = dialogs.collect();
        assertThat(shown).as("the book shows exactly one dialog").hasSize(1);
        Dialog dialog = shown.getFirst().dialog().asValue();
        assertThat(dialog).as("the dialog is a multi-action dialog with buttons").isInstanceOf(Dialog.MultiAction.class);
        return new Shown(((Dialog.MultiAction) dialog).metadata().title());
    }

    private static String renderIn(Component component, Locale locale) {
        return PlainTextComponentSerializer.plainText().serialize(GlobalTranslator.render(component, locale));
    }

    /** The title of the dialog the racer was shown. */
    private record Shown(Component title) {
    }
}
