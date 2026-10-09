package net.elytrarace.voyager.api.race;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.exception.InvalidGuideLineException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuidePointTest {

    private static final Vec3 SOMEWHERE = new Vec3(-79.36, -17.16, 76.31);

    /**
     * The order index says which pair of rings a guide bends the line between, and the answer is the
     * ring below it. 150 is between rings 1 and 2; 175 is too, which is the case the committed course
     * has twice over.
     */
    @Test
    void placesItselfAfterTheRingBelowItsOrderIndex() {
        assertThat(new GuidePoint(150, SOMEWHERE).afterRing()).isEqualTo(1);
        assertThat(new GuidePoint(175, SOMEWHERE).afterRing()).isEqualTo(1);
        assertThat(new GuidePoint(2_475, SOMEWHERE).afterRing()).isEqualTo(24);
    }

    /**
     * An order index in front of the first ring answers -1, not 0.
     *
     * <p>This is the whole reason {@code afterRing} floor-divides. Integer division rounds towards
     * zero, so {@code -50 / 100} is 0 and a guide sitting in front of the course would report itself
     * as bending the line between rings 0 and 1 — inside the course, where a map would accept it.
     */
    @Test
    void reportsAnOrderIndexInFrontOfTheCourseAsBeforeTheFirstRing() {
        assertThat(new GuidePoint(-50, SOMEWHERE).afterRing()).isEqualTo(-1);
        assertThat(new GuidePoint(-150, SOMEWHERE).afterRing()).isEqualTo(-2);
    }

    @Test
    void refusesAnOrderIndexOnARingsOwnSlot() {
        // Nothing would say whether the line reaches the guide or ring 3 first.
        assertThatThrownBy(() -> new GuidePoint(300, SOMEWHERE))
                .isInstanceOf(InvalidGuideLineException.class)
                .hasMessageContaining("ring 3's own slot");
        assertThatThrownBy(() -> new GuidePoint(0, SOMEWHERE))
                .isInstanceOf(InvalidGuideLineException.class);
    }

    @Test
    void keepsAnOrderIndexBetweenTwoRings() {
        assertThatCode(() -> new GuidePoint(299, SOMEWHERE)).doesNotThrowAnyException();
        assertThatCode(() -> new GuidePoint(301, SOMEWHERE)).doesNotThrowAnyException();
    }

    @Test
    void keepsThePositionItWasGiven() {
        assertThat(new GuidePoint(150, SOMEWHERE).position()).isEqualTo(SOMEWHERE);
    }
}
