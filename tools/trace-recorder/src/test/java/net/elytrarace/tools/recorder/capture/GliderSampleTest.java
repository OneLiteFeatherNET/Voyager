package net.elytrarace.tools.recorder.capture;

import net.elytrarace.tools.recorder.format.exception.InvalidTraceException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Every {@link GliderSample} constructed in {@link TraceCollectorTest} is valid, so nothing there
 * ever exercises the compact constructor's own checks. These do, one check at a time, and build the
 * invalid sample inside the {@code assertThatThrownBy} lambda rather than before it -- constructing
 * it outside would throw during test setup, leaving the test permanently red against a correct
 * implementation instead of proving anything about the check under test.
 */
class GliderSampleTest {

    @Test
    void rejectsANonFinitePositionOrVelocity() {
        assertThatThrownBy(() -> new GliderSample(
                Double.NaN, 100.0, 0.0, 0.0, -0.08, 0.0, 0.0f, -5.0f, false, false, 0, 0))
                .isInstanceOf(InvalidTraceException.class);
    }

    @Test
    void rejectsANonFiniteRotation() {
        assertThatThrownBy(() -> new GliderSample(
                0.0, 100.0, 0.0, 0.0, -0.08, 0.0, Float.NaN, -5.0f, false, false, 0, 0))
                .isInstanceOf(InvalidTraceException.class);
    }

    @Test
    void rejectsANegativeFireworkTickCount() {
        assertThatThrownBy(() -> new GliderSample(
                0.0, 100.0, 0.0, 0.0, -0.08, 0.0, 0.0f, -5.0f, false, false, -1, 0))
                .isInstanceOf(InvalidTraceException.class);
    }

    @Test
    void rejectsANegativeEntityTick() {
        assertThatThrownBy(() -> new GliderSample(
                0.0, 100.0, 0.0, 0.0, -0.08, 0.0, 0.0f, -5.0f, false, false, 0, -1))
                .isInstanceOf(InvalidTraceException.class);
    }
}
