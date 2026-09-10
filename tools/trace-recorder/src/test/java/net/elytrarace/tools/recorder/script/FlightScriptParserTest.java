package net.elytrarace.tools.recorder.script;

import net.elytrarace.tools.recorder.format.exception.InvalidTraceException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class FlightScriptParserTest {

    @Test
    void holdKeepsRotationForTheWholeSpan() {
        FlightScript script = FlightScriptParser.parse("hold 3 yaw=90 pitch=-5");

        assertThat(script.durationTicks()).isEqualTo(3);
        for (int tick = 0; tick < 3; tick++) {
            assertThat(script.inputAt(tick).yaw()).isEqualTo(90.0f);
            assertThat(script.inputAt(tick).pitch()).isEqualTo(-5.0f);
            assertThat(script.inputAt(tick).igniteFirework()).isFalse();
        }
    }

    @Test
    void rampInterpolatesFromStartToEndInclusive() {
        FlightScript script = FlightScriptParser.parse("ramp 5 yaw=0 pitch=0..40");

        assertThat(script.inputAt(0).pitch()).isEqualTo(0.0f);
        assertThat(script.inputAt(4).pitch()).isEqualTo(40.0f);
        assertThat(script.inputAt(2).pitch()).isCloseTo(20.0f, within(1e-4f));
    }

    @Test
    void boostOccupiesOneTickAndInheritsThePreviousRotation() {
        FlightScript script = FlightScriptParser.parse("""
                hold 2 yaw=0 pitch=-5
                boost
                hold 2 yaw=0 pitch=-5
                """);

        assertThat(script.durationTicks()).isEqualTo(5);
        assertThat(script.inputAt(2).igniteFirework()).isTrue();
        assertThat(script.inputAt(2).pitch()).isEqualTo(-5.0f);
        assertThat(script.inputAt(3).igniteFirework()).isFalse();
    }

    @Test
    void ignoresBlankLinesAndComments() {
        FlightScript script = FlightScriptParser.parse("""
                # a comment

                hold 2 yaw=0 pitch=-5
                """);

        assertThat(script.durationTicks()).isEqualTo(2);
    }

    @Test
    void rejectsABoostBeforeAnyRotationIsEstablished() {
        assertThatThrownBy(() -> FlightScriptParser.parse("boost"))
                .isInstanceOf(InvalidTraceException.class);
    }

    @Test
    void rejectsAnUnknownDirective() {
        assertThatThrownBy(() -> FlightScriptParser.parse("loop 3 yaw=0 pitch=0"))
                .isInstanceOf(InvalidTraceException.class);
    }

    @Test
    void rejectsANonPositiveSpan() {
        assertThatThrownBy(() -> FlightScriptParser.parse("hold 0 yaw=0 pitch=-5"))
                .isInstanceOf(InvalidTraceException.class);
    }

    @Test
    void rejectsAskingForATickBeyondTheScript() {
        FlightScript script = FlightScriptParser.parse("hold 2 yaw=0 pitch=-5");

        assertThatThrownBy(() -> script.inputAt(2)).isInstanceOf(InvalidTraceException.class);
    }

    @Test
    void aRampOfOneTickYieldsItsEndValue() {
        FlightScript script = FlightScriptParser.parse("ramp 1 yaw=0 pitch=10..20");

        assertThat(script.inputAt(0).pitch()).isEqualTo(20.0f);
    }

    @Test
    void rampComputesInFloatRatherThanDoubleNarrowedAtTheEnd() {
        // a=0, b=40, n=4, index=1: float arithmetic (0f + 40f * (1f/3f)) rounds to 13.333334f;
        // computing in double and narrowing at the end rounds to 13.333333f instead. This pins
        // the specific numeric type used, which a looser tolerance would not distinguish.
        FlightScript script = FlightScriptParser.parse("ramp 4 yaw=0 pitch=0..40");

        assertThat(script.inputAt(1).pitch()).isEqualTo(13.333334f);
    }
}
