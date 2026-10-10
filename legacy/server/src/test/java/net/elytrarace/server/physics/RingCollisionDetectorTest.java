package net.elytrarace.server.physics;

import net.minestom.server.coordinate.Vec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RingCollisionDetectorTest {

    // A ring at the origin, facing along the Z axis (normal = +Z), radius 3
    private static final Vec RING_CENTER = new Vec(0, 10, 0);
    private static final Vec RING_NORMAL = new Vec(0, 0, 1);
    private static final double RING_RADIUS = 3.0;

    @Test
    void passthroughDetectedWhenFlyingThroughCenter() {
        // Flying straight through the ring center along the Z axis
        Vec prevPos = new Vec(0, 10, -5);
        Vec currPos = new Vec(0, 10, 5);

        boolean result = RingCollisionDetector.checkPassthrough(
                RING_CENTER, RING_NORMAL, RING_RADIUS, prevPos, currPos);

        assertThat(result).isTrue();
    }

    @Test
    void noPassthroughWhenFlyingPastRing() {
        // Flying parallel to the ring but far outside its radius
        Vec prevPos = new Vec(10, 10, -5);
        Vec currPos = new Vec(10, 10, 5);

        boolean result = RingCollisionDetector.checkPassthrough(
                RING_CENTER, RING_NORMAL, RING_RADIUS, prevPos, currPos);

        assertThat(result).isFalse();
    }

    @Test
    void passthroughDetectedAtEdgeOfRing() {
        // Flying through at exactly the ring radius (boundary case)
        Vec prevPos = new Vec(RING_RADIUS, 10, -5);
        Vec currPos = new Vec(RING_RADIUS, 10, 5);

        boolean result = RingCollisionDetector.checkPassthrough(
                RING_CENTER, RING_NORMAL, RING_RADIUS, prevPos, currPos);

        assertThat(result).isTrue();
    }

    @Test
    void noPassthroughJustOutsideRing() {
        // Flying through just outside the ring radius
        Vec prevPos = new Vec(RING_RADIUS + 0.01, 10, -5);
        Vec currPos = new Vec(RING_RADIUS + 0.01, 10, 5);

        boolean result = RingCollisionDetector.checkPassthrough(
                RING_CENTER, RING_NORMAL, RING_RADIUS, prevPos, currPos);

        assertThat(result).isFalse();
    }

    @Test
    void reversePassthroughDetected() {
        // Flying through the ring in the opposite direction (backwards)
        Vec prevPos = new Vec(0, 10, 5);
        Vec currPos = new Vec(0, 10, -5);

        boolean result = RingCollisionDetector.checkPassthrough(
                RING_CENTER, RING_NORMAL, RING_RADIUS, prevPos, currPos);

        assertThat(result).isTrue();
    }

    @Test
    void noPassthroughWhenParallelToRing() {
        // Flying exactly parallel to the ring plane (along X axis, within the plane Z=0)
        Vec prevPos = new Vec(-5, 10, 0);
        Vec currPos = new Vec(5, 10, 0);

        boolean result = RingCollisionDetector.checkPassthrough(
                RING_CENTER, RING_NORMAL, RING_RADIUS, prevPos, currPos);

        assertThat(result).isFalse();
    }

    @Test
    void noPassthroughWhenSegmentTooShort() {
        // Segment does not reach the ring plane
        Vec prevPos = new Vec(0, 10, -10);
        Vec currPos = new Vec(0, 10, -5);

        boolean result = RingCollisionDetector.checkPassthrough(
                RING_CENTER, RING_NORMAL, RING_RADIUS, prevPos, currPos);

        assertThat(result).isFalse();
    }

    @Test
    void passthroughWithAngledTrajectory() {
        // Flying at an angle through the ring
        Vec prevPos = new Vec(-1, 9, -3);
        Vec currPos = new Vec(1, 11, 3);

        boolean result = RingCollisionDetector.checkPassthrough(
                RING_CENTER, RING_NORMAL, RING_RADIUS, prevPos, currPos);

        assertThat(result).isTrue();
    }

    @Test
    void convenienceOverloadWithRingRecord() {
        Ring ring = new Ring(RING_CENTER, RING_NORMAL, RING_RADIUS, 10);
        Vec prevPos = new Vec(0, 10, -5);
        Vec currPos = new Vec(0, 10, 5);

        boolean result = RingCollisionDetector.checkPassthrough(ring, prevPos, currPos);

        assertThat(result).isTrue();
    }

    @Test
    void passthroughWithTiltedRing() {
        // Ring tilted 45 degrees — normal pointing diagonally
        Vec normal = new Vec(0, 1, 1).normalize();
        Vec center = new Vec(0, 10, 0);

        // Flying straight up through the tilted ring
        Vec prevPos = new Vec(0, 5, -5);
        Vec currPos = new Vec(0, 15, 5);

        boolean result = RingCollisionDetector.checkPassthrough(
                center, normal, RING_RADIUS, prevPos, currPos);

        assertThat(result).isTrue();
    }
}
