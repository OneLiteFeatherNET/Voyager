package net.elytrarace.server.game;

import net.elytrarace.server.physics.Ring;
import net.elytrarace.server.physics.RingCollisionDetector;
import net.minestom.server.coordinate.Vec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the core logic used inside {@link GameLoopSystem}.
 * <p>
 * The system itself requires a Minestom {@code Player}, which makes full
 * integration testing difficult without {@code @EnvTest}. These tests verify
 * the individual formulas (predicted position, ring detection with velocity)
 * that the game loop relies on.
 */
class GameLoopSystemTest {

    @Test
    void predictedPositionIsCorrect() {
        Vec prevPos = new Vec(0, 50, 0);
        Vec velocity = new Vec(1, 0, 1);

        Vec predicted = prevPos.add(velocity);

        assertThat(predicted).isEqualTo(new Vec(1, 50, 1));
    }

    @Test
    void predictedPositionWithNegativeVelocity() {
        Vec prevPos = new Vec(10, 100, 10);
        Vec velocity = new Vec(-3, -2, -5);

        Vec predicted = prevPos.add(velocity);

        assertThat(predicted).isEqualTo(new Vec(7, 98, 5));
    }

    @Test
    void predictedPositionWithZeroVelocity() {
        Vec prevPos = new Vec(5, 60, 5);
        Vec velocity = Vec.ZERO;

        Vec predicted = prevPos.add(velocity);

        assertThat(predicted).isEqualTo(prevPos);
    }

    @Test
    void ringDetectionWorksWithPredictedPosition() {
        // Player moves from z=-2 to z=+2 through a ring at z=0
        Vec prevPos = new Vec(0, 50, -2);
        Vec velocity = new Vec(0, 0, 4);
        Vec currPos = prevPos.add(velocity);

        Ring ring = new Ring(new Vec(0, 50, 0), new Vec(0, 0, 1), 5.0, 10);

        assertThat(RingCollisionDetector.checkPassthrough(ring, prevPos, currPos)).isTrue();
    }

    @Test
    void ringDetectionMissesWhenVelocityTooShort() {
        // Player moves from z=-10 to z=-6, ring is at z=0 -- does not reach
        Vec prevPos = new Vec(0, 50, -10);
        Vec velocity = new Vec(0, 0, 4);
        Vec currPos = prevPos.add(velocity);

        Ring ring = new Ring(new Vec(0, 50, 0), new Vec(0, 0, 1), 5.0, 10);

        assertThat(RingCollisionDetector.checkPassthrough(ring, prevPos, currPos)).isFalse();
    }

    @Test
    void ringDetectionMissesWhenOutsideRadius() {
        // Player flies through the ring plane but far outside the ring radius
        Vec prevPos = new Vec(100, 50, -2);
        Vec velocity = new Vec(0, 0, 4);
        Vec currPos = prevPos.add(velocity);

        Ring ring = new Ring(new Vec(0, 50, 0), new Vec(0, 0, 1), 5.0, 10);

        assertThat(RingCollisionDetector.checkPassthrough(ring, prevPos, currPos)).isFalse();
    }

    @Test
    void highVelocityPassesThroughDistantRing() {
        // Player has high velocity and covers a large distance in one tick
        Vec prevPos = new Vec(0, 50, -50);
        Vec velocity = new Vec(0, 0, 100);
        Vec currPos = prevPos.add(velocity);

        Ring ring = new Ring(new Vec(0, 50, 0), new Vec(0, 0, 1), 5.0, 10);

        assertThat(RingCollisionDetector.checkPassthrough(ring, prevPos, currPos)).isTrue();
    }

    @Test
    void diagonalVelocityPassesThroughRing() {
        // Player flies diagonally through a ring
        Vec prevPos = new Vec(-2, 48, -3);
        Vec velocity = new Vec(4, 4, 6);
        Vec currPos = prevPos.add(velocity);

        // Ring at (0, 50, 0), facing +Z, radius 5
        Ring ring = new Ring(new Vec(0, 50, 0), new Vec(0, 0, 1), 5.0, 10);

        assertThat(RingCollisionDetector.checkPassthrough(ring, prevPos, currPos)).isTrue();
    }
}
