package net.elytrarace.voyager.platform.hud;

import net.elytrarace.voyager.race.flow.RacePhase;
import net.elytrarace.voyager.race.flow.RaceState;
import net.elytrarace.voyager.race.flow.RaceTimings;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * In the last three seconds before a map's GO the start countdown keeps its digits, its title and its beat, and the wait
 * between maps adds nothing on top of them: its count is the boss bar's title and no title or sound of its own.
 */
class StartCountdownUnchangedTest {

    /** Lobby 20 s, race 60 s, results 8 s between maps. */
    private static final RaceTimings TIMINGS = new RaceTimings(
            Duration.ofSeconds(20), Duration.ofSeconds(60), Duration.ofSeconds(8), Duration.ofSeconds(20));

    @Test
    void theDigitsOfTheLastThreeSecondsAreThreeTwoOneAsBefore() {
        assertThat(StartCountdown.digitFor(Duration.ofMillis(2_950)))
                .as("three appears in the third-last second, as it always has")
                .isEqualTo(3);
        assertThat(StartCountdown.digitFor(Duration.ofMillis(1_500)))
                .as("two in the second-last second")
                .isEqualTo(2);
        assertThat(StartCountdown.digitFor(Duration.ofMillis(500)))
                .as("one in the last second")
                .isEqualTo(1);
    }

    @Test
    void theWaitsCountIsTheBossBarsOnlyTitleInTheLastThreeSeconds() {
        RaceState lastThreeSeconds = new RaceState(RacePhase.LOBBY, 1, Duration.ofMillis(17_500), false);

        Optional<WaitBar> bar = WaitBar.countingDown(lastThreeSeconds, TIMINGS, true, true);

        assertThat(bar)
                .as("the boss bar carries the count to the GO through the last three seconds")
                .isPresent();
        assertThat(bar.orElseThrow().progress())
                .as("and its progress is the share of the wait that has passed, not a title or a sound")
                .isGreaterThan(0f);
    }
}
