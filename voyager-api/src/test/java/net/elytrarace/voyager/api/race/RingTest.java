package net.elytrarace.voyager.api.race;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.exception.InvalidRingException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RingTest {

    private static final Vec3 UP_RIGHT = new Vec3(0.6, 0.8, 0.0);

    @Test
    void keepsWhatItWasGiven() {
        Ring ring = new Ring(7, new Vec3(10.0, 70.0, -20.0), UP_RIGHT, 4.0, 25, RingType.CHECKPOINT);

        assertThat(ring.index()).isEqualTo(7);
        assertThat(ring.center()).isEqualTo(new Vec3(10.0, 70.0, -20.0));
        assertThat(ring.normal()).isEqualTo(UP_RIGHT);
        assertThat(ring.radius()).isEqualTo(4.0);
        assertThat(ring.points()).isEqualTo(25);
        assertThat(ring.type()).isEqualTo(RingType.CHECKPOINT);
    }

    @Test
    void rejectsANonPositiveRadius() {
        assertThatThrownBy(() -> new Ring(0, Vec3.ZERO, UP_RIGHT, 0.0, 10, RingType.STANDARD))
                .isInstanceOf(InvalidRingException.class);
    }

    @Test
    void rejectsANormalThatIsNotUnitLength() {
        // A normal of the wrong length silently scales every dot product in the crossing test.
        // Rejecting it here is cheaper than debugging a ring that is only sometimes passable.
        assertThatThrownBy(() -> new Ring(0, Vec3.ZERO, new Vec3(0.0, 0.0, 2.0), 4.0, 10, RingType.STANDARD))
                .isInstanceOf(InvalidRingException.class);
    }

    @Test
    void rejectsANegativeIndex() {
        assertThatThrownBy(() -> new Ring(-1, Vec3.ZERO, UP_RIGHT, 4.0, 10, RingType.STANDARD))
                .isInstanceOf(InvalidRingException.class);
    }

    @Test
    void rejectsNegativePoints() {
        assertThatThrownBy(() -> new Ring(0, Vec3.ZERO, UP_RIGHT, 4.0, -1, RingType.STANDARD))
                .isInstanceOf(InvalidRingException.class);
    }

    @Test
    void looksUpTypesByNameAndReportsUnknownOnes() {
        assertThat(RingType.byName("boost")).contains(RingType.BOOST);
        assertThat(RingType.byName("CHECKPOINT")).contains(RingType.CHECKPOINT);
        assertThat(RingType.byName("bonus")).isEmpty();
        assertThat(RingType.byName("  ")).isEmpty();
    }
}
