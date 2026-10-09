package net.elytrarace.voyager.setup.mapsetup;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;

class RingPickerTest {

    private static final Vec3 ORIGIN = Vec3.ZERO;
    private static final Vec3 FORWARD = new Vec3(0, 0, 1);
    private static final double REACH = 32.0;

    @Test
    void theNearestOfTwoCrossedRingsWins() {
        List<Ring> rings = List.of(ring(0, new Vec3(0, 0, 5), FORWARD, 2), ring(1, new Vec3(0, 0, 10), FORWARD, 2));

        assertThat(RingPicker.nearestCrossed(rings, ORIGIN, FORWARD, REACH)).isEqualTo(OptionalInt.of(0));
    }

    @Test
    void aHitOnTheRimCountsAsCrossed() {
        List<Ring> rings = List.of(ring(0, new Vec3(0, 0, 5), FORWARD, 2));

        assertThat(RingPicker.nearestCrossed(rings, new Vec3(2, 0, 0), FORWARD, REACH))
                .isEqualTo(OptionalInt.of(0));
    }

    @Test
    void aRingBehindTheOriginIsNotCrossed() {
        List<Ring> rings = List.of(ring(0, new Vec3(0, 0, -5), FORWARD, 2));

        assertThat(RingPicker.nearestCrossed(rings, ORIGIN, FORWARD, REACH)).isEmpty();
    }

    @Test
    void aRayParallelToTheDiscIsNotCrossed() {
        List<Ring> rings = List.of(ring(0, new Vec3(0, 0, 5), new Vec3(1, 0, 0), 2));

        assertThat(RingPicker.nearestCrossed(rings, ORIGIN, FORWARD, REACH)).isEmpty();
    }

    @Test
    void aRingBeyondTheReachIsNotCrossed() {
        List<Ring> rings = List.of(ring(0, new Vec3(0, 0, 50), FORWARD, 2));

        assertThat(RingPicker.nearestCrossed(rings, ORIGIN, FORWARD, REACH)).isEmpty();
    }

    @Test
    void aRayThatMissesTheDiscBeyondItsRadiusIsNotCrossed() {
        List<Ring> rings = List.of(ring(0, new Vec3(5, 0, 5), FORWARD, 2));

        assertThat(RingPicker.nearestCrossed(rings, ORIGIN, FORWARD, REACH)).isEmpty();
    }

    @Test
    void aLookDirectionOfLengthTwoPicksTheSameRing() {
        List<Ring> rings = List.of(ring(0, new Vec3(0, 0, 5), FORWARD, 2));

        assertThat(RingPicker.nearestCrossed(rings, ORIGIN, new Vec3(0, 0, 2), REACH)).isEqualTo(OptionalInt.of(0));
    }

    @Test
    void noRingsGivesNoIndex() {
        assertThat(RingPicker.nearestCrossed(List.of(), ORIGIN, FORWARD, REACH)).isEmpty();
    }

    private static Ring ring(int index, Vec3 center, Vec3 normal, double radius) {
        return new Ring(index, center, normal, radius, 10, RingType.STANDARD);
    }
}
