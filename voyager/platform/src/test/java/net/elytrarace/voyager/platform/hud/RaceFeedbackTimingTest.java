package net.elytrarace.voyager.platform.hud;

import net.kyori.adventure.title.Title;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The hold and fade times of the start titles, read from the values {@link RaceFeedback} builds and never from
 * a clock: a title's timing is data, so it is asserted as data.
 */
class RaceFeedbackTimingTest {

    private static final Duration TICK = Duration.ofMillis(50);

    /** The GO title holds for twenty ticks, which is one second, so the one cue that starts the race can be read. */
    @Test
    void theGoTitleHoldsTwentyTicks() {
        Title.Times go = RaceFeedback.goTimes();

        assertThat(go.stay()).as("GO hold").isEqualTo(TICK.multipliedBy(20));
        assertThat(go.fadeOut()).as("GO fade-out").isEqualTo(TICK.multipliedBy(5));
    }

    /** The GO title is on screen at least as long as a countdown digit, which is one second in total. */
    @Test
    void theGoTitleIsOnScreenAtLeastAsLongAsACountdownDigit() {
        Title.Times go = RaceFeedback.goTimes();
        Title.Times digit = RaceFeedback.countdownTimes();

        assertThat(go.stay().plus(go.fadeOut()))
                .as("GO total on screen")
                .isGreaterThanOrEqualTo(Duration.ofSeconds(1));
        assertThat(go.stay()).as("GO hold against a digit's hold").isGreaterThanOrEqualTo(digit.stay());
    }

    @Test
    void theCountdownDigitHoldsFifteenTicksAndFadesFive() {
        Title.Times digit = RaceFeedback.countdownTimes();

        assertThat(digit.stay()).isEqualTo(TICK.multipliedBy(15));
        assertThat(digit.fadeOut()).isEqualTo(TICK.multipliedBy(5));
    }

    /** A start title that fades in arrives late, so the GO and the digits have no fade-in at all. */
    @Test
    void eachStartTitleFadesInWithNoDelay() {
        assertThat(RaceFeedback.goTimes().fadeIn()).isEqualTo(Duration.ZERO);
        assertThat(RaceFeedback.countdownTimes().fadeIn()).isEqualTo(Duration.ZERO);
    }
}
