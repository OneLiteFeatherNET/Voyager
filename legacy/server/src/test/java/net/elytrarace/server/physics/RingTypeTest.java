package net.elytrarace.server.physics;

import net.minestom.server.coordinate.Vec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RingTypeTest {

    private static final Vec CENTER = new Vec(0, 50, 0);
    private static final Vec NORMAL = new Vec(0, 0, 1);
    private static final double RADIUS = 5.0;
    private static final int POINTS = 10;

    @Test
    void createStandardRingExplicitly() {
        var ring = new Ring(CENTER, NORMAL, RADIUS, POINTS, RingType.STANDARD);

        assertThat(ring.type()).isEqualTo(RingType.STANDARD);
        assertThat(ring.points()).isEqualTo(POINTS);
    }

    @Test
    void createBoostRing() {
        var ring = new Ring(CENTER, NORMAL, RADIUS, POINTS, RingType.BOOST);

        assertThat(ring.type()).isEqualTo(RingType.BOOST);
    }

    @Test
    void createCheckpointRing() {
        var ring = new Ring(CENTER, NORMAL, RADIUS, POINTS, RingType.CHECKPOINT);

        assertThat(ring.type()).isEqualTo(RingType.CHECKPOINT);
    }

    @Test
    void createSlowRing() {
        var ring = new Ring(CENTER, NORMAL, RADIUS, POINTS, RingType.SLOW);

        assertThat(ring.type()).isEqualTo(RingType.SLOW);
    }

    @Test
    void createBonusRing() {
        var ring = new Ring(CENTER, NORMAL, RADIUS, 50, RingType.BONUS);

        assertThat(ring.type()).isEqualTo(RingType.BONUS);
        assertThat(ring.points()).isEqualTo(50);
    }

    @Test
    void defaultConstructorUsesStandardType() {
        var ring = new Ring(CENTER, NORMAL, RADIUS, POINTS);

        assertThat(ring.type()).isEqualTo(RingType.STANDARD);
    }

    @Test
    void allEnumValuesExist() {
        assertThat(RingType.values()).containsExactlyInAnyOrder(
                RingType.STANDARD, RingType.BOOST, RingType.CHECKPOINT,
                RingType.SLOW, RingType.BONUS
        );
    }

    @Test
    void ringsWithDifferentTypesAreNotEqual() {
        var standard = new Ring(CENTER, NORMAL, RADIUS, POINTS, RingType.STANDARD);
        var boost = new Ring(CENTER, NORMAL, RADIUS, POINTS, RingType.BOOST);

        assertThat(standard).isNotEqualTo(boost);
    }
}
