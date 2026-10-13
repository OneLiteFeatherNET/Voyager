package net.elytrarace.voyager.platform.stats;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The per-racer counters of one map, with no server and no player. Every test builds its own tally and uses two
 * racers, so a tally that kept one counter for everybody cannot pass.
 */
class RacerTallyTest {

    private static final Duration STEP = Duration.ofMillis(50);
    private static final UUID FIRST = UUID.fromString("00000000-0000-0000-0000-00000000a001");
    private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-00000000a002");

    @Test
    void aBoostIsCountedOncePerAcceptedBoost() {
        RacerTally tally = new RacerTally(STEP);

        tally.recordBoost(FIRST);

        assertThat(tally.boosts(FIRST)).as("boosts of the racer who boosted").isEqualTo(1);
        assertThat(tally.boosts(SECOND)).as("boosts of the other racer").isZero();
    }

    @Test
    void aResetIsCountedPerReset() {
        RacerTally tally = new RacerTally(STEP);

        tally.recordReset(FIRST);
        tally.recordReset(FIRST);

        assertThat(tally.resets(FIRST)).as("resets of the racer who reset twice").isEqualTo(2);
        assertThat(tally.resets(SECOND)).as("resets of the other racer").isZero();
    }

    @Test
    void flightTimeIsTheGlidingTicksTimesFiftyMilliseconds() {
        RacerTally tally = new RacerTally(STEP);

        tally.recordGlidingTick(FIRST);
        tally.recordGlidingTick(FIRST);
        tally.recordGlidingTick(FIRST);

        assertThat(tally.glidingTicks(FIRST)).as("gliding ticks").isEqualTo(3);
        assertThat(tally.flightTime(FIRST)).as("flight time").isEqualTo(Duration.ofMillis(150));
        assertThat(tally.flightTime(SECOND)).as("flight time of the other racer").isEqualTo(Duration.ZERO);
    }

    @Test
    void theTallyIsClearedAtTheStartOfTheNextMap() {
        RacerTally tally = new RacerTally(STEP);
        tally.recordBoost(FIRST);
        tally.recordReset(FIRST);
        tally.recordGlidingTick(FIRST);

        tally.clear();

        assertThat(tally.boosts(FIRST)).as("boosts after the clear").isZero();
        assertThat(tally.resets(FIRST)).as("resets after the clear").isZero();
        assertThat(tally.flightTime(FIRST)).as("flight time after the clear").isEqualTo(Duration.ZERO);
    }
}
