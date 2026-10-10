package net.elytrarace.server.ecs.component;

import net.minestom.server.coordinate.Vec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ElytraFlightComponentTest {

    @Test
    void defaultStateIsStationary() {
        var flight = new ElytraFlightComponent();

        assertThat(flight.getVelocity()).isEqualTo(Vec.ZERO);
        assertThat(flight.isFlying()).isFalse();
        assertThat(flight.getPitch()).isEqualTo(0.0);
        assertThat(flight.getYaw()).isEqualTo(0.0);
    }

    @Test
    void velocityCanBeSet() {
        var flight = new ElytraFlightComponent();
        var vel = new Vec(1.0, -0.5, 2.0);

        flight.setVelocity(vel);

        assertThat(flight.getVelocity()).isEqualTo(vel);
    }

    @Test
    void speedBlocksPerSecondConvertsFromTicksToSeconds() {
        var flight = new ElytraFlightComponent();
        // 1 block/tick = 20 blocks/second
        flight.setVelocity(new Vec(1.0, 0.0, 0.0));

        assertThat(flight.getSpeedBlocksPerSecond()).isCloseTo(20.0, within(0.001));
    }

    @Test
    void speedBlocksPerSecondWithDiagonalVelocity() {
        var flight = new ElytraFlightComponent();
        flight.setVelocity(new Vec(3.0, 4.0, 0.0));
        // length = 5, * 20 = 100
        assertThat(flight.getSpeedBlocksPerSecond()).isCloseTo(100.0, within(0.001));
    }

    @Test
    void flyingStateCanBeToggled() {
        var flight = new ElytraFlightComponent();

        flight.setFlying(true);
        assertThat(flight.isFlying()).isTrue();

        flight.setFlying(false);
        assertThat(flight.isFlying()).isFalse();
    }

    @Test
    void pitchAndYawCanBeSet() {
        var flight = new ElytraFlightComponent();

        flight.setPitch(-30.0);
        flight.setYaw(90.0);

        assertThat(flight.getPitch()).isEqualTo(-30.0);
        assertThat(flight.getYaw()).isEqualTo(90.0);
    }
}
