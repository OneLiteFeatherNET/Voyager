package net.elytrarace.voyager.platform.cup;

import net.elytrarace.voyager.race.flow.RaceClock;
import net.elytrarace.voyager.race.standing.MapStanding;
import net.elytrarace.voyager.race.standing.MapStanding.Entry;
import net.elytrarace.voyager.race.standing.MapStanding.Outcome;
import net.elytrarace.voyager.race.standing.PersonalBestComparison;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The place line a finisher is sent after a map. The line is built by {@link CupAnnouncer#placeLine} from a standing alone,
 * so each test builds its own standing and needs no racer, server or clock.
 */
class CupAnnouncerStandingTest {

    private static final Duration STEP = Duration.ofMillis(50);

    private static final UUID ADA = new UUID(0, 1);
    private static final UUID BEN = new UUID(0, 2);
    private static final UUID CAL = new UUID(0, 3);
    private static final UUID DAN = new UUID(0, 4);
    private static final UUID EVE = new UUID(0, 5);
    private static final UUID COM_ONE = new UUID(0, 6);

    @Test
    void thePlaceLineReadsThePlaceOutOfEveryRacerWhoStartedWithTheGapToTheRacerAhead() {
        MapStanding standing = MapStanding.of(List.of(
                finished(ADA, 1224), finished(BEN, 1248), finished(CAL, 1290), dnf(DAN), finished(EVE, 1300)));

        Component line = CupAnnouncer.placeLine(BEN, standing, names(), Optional.empty()).orElseThrow();

        assertThat(translatable(line).key()).isEqualTo("voyager.result.place.behind");
        assertThat(argumentsOf(line))
                .as("second of five, 1.2 s behind Ada: the DNF counts in the five")
                .containsExactly("2", "5", "1.2", "Ada");
    }

    @Test
    void theFirstPlaceIsAheadOfTheRacerBehindIt() {
        MapStanding standing = MapStanding.of(List.of(finished(ADA, 1224), finished(BEN, 1248), finished(CAL, 1290)));

        Component line = CupAnnouncer.placeLine(ADA, standing, names(), Optional.empty()).orElseThrow();

        assertThat(translatable(line).key()).isEqualTo("voyager.result.place.ahead");
        assertThat(argumentsOf(line))
                .as("first of three, 1.2 s ahead of Ben, the racer directly behind")
                .containsExactly("1", "3", "1.2", "Ben");
    }

    @Test
    void aComputerRacerAheadIsNamedAndCounted() {
        MapStanding standing = MapStanding.of(List.of(
                finished(COM_ONE, 1200, true), finished(BEN, 1230)));

        Component line = CupAnnouncer.placeLine(BEN, standing, names(), Optional.empty()).orElseThrow();

        assertThat(translatable(line).key()).isEqualTo("voyager.result.place.behind");
        assertThat(argumentsOf(line))
                .as("second of two, 1.5 s behind the computer racer, which is named COM 1 and counts in the total")
                .containsExactly("2", "2", "1.5", "@voyager.computer.name");
    }

    @Test
    void aComputerRacerHasNoPlaceLineOfItsOwn() {
        MapStanding standing = MapStanding.of(List.of(finished(COM_ONE, 1200, true), finished(BEN, 1230)));

        assertThat(CupAnnouncer.placeLine(COM_ONE, standing, names(), Optional.empty()))
                .as("a computer racer has no client, so no line is made for it")
                .isEmpty();
    }

    @Test
    void aRacerWhoDidNotFinishGetsNoPlaceLine() {
        MapStanding standing = MapStanding.of(List.of(finished(ADA, 1224), dnf(BEN)));

        assertThat(CupAnnouncer.placeLine(BEN, standing, names(), Optional.empty()))
                .as("the did-not-finish line is the only line for a racer who did not finish")
                .isEmpty();
    }

    @Test
    void withStatisticsOffNoLineAboutTheMapCarriesABestTime() {
        MapStanding standing = MapStanding.of(List.of(finished(ADA, 1224), finished(BEN, 1248)));

        Component line = CupAnnouncer.placeLine(BEN, standing, names(), Optional.empty()).orElseThrow();

        assertThat(line.children())
                .as("no personal best clause is appended when no best is read")
                .isEmpty();
        assertThat(keysOf(line))
                .noneMatch(key -> key.startsWith("voyager.result.pb."));
    }

    @Test
    void aRankedFinishWithStatisticsOnCarriesThePersonalBestClause() {
        MapStanding standing = MapStanding.of(List.of(finished(ADA, 1224), finished(BEN, 1248)));
        PersonalBestComparison newBest = PersonalBestComparison.of(Duration.ofMillis(59_100), Duration.ofMillis(61_200));

        Component line = CupAnnouncer.placeLine(BEN, standing, names(), Optional.of(newBest)).orElseThrow();

        assertThat(line.children()).hasSize(1);
        assertThat(translatable(line.children().getFirst()).key())
                .as("the clause is the new best, with its delta, after the place")
                .isEqualTo("voyager.result.pb.new");
        assertThat(argumentsOf(line.children().getFirst())).containsExactly("2.1");
    }

    @Test
    void aFirstRunOnAMapStatesANewBestWithNoDelta() {
        MapStanding standing = MapStanding.of(List.of(finished(ADA, 1224)));

        Component line = CupAnnouncer.placeLine(ADA, standing, names(),
                Optional.of(PersonalBestComparison.of(Duration.ofMillis(61_200), null))).orElseThrow();

        assertThat(translatable(line.children().getFirst()).key()).isEqualTo("voyager.result.pb.first");
    }

    @Test
    void aRacerAloneOnTheMapHasAPlaceAndNoRival() {
        MapStanding standing = MapStanding.of(List.of(finished(ADA, 1224)));

        Component line = CupAnnouncer.placeLine(ADA, standing, names(), Optional.empty()).orElseThrow();

        assertThat(translatable(line).key()).isEqualTo("voyager.result.place.alone");
        assertThat(argumentsOf(line)).containsExactly("1", "1");
    }

    // ---------------------------------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------------------------------

    /** The names the tests read: a human by name, and a computer racer as its translated {@code COM n} name. */
    private static Function<UUID, Component> names() {
        return id -> {
            if (id.equals(ADA)) {
                return Component.text("Ada");
            }
            if (id.equals(BEN)) {
                return Component.text("Ben");
            }
            if (id.equals(COM_ONE)) {
                return Component.translatable("voyager.computer.name", Component.text(1));
            }
            return Component.text("someone");
        };
    }

    private static Entry finished(UUID racer, int tick) {
        return new Entry(racer, false, Outcome.FINISHED, new RaceClock(tick, STEP));
    }

    private static Entry finished(UUID racer, int tick, boolean computer) {
        return new Entry(racer, computer, Outcome.FINISHED, new RaceClock(tick, STEP));
    }

    private static Entry dnf(UUID racer) {
        return new Entry(racer, false, Outcome.DID_NOT_FINISH, null);
    }

    private static TranslatableComponent translatable(Component component) {
        assertThat(component).isInstanceOf(TranslatableComponent.class);
        return (TranslatableComponent) component;
    }

    /** Each argument as text, or {@code @key} for an argument that is itself a translation. */
    private static List<String> argumentsOf(Component component) {
        return translatable(component).arguments().stream()
                .map(argument -> (Component) argument.value())
                .map(value -> value instanceof TranslatableComponent nested
                        ? "@" + nested.key()
                        : PlainTextComponentSerializer.plainText().serialize(value))
                .toList();
    }

    /** Every translation key in the line and in its appended children. */
    private static List<String> keysOf(Component component) {
        List<String> keys = new ArrayList<>();
        if (component instanceof TranslatableComponent translatable) {
            keys.add(translatable.key());
        }
        for (Component child : component.children()) {
            keys.addAll(keysOf(child));
        }
        return keys;
    }
}
