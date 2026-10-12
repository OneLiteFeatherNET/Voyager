package net.elytrarace.voyager.race.flow;

import net.elytrarace.voyager.race.flow.StartGate.Situation;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StartGateShortenTest {

    private static final int MAXIMUM = 16;

    @Test
    void aFullLobbyWithFifteenSecondsLeftShortensToTen() {
        Optional<Duration> shortened = StartGate.shortenedCountdown(
                Situation.COUNTDOWN, Duration.ofSeconds(15), MAXIMUM, MAXIMUM);

        assertThat(shortened).contains(Duration.ofSeconds(10));
    }

    @Test
    void aFullLobbyWithSixSecondsLeftIsUnchanged() {
        Optional<Duration> shortened = StartGate.shortenedCountdown(
                Situation.COUNTDOWN, Duration.ofSeconds(6), MAXIMUM, MAXIMUM);

        assertThat(shortened).isEmpty();
    }

    @Test
    void aCommittedStartIsNeverShortened() {
        Optional<Duration> shortened = StartGate.shortenedCountdown(
                Situation.COMMITTED, Duration.ofSeconds(2), MAXIMUM, MAXIMUM);

        assertThat(shortened).isEmpty();
    }

    @Test
    void aLobbyBelowTheMaximumIsUnchanged() {
        Optional<Duration> shortened = StartGate.shortenedCountdown(
                Situation.COUNTDOWN, Duration.ofSeconds(15), MAXIMUM - 1, MAXIMUM);

        assertThat(shortened).isEmpty();
    }

    @Test
    void aCountdownIsNeverLengthened() {
        Optional<Duration> shortened = StartGate.shortenedCountdown(
                Situation.COUNTDOWN, Duration.ofSeconds(10), MAXIMUM, MAXIMUM);

        assertThat(shortened).isEmpty();
    }

    @Test
    void aMaximumBelowOneIsRefused() {
        assertThatThrownBy(() -> StartGate.shortenedCountdown(Situation.COUNTDOWN, Duration.ofSeconds(15), 2, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maximum");
    }
}
