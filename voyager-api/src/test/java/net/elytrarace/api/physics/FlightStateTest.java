package net.elytrarace.api.physics;

import net.elytrarace.api.math.Vec3;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlightStateTest {

    private static Class<?> componentType(Class<?> record, String name) {
        return Arrays.stream(record.getRecordComponents())
                .filter(component -> component.getName().equals(name))
                .map(RecordComponent::getType)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no record component named " + name));
    }

    @Test
    void rotationIsFloatAndPositionIsDoubleToMatchVanilla() {
        assertThat(componentType(FlightState.class, "yaw")).isEqualTo(float.class);
        assertThat(componentType(FlightState.class, "pitch")).isEqualTo(float.class);
        assertThat(componentType(FlightInput.class, "yaw")).isEqualTo(float.class);
        assertThat(componentType(FlightInput.class, "pitch")).isEqualTo(float.class);
        assertThat(componentType(FlightState.class, "position")).isEqualTo(Vec3.class);
        assertThat(componentType(FlightState.class, "velocity")).isEqualTo(Vec3.class);
    }

    @Test
    void holdsAFiniteState() {
        FlightState state = new FlightState(Vec3.ZERO, new Vec3(0, -0.08, 0), 90.0f, -12.5f, false);

        assertThat(state.velocity().y()).isEqualTo(-0.08);
        assertThat(state.pitch()).isEqualTo(-12.5f);
        assertThat(state.onGround()).isFalse();
    }

    @Test
    void rejectsNonFiniteRotation() {
        assertThatThrownBy(() -> new FlightState(Vec3.ZERO, Vec3.ZERO, Float.NaN, 0f, false))
                .isInstanceOf(NonFiniteRotationException.class);
        assertThatThrownBy(() -> new FlightInput(0f, Float.POSITIVE_INFINITY, false, 0))
                .isInstanceOf(NonFiniteRotationException.class);
    }

    @Test
    void rejectsNegativeFireworkTicks() {
        assertThatThrownBy(() -> new FlightInput(0f, 0f, true, -1))
                .isInstanceOf(InvalidFlightInputException.class);
    }

    @Test
    void acceptsAnInactiveBoostWithZeroTicks() {
        assertThat(new FlightInput(0f, 0f, false, 0).fireworkBoostActive()).isFalse();
    }
}
