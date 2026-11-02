package net.elytrarace.voyager.race.standing;

import net.elytrarace.voyager.race.flow.RaceClock;
import net.elytrarace.voyager.race.standing.MapStanding.Entry;
import net.elytrarace.voyager.race.standing.MapStanding.Outcome;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The order and the gaps of a map's racers. Every racer is built here from a fixed id and a fixed tick, so the tests
 * need no clock and no server.
 */
class MapStandingTest {

    private static final Duration STEP = Duration.ofMillis(50);

    private static final UUID ADA = new UUID(0, 1);
    private static final UUID BEN = new UUID(0, 2);
    private static final UUID CAL = new UUID(0, 3);
    private static final UUID DAN = new UUID(0, 4);
    private static final UUID COM_ONE = new UUID(0, 5);
    private static final UUID EVE = new UUID(0, 6);

    @Test
    void finishersAreOrderedByFinishTimeWithGapsToTheLeaderThenRacingThenDnf() {
        MapStanding standing = MapStanding.of(List.of(
                dnf(ADA),
                finished(BEN, 1224),
                racing(CAL),
                finished(DAN, 1270),
                finished(EVE, 1248)));

        assertThat(standing.entries()).extracting(Entry::racer)
                .as("finishers by time, then the racer still racing, then the racer who did not finish")
                .containsExactly(BEN, EVE, DAN, CAL, ADA);
        assertThat(standing.gapToLeader(BEN))
                .as("the leader's gap is zero")
                .contains(Duration.ZERO);
        assertThat(standing.gapToLeader(EVE))
                .as("1.2 s behind the leader, from the 62.4 s and 61.2 s finishes")
                .contains(Duration.ofMillis(1_200));
        assertThat(standing.gapToLeader(DAN))
                .as("2.3 s behind the leader, the racer last to finish")
                .contains(Duration.ofMillis(2_300));
        assertThat(standing.gapToLeader(CAL))
                .as("a racer still racing has no gap yet")
                .isEmpty();
    }

    @Test
    void aRacerAloneOnTheMapHasNoGapAndComputerRacersAreInTheList() {
        MapStanding alone = MapStanding.of(List.of(finished(ADA, 1224)));

        assertThat(alone.placeOf(ADA))
                .as("the only racer on the map is first of one")
                .hasValueSatisfying(place -> {
                    assertThat(place.place()).isEqualTo(1);
                    assertThat(place.of()).isEqualTo(1);
                    assertThat(place.rival()).as("no other racer is named").isEmpty();
                    assertThat(place.gap()).as("and no gap is measured").isEmpty();
                });

        MapStanding withComputer = MapStanding.of(List.of(finished(ADA, 1224), finished(COM_ONE, 1260, true)));

        assertThat(withComputer.size())
                .as("a computer racer counts in the total of a place")
                .isEqualTo(2);
        assertThat(withComputer.placeOf(ADA).orElseThrow().of())
                .as("so the human is first of two, not first of one")
                .isEqualTo(2);
    }

    @Test
    void aSecondPlaceIsBehindTheRacerAheadAndAFirstPlaceIsAheadOfTheRacerBehind() {
        MapStanding standing = MapStanding.of(List.of(finished(ADA, 1224), finished(BEN, 1248), finished(CAL, 1280)));

        assertThat(standing.placeOf(BEN))
                .hasValueSatisfying(place -> {
                    assertThat(place.place()).isEqualTo(2);
                    assertThat(place.rival()).contains(ADA);
                    assertThat(place.gap()).contains(Duration.ofMillis(1_200));
                    assertThat(place.rivalIsAhead()).isTrue();
                });
        assertThat(standing.placeOf(ADA))
                .hasValueSatisfying(place -> {
                    assertThat(place.place()).isEqualTo(1);
                    assertThat(place.rival()).as("the racer directly behind").contains(BEN);
                    assertThat(place.gap()).contains(Duration.ofMillis(1_200));
                    assertThat(place.rivalIsAhead()).isFalse();
                });
    }

    @Test
    void aRacerWhoDidNotFinishHasNoPlace() {
        MapStanding standing = MapStanding.of(List.of(finished(ADA, 1224), dnf(BEN)));

        assertThat(standing.placeOf(BEN))
                .as("a racer who did not finish is in the total but has no place")
                .isEmpty();
        assertThat(standing.placeOf(ADA).orElseThrow().of())
                .as("and the racer who did not finish still counts in the total")
                .isEqualTo(2);
    }

    @Test
    void aTieIsBrokenByTheEarlierFinishTick() {
        MapStanding standing = MapStanding.of(List.of(finished(ADA, 1250), finished(BEN, 1248)));

        assertThat(standing.entries()).extracting(Entry::racer)
                .as("the earlier finish tick is ahead")
                .containsExactly(BEN, ADA);
    }

    @Test
    void aFinisherNeedsAFinishTime() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> new Entry(ADA, false, Outcome.FINISHED, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Entry finished(UUID racer, int tick) {
        return finished(racer, tick, false);
    }

    private static Entry finished(UUID racer, int tick, boolean computer) {
        return new Entry(racer, computer, Outcome.FINISHED, new RaceClock(tick, STEP));
    }

    private static Entry racing(UUID racer) {
        return new Entry(racer, false, Outcome.RACING, null);
    }

    private static Entry dnf(UUID racer) {
        return new Entry(racer, false, Outcome.DID_NOT_FINISH, null);
    }
}
