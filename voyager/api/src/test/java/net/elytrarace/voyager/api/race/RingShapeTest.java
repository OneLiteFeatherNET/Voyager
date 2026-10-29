package net.elytrarace.voyager.api.race;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.exception.InvalidRingException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The shapes of a ring, their containment and their bounds. The ring's centre is fixed off the origin, so that a test
 * cannot pass because a point or an offset happens to be zero.
 */
class RingShapeTest {

    private static final Vec3 CENTRE = new Vec3(10.0, 70.0, -20.0);
    private static final Vec3 UP = new Vec3(0.0, 1.0, 0.0);
    private static final Vec3 ALONG_X = new Vec3(1.0, 0.0, 0.0);
    private static final double HALF_SQRT2 = Math.sqrt(0.5);

    @Test
    void aCircleContainsThePointsOfItsDiscAsTheDistanceTestDoes() {
        Ring circle = new Ring(0, CENTRE, UP, 4.0, 10, RingType.STANDARD);

        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
                Vec3 point = CENTRE.plus(new Vec3(x * 0.5, 0.0, z * 0.5));
                assertThat(circle.contains(point))
                        .as("containment of the point offset (%s, 0, %s)".formatted(x * 0.5, z * 0.5))
                        .isEqualTo(point.distanceTo(CENTRE) <= 4.0);
            }
        }
    }

    @Test
    void anAxisAlignedOvalContainsAPointInsideItsEllipse() {
        Ring oval = ovalAlongX(6.0, 3.0);

        assertThat(oval.contains(CENTRE.plus(new Vec3(5.0, 0.0, 0.0)))).isTrue();
    }

    @Test
    void anAxisAlignedOvalRefusesAPointBeyondItsMinorRadius() {
        Ring oval = ovalAlongX(6.0, 3.0);

        assertThat(oval.contains(CENTRE.plus(new Vec3(0.0, 0.0, 3.5)))).isFalse();
    }

    @Test
    void aTiltedOvalContainsAPointAlongItsMajorAxisAndRefusesOneAlongItsMinorAxis() {
        Vec3 major = new Vec3(HALF_SQRT2, 0.0, HALF_SQRT2);
        Ring oval = new Ring(0, CENTRE, UP, RingShape.of(6.0, 3.0, major, UP), 10, RingType.STANDARD);
        Vec3 minor = new Vec3(HALF_SQRT2, 0.0, -HALF_SQRT2);

        assertThat(oval.contains(CENTRE.plus(major.scale(5.0)))).as("clearly inside along the major axis").isTrue();
        assertThat(oval.contains(CENTRE.plus(minor.scale(3.5)))).as("clearly outside along the minor axis").isFalse();
    }

    @Test
    void theRimIsInclusiveAtTheEndOfTheMajorAxis() {
        Ring oval = ovalAlongX(6.0, 3.0);

        assertThat(oval.contains(CENTRE.plus(new Vec3(6.0, 0.0, 0.0)))).isTrue();
    }

    @Test
    void theRimIsInclusiveAtTheEndOfTheMinorAxis() {
        Ring oval = ovalAlongX(6.0, 3.0);

        assertThat(oval.contains(CENTRE.plus(new Vec3(0.0, 0.0, 3.0)))).isTrue();
    }

    @Test
    void aPointInsideTheCircumscribedCircleButOutsideTheOvalIsRefused() {
        Ring oval = ovalAlongX(6.0, 3.0);

        assertThat(oval.contains(CENTRE.plus(new Vec3(0.0, 0.0, 5.0)))).isFalse();
    }

    @Test
    void aCircleOfRadius049IsRefusedNamingTheRadiusAndTheRange() {
        assertThatThrownBy(() -> new RingShape.Circle(0.49))
                .isInstanceOf(InvalidRingException.class)
                .hasMessageContaining("0.49")
                .hasMessageContaining("[0.5, 32");
    }

    @Test
    void aCircleOfRadiusHalfABlockIsAccepted() {
        assertThat(new RingShape.Circle(0.5).radius()).isEqualTo(0.5);
    }

    @Test
    void aCircleOfRadius32IsAccepted() {
        assertThat(new RingShape.Circle(32.0).radius()).isEqualTo(32.0);
    }

    @Test
    void aCircleOfRadius3201IsRefusedNamingTheRadiusAndTheRange() {
        assertThatThrownBy(() -> new RingShape.Circle(32.01))
                .isInstanceOf(InvalidRingException.class)
                .hasMessageContaining("32.01")
                .hasMessageContaining("[0.5, 32");
    }

    @Test
    void anOvalWhoseSemiMajorRadiusIsAboveTheBoundIsRefusedNamingTheValue() {
        assertThatThrownBy(() -> new RingShape.Ellipse(33.0, 3.0, ALONG_X))
                .isInstanceOf(InvalidRingException.class)
                .hasMessageContaining("33")
                .hasMessageContaining("[0.5, 32");
    }

    @Test
    void anOvalWhoseSemiMinorRadiusIsBelowTheBoundIsRefusedNamingRadiusMinor() {
        assertThatThrownBy(() -> new RingShape.Ellipse(6.0, 0.25, ALONG_X))
                .isInstanceOf(InvalidRingException.class)
                .hasMessageContaining("radiusMinor")
                .hasMessageContaining("0.25");
    }

    @Test
    void anOvalWhoseSemiMinorRadiusIsAboveItsSemiMajorRadiusIsRefusedNamingRadiusMinor() {
        assertThatThrownBy(() -> new RingShape.Ellipse(6.0, 7.0, ALONG_X))
                .isInstanceOf(InvalidRingException.class)
                .hasMessageContaining("radiusMinor");
    }

    @Test
    void anOvalWhoseMajorAxisIsNotOfUnitLengthIsRefusedNamingMajorAxis() {
        assertThatThrownBy(() -> new RingShape.Ellipse(6.0, 3.0, new Vec3(2.0, 0.0, 0.0)))
                .isInstanceOf(InvalidRingException.class)
                .hasMessageContaining("majorAxis");
    }

    @Test
    void anOvalWhoseMajorAxisIsNotPerpendicularToTheNormalIsRefusedNamingMajorAxis() {
        assertThatThrownBy(() -> RingShape.of(6.0, 3.0, UP, UP))
                .isInstanceOf(InvalidRingException.class)
                .hasMessageContaining("majorAxis");
    }

    @Test
    void anOvalWithEqualRadiiIsTheCircleOfThatRadius() {
        assertThat(RingShape.of(4.0, 4.0, ALONG_X, UP)).isEqualTo(new RingShape.Circle(4.0));
    }

    @Test
    void anOvalWithDifferentRadiiIsAnEllipse() {
        assertThat(RingShape.of(6.0, 3.0, ALONG_X, UP)).isEqualTo(new RingShape.Ellipse(6.0, 3.0, ALONG_X));
    }

    private static Ring ovalAlongX(double radius, double radiusMinor) {
        return new Ring(0, CENTRE, UP, RingShape.of(radius, radiusMinor, ALONG_X, UP), 10, RingType.STANDARD);
    }
}
