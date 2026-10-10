package net.elytrarace.voyager.platform.hud;

import net.elytrarace.voyager.race.flow.StartGate;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which digit is on screen at which tick, and the fact that each one goes up exactly once.
 *
 * <h2>Every boundary is asserted from both sides, one tick apart</h2>
 *
 * <p>A countdown that is off by a single tick is a countdown whose last digit is still on screen at
 * the launch, or whose first one arrives a frame late — and nothing about either is visible in a
 * test that only samples the middle of a second. So every assertion below is a pair: the tick where
 * a digit becomes true and the tick before it.
 */
class StartCountdownTest {

    private static final Duration TICK = Duration.ofMillis(50);

    private static Duration seconds(double value) {
        return Duration.ofMillis(Math.round(value * 1000));
    }

    @Test
    void threeAppearsAtExactlyThreeSecondsAndNotOneTickEarlier() {
        assertThat(StartCountdown.digitFor(seconds(3).plus(TICK))).isEqualTo(StartCountdown.NONE);
        assertThat(StartCountdown.digitFor(seconds(3))).isEqualTo(3);
    }

    @Test
    void eachDigitHoldsForItsOwnSecondAndChangesOnTheBoundary() {
        assertThat(StartCountdown.digitFor(seconds(2.95))).isEqualTo(3);
        assertThat(StartCountdown.digitFor(seconds(2.05))).isEqualTo(3);
        assertThat(StartCountdown.digitFor(seconds(2))).isEqualTo(2);

        assertThat(StartCountdown.digitFor(seconds(1.05))).isEqualTo(2);
        assertThat(StartCountdown.digitFor(seconds(1))).isEqualTo(1);

        assertThat(StartCountdown.digitFor(seconds(0.05))).isEqualTo(1);
    }

    /**
     * Zero left is the launch tick, and the launch says {@code GO}. A countdown that answered "0"
     * there would put a fourth digit on the screen in the instant the racer needs to see the first
     * ring.
     */
    @Test
    void nothingIsShownWithNoLobbyLeft() {
        assertThat(StartCountdown.digitFor(Duration.ZERO)).isEqualTo(StartCountdown.NONE);
        assertThat(StartCountdown.digitFor(Duration.ofMillis(-50))).isEqualTo(StartCountdown.NONE);
    }

    @Test
    void aLongLobbyShowsNothingUntilItsLastThreeSeconds() {
        assertThat(StartCountdown.digitFor(Duration.ofSeconds(20))).isEqualTo(StartCountdown.NONE);
        assertThat(StartCountdown.digitFor(Duration.ofMillis(3_050))).isEqualTo(StartCountdown.NONE);
    }

    /**
     * The whole tail of a 20 s lobby at 50 ms a tick, which is what the server actually does: three
     * digits, in order, each handed out on exactly one of the sixty ticks.
     */
    @Test
    void aWholeLobbyTailHandsOutThreeTwoOneAndNothingElse() {
        StartCountdown countdown = new StartCountdown();
        List<Integer> shown = new ArrayList<>();
        List<Integer> ticksWithADigit = new ArrayList<>();

        Duration lobby = Duration.ofSeconds(20);
        int ticks = (int) (lobby.toMillis() / TICK.toMillis());
        for (int tick = 1; tick < ticks; tick++) {
            Duration remaining = lobby.minus(TICK.multipliedBy(tick));
            int digit = countdown.show(remaining);
            if (digit != StartCountdown.NONE) {
                shown.add(digit);
                ticksWithADigit.add(tick);
            }
        }

        assertThat(shown).containsExactly(3, 2, 1);
        // 20 s is 400 ticks. 3 s left is after tick 340, 2 s after 360, 1 s after 380.
        assertThat(ticksWithADigit).containsExactly(340, 360, 380);
        assertThat(countdown.started()).isTrue();
    }

    @Test
    void aDigitIsHandedOutOnceAndNotOnEveryTickOfItsOwnSecond() {
        StartCountdown countdown = new StartCountdown();

        assertThat(countdown.show(seconds(3))).isEqualTo(3);
        assertThat(countdown.show(seconds(2.95))).isEqualTo(StartCountdown.NONE);
        assertThat(countdown.show(seconds(2.5))).isEqualTo(StartCountdown.NONE);
        assertThat(countdown.show(seconds(2))).isEqualTo(2);
    }

    /**
     * A lobby shorter than the count degrades to whatever fits. A dev run with a two-second lobby
     * gets "2, 1" and starts on time; a run with no lobby at all gets nothing and still starts on
     * time. Reintroducing a wait would defeat the one flag that exists to remove waits.
     */
    @Test
    void aShortLobbyGetsTheDigitsThatFitRatherThanADelay() {
        StartCountdown countdown = new StartCountdown();
        List<Integer> shown = new ArrayList<>();

        Duration lobby = Duration.ofSeconds(2);
        for (int tick = 1; tick < lobby.toMillis() / TICK.toMillis(); tick++) {
            int digit = countdown.show(lobby.minus(TICK.multipliedBy(tick)));
            if (digit != StartCountdown.NONE) {
                shown.add(digit);
            }
        }

        assertThat(shown).containsExactly(2, 1);
    }

    @Test
    void aLobbyOfZeroLengthShowsNothingAtAll() {
        StartCountdown countdown = new StartCountdown();

        assertThat(countdown.show(Duration.ZERO)).isEqualTo(StartCountdown.NONE);
        assertThat(countdown.started()).isFalse();
    }

    @Test
    void resettingLetsTheNextMapCountFromThreeAgain() {
        StartCountdown countdown = new StartCountdown();
        countdown.show(seconds(3));
        countdown.show(seconds(2));
        countdown.show(seconds(1));

        countdown.reset();

        assertThat(countdown.started()).isFalse();
        assertThat(countdown.show(seconds(3))).isEqualTo(3);
    }

    /** Without the reset, a second map would inherit the first map's count and show nothing. */
    @Test
    void withoutAResetASecondMapWouldInheritTheFirstMapsCount() {
        StartCountdown countdown = new StartCountdown();
        countdown.show(seconds(3));

        assertThat(countdown.show(seconds(3))).isEqualTo(StartCountdown.NONE);
    }

    /** The countdown's length is the commit window of the start gate, so the two cannot drift apart. */
    @Test
    void lengthIsTheCommitWindow() {
        assertThat(StartCountdown.LENGTH).isEqualTo(StartGate.COMMIT_WINDOW);
    }
}
