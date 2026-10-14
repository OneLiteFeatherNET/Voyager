package net.elytrarace.voyager.platform.hud;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * How long the missed-ring line holds the action bar, counted in game ticks and never by a clock. The line is
 * a replacement for the flight line, so the span is asserted as the tick boundaries it starts and ends on.
 */
class RaceHudMissedRingTest {

    private static final int SHOWN_AT = 500;

    /** One and a half seconds is thirty ticks of fifty milliseconds. */
    @Test
    void theMissedRingLineHoldsForThirtyTicks() {
        assertThat(RaceHud.MISSED_RING_TICKS).isEqualTo(30);
    }

    @Test
    void theMissedRingLineReplacesTheFlightLineFromTheTickItIsShown() {
        assertThat(RaceHud.missedLineShowing(SHOWN_AT, SHOWN_AT)).isTrue();
    }

    @Test
    void theMissedRingLineIsStillShownOnItsLastTick() {
        assertThat(RaceHud.missedLineShowing(SHOWN_AT + 29, SHOWN_AT)).isTrue();
    }

    @Test
    void theFlightLineReturnsOnTheTickAfterTheMissedRingLine() {
        assertThat(RaceHud.missedLineShowing(SHOWN_AT + 30, SHOWN_AT)).isFalse();
    }
}
