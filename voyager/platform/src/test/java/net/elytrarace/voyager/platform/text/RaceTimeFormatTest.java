package net.elytrarace.voyager.platform.text;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The clock a racer reads under their own crosshair twenty times a second.
 *
 * <p>Every fixture below carries a different minute, second and tenth wherever it can. A value like
 * {@code 1:11.1} could not tell a formatter that divided by the wrong constant from one that did
 * not, and a whole-second fixture cannot see a tenths digit that is always zero — which is exactly
 * the class of defect this repository keeps finding by deleting a line rather than by reading a test.
 */
class RaceTimeFormatTest {

    @Test
    void writesMinutesSecondsAndTenths() {
        assertThat(RaceTimeFormat.tenths(Duration.ofMillis(143_400))).isEqualTo("2:23.4");
    }

    @Test
    void padsTheSecondsFieldAndLeavesTheMinutesUnpadded() {
        assertThat(RaceTimeFormat.tenths(Duration.ofMillis(63_200))).isEqualTo("1:03.2");
        assertThat(RaceTimeFormat.tenths(Duration.ofMillis(900))).isEqualTo("0:00.9");
    }

    @Test
    void countsPastTenMinutesWithoutOverflowingIntoTheSecondsField() {
        assertThat(RaceTimeFormat.tenths(Duration.ofMillis(754_500))).isEqualTo("12:34.5");
    }

    @Test
    void zeroReadsAsZeroRatherThanAsAnEmptyField() {
        assertThat(RaceTimeFormat.tenths(Duration.ZERO)).isEqualTo("0:00.0");
        assertThat(RaceTimeFormat.whole(Duration.ZERO)).isEqualTo("0:00");
    }

    /**
     * Truncation, not rounding, and this is the fixture that tells them apart: 0.97 s is nine tenths
     * of a second that have passed, not one whole second that has not. Rounding here would make
     * every finish time a tenth faster than the run.
     */
    @Test
    void truncatesTowardTheTimeThatHasActuallyPassed() {
        assertThat(RaceTimeFormat.tenths(Duration.ofMillis(970))).isEqualTo("0:00.9");
        assertThat(RaceTimeFormat.tenths(Duration.ofMillis(59_999))).isEqualTo("0:59.9");
        assertThat(RaceTimeFormat.whole(Duration.ofMillis(59_999))).isEqualTo("0:59");
    }

    @Test
    void aTargetTimeIsWrittenWithoutAPrecisionItDoesNotHave() {
        assertThat(RaceTimeFormat.whole(Duration.ofSeconds(60))).isEqualTo("1:00");
        assertThat(RaceTimeFormat.whole(Duration.ofMillis(143_400))).isEqualTo("2:23");
    }

    @Test
    void aClockDoesNotRunBackwards() {
        assertThatThrownBy(() -> RaceTimeFormat.tenths(Duration.ofMillis(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("backwards");
        assertThatThrownBy(() -> RaceTimeFormat.whole(Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("backwards");
    }

    /**
     * The action bar pads the ring counter onto the screen centreline with a run of spaces as wide
     * as the clock, so the clock's width has to be the same on every tick of a race. It is: the
     * minutes field only grows past one character after ten minutes, which is longer than the race
     * cap allows anybody to be in the air.
     */
    @Test
    void theClockKeepsOneWidthForEveryTimeARaceCanProduce() {
        assertThat(RaceTimeFormat.tenths(Duration.ZERO)).hasSize(6);
        assertThat(RaceTimeFormat.tenths(Duration.ofMillis(66_000))).hasSize(6);
        assertThat(RaceTimeFormat.tenths(Duration.ofSeconds(300))).hasSize(6);
    }
}
