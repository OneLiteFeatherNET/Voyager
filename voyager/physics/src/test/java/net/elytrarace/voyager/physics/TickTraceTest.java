package net.elytrarace.voyager.physics;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.physics.FlightState;
import net.elytrarace.voyager.physics.step.ElytraStep;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class TickTraceTest {

    private static FlightState state() {
        return new FlightState(Vec3.ZERO, Vec3.ZERO, 0.0f, 0.0f, false);
    }

    /**
     * {@code new EnumMap<>(someMap)} throws {@code IllegalArgumentException} when {@code someMap} is
     * empty and not itself an {@code EnumMap} — it has no element to infer the key type from. {@link
     * TickTrace} is a public record constructor a caller could hand an empty {@link Map#of()} to (a
     * tick with no steps recorded, or a stub in a test double); it must not throw for that input.
     */
    @Test
    void anEmptyNonEnumMapDoesNotThrow() {
        assertThatCode(() -> new TickTrace(state(), Map.of())).doesNotThrowAnyException();
    }

    @Test
    void anEmptyVelocityAfterMapStaysEmpty() {
        TickTrace trace = new TickTrace(state(), Map.of());

        assertThat(trace.velocityAfter()).isEmpty();
    }

    @Test
    void preservesEntriesFromAnArbitraryMapImplementation() {
        TickTrace trace = new TickTrace(state(), Map.of(ElytraStep.DRAG, new Vec3(1, 2, 3)));

        assertThat(trace.velocityAfter()).containsExactly(Map.entry(ElytraStep.DRAG, new Vec3(1, 2, 3)));
    }
}
