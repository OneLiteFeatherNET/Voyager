package net.elytrarace.voyager.race.flow;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The time left until the next map's GO, read from the race state alone: the rest of the END phase plus the rest of the
 * lobby. Every test builds its own states, and nothing reads the wall clock.
 */
class SecondsUntilGoTest {

    /** Lobby 20 s, race 60 s, results 8 s between maps: a full wait is 28 s. */
    private static final RaceTimings TIMINGS = new RaceTimings(
            Duration.ofSeconds(20), Duration.ofSeconds(60), Duration.ofSeconds(8), Duration.ofSeconds(20));

    @Test
    void theSecondsToGoAreTheEndRemainderPlusTheLobbyRemainderRoundedUp() {
        RaceState ended = new RaceState(RacePhase.END, 0, Duration.ZERO, false);
        RaceState halfway = new RaceState(RacePhase.END, 0, Duration.ofMillis(3_200), false);
        RaceState lobby = new RaceState(RacePhase.LOBBY, 1, Duration.ofSeconds(5), false);

        assertThat(SecondsUntilGo.remaining(ended, TIMINGS))
                .as("at the end of a map the full wait of 8 s of results and 20 s of lobby is left")
                .isEqualTo(Duration.ofSeconds(28));
        assertThat(SecondsUntilGo.remaining(halfway, TIMINGS))
                .as("3.2 s into the results 4.8 s of them and the whole lobby are left")
                .isEqualTo(Duration.ofMillis(24_800));
        assertThat(SecondsUntilGo.remaining(lobby, TIMINGS))
                .as("5 s into the next lobby 15 s of it are left, with no results left")
                .isEqualTo(Duration.ofSeconds(15));
        assertThat(SecondsUntilGo.wholeSecondsUp(SecondsUntilGo.remaining(halfway, TIMINGS)))
                .as("24.8 s left reads as 25 s, so the count never shows a second that has already gone")
                .isEqualTo(25);
    }

    @Test
    void aWholeSecondLeftReadsAsItself() {
        RaceState oneSecondLeft = new RaceState(RacePhase.LOBBY, 1, Duration.ofSeconds(19), false);

        assertThat(SecondsUntilGo.wholeSecondsUp(SecondsUntilGo.remaining(oneSecondLeft, TIMINGS)))
                .as("exactly one second left reads as 1, and the GO follows within that second")
                .isEqualTo(1);
    }

    @Test
    void aFractionOfASecondLeftReadsAsOne() {
        assertThat(SecondsUntilGo.wholeSecondsUp(Duration.ofMillis(400)))
                .as("0.4 s left still reads as 1, not 0, until the GO")
                .isEqualTo(1);
    }

    @Test
    void theRemainderIsNeverNegative() {
        RaceState pastTheLobby = new RaceState(RacePhase.LOBBY, 1, Duration.ofSeconds(25), false);

        assertThat(SecondsUntilGo.remaining(pastTheLobby, TIMINGS))
                .as("an overshoot past the lobby leaves nothing to wait for")
                .isEqualTo(Duration.ZERO);
    }

    @Test
    void theFractionOfTheWaitPassedCountsWholeSeconds() {
        RaceState threeIntoResults = new RaceState(RacePhase.END, 0, Duration.ofMillis(3_200), false);
        RaceState fiveIntoLobby = new RaceState(RacePhase.LOBBY, 1, Duration.ofMillis(5_400), false);

        assertThat(SecondsUntilGo.fractionPassed(threeIntoResults, TIMINGS))
                .as("3 whole seconds of the 28 s wait have passed")
                .isEqualTo(3.0 / 28.0);
        assertThat(SecondsUntilGo.fractionPassed(fiveIntoLobby, TIMINGS))
                .as("8 s of results and 5 whole seconds of the lobby have passed")
                .isEqualTo(13.0 / 28.0);
    }

    @Test
    void aRunningGameIsRefusedBecauseNoWaitIsRunning() {
        RaceState racing = new RaceState(RacePhase.GAME, 0, Duration.ofSeconds(4), false);

        assertThatThrownBy(() -> SecondsUntilGo.remaining(racing, TIMINGS))
                .as("a game phase has no time to a GO, so asking for one is a caller's mistake")
                .isInstanceOf(IllegalArgumentException.class);
    }
}
