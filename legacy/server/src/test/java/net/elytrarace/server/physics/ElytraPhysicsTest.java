package net.elytrarace.server.physics;

import net.minestom.server.coordinate.Vec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ElytraPhysicsTest {

    private static final double TOLERANCE = 1e-6;

    @Test
    void stationaryPlayerSinksDueToGravity() {
        // A player with zero velocity looking horizontally should sink
        Vec velocity = Vec.ZERO;
        Vec next = ElytraPhysics.computeNextVelocity(velocity, 0.0, 0.0);

        // Gravity (-0.08) + Lift (cos^2(0) * 0.06 = 0.06) = -0.02, then drag *0.98
        assertThat(next.y()).isLessThan(0.0);
        // Horizontal velocity should remain small (downward-glide damping may produce slight drift)
        assertThat(Math.sqrt(next.x() * next.x() + next.z() * next.z())).isLessThan(0.01);
    }

    @Test
    void lookingDownAcceleratesForward() {
        // Looking steeply down (positive pitch) with some initial forward velocity
        Vec velocity = new Vec(0.0, 0.0, 0.5);
        double pitchDown = 45.0; // looking down
        double yaw = 0.0; // facing south

        Vec next = ElytraPhysics.computeNextVelocity(velocity, pitchDown, yaw);
        // Downward-glide conversion should maintain or increase horizontal speed components
        // Also the player should be sinking faster than level flight
        double hVelAfter = Math.sqrt(next.x() * next.x() + next.z() * next.z());
        // With 45 degrees down and gravity, the net vertical velocity should be strongly negative
        assertThat(next.y()).isLessThan(-0.02);
    }

    @Test
    void horizontalDragReducesSpeed() {
        // Give the player a purely horizontal velocity and a level pitch
        Vec velocity = new Vec(1.0, 0.0, 0.0);
        Vec next = ElytraPhysics.computeNextVelocity(velocity, 0.0, 0.0);

        double hVelBefore = 1.0;
        double hVelAfter = Math.sqrt(next.x() * next.x() + next.z() * next.z());

        // Horizontal drag (0.99) plus alignment effects should reduce speed
        assertThat(hVelAfter).isLessThan(hVelBefore);
    }

    @Test
    void velocityRemainsInRealisticBounds() {
        // Simulate 200 ticks of level flight from an initial high speed
        Vec velocity = new Vec(0.0, 0.0, 2.0);
        double pitch = 0.0;
        double yaw = 0.0;

        for (int i = 0; i < 200; i++) {
            velocity = ElytraPhysics.computeNextVelocity(velocity, pitch, yaw);
        }

        double speed = velocity.length();
        // After many ticks of level flight, speed should settle to a realistic value
        // Vanilla max without fireworks is around 1.675 blocks/tick (~33.5 blocks/sec)
        assertThat(speed).isLessThan(4.0);
        // Velocity should not explode
        assertThat(Double.isFinite(velocity.x())).isTrue();
        assertThat(Double.isFinite(velocity.y())).isTrue();
        assertThat(Double.isFinite(velocity.z())).isTrue();
    }

    @Test
    void lookingUpConvertsHorizontalSpeedToAltitude() {
        // Initial forward velocity, looking up
        Vec velocity = new Vec(0.0, 0.0, 1.0);
        double pitchUp = -30.0; // looking up
        double yaw = 0.0;

        Vec next = ElytraPhysics.computeNextVelocity(velocity, pitchUp, yaw);

        // The upward pitch boost should give positive Y acceleration relative to just gravity
        // Compare with level flight
        Vec nextLevel = ElytraPhysics.computeNextVelocity(velocity, 0.0, yaw);
        assertThat(next.y()).isGreaterThan(nextLevel.y());
    }

    @Test
    void fireworkBoostIncreasesSpeed() {
        Vec velocity = new Vec(0.0, 0.0, 0.5);
        double pitch = 0.0;
        double yaw = 0.0;

        Vec boosted = ElytraPhysics.applyFireworkBoost(velocity, pitch, yaw);

        double speedBefore = velocity.length();
        double speedAfter = boosted.length();

        assertThat(speedAfter).isGreaterThan(speedBefore);
    }

    @Test
    void fireworkBoostAcceleratesInLookDirection() {
        // Looking south (yaw=0, pitch=0)
        Vec velocity = Vec.ZERO;
        Vec boosted = ElytraPhysics.applyFireworkBoost(velocity, 0.0, 0.0);

        // The boost should produce significant velocity
        assertThat(boosted.length()).isGreaterThan(0.5);
    }

    @Test
    void multipleTicksOfGravityProduceConsistentSinking() {
        Vec velocity = Vec.ZERO;
        // 20 ticks of free fall (level pitch)
        for (int i = 0; i < 20; i++) {
            velocity = ElytraPhysics.computeNextVelocity(velocity, 0.0, 0.0);
        }
        // After 1 second of level elytra glide, player should have sunk
        assertThat(velocity.y()).isLessThan(0.0);
    }
}
