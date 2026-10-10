package net.elytrarace.voyager.api.race;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.exception.InvalidGuideLineException;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuideLineTest {

    private static final GuidePoint EARLY = new GuidePoint(2_450, new Vec3(-59.5, 131.9, -82.4));

    private static final GuidePoint LATE = new GuidePoint(2_475, new Vec3(-90.3, 133.4, -89.8));

    private static final GuidePoint FAR_ON = new GuidePoint(2_650, new Vec3(-101.8, 158.1, -62.2));

    /**
     * The two order indices of the committed course's one doubled gap, given in the wrong order.
     *
     * <p>Sorted rather than refused: an order index carries the order whole, so list order adds
     * nothing to it, and a designer who appended a new guide to the end of the array has written
     * something unambiguous. What the sort has to be is stable against the input — these two are 25
     * apart and both between rings 24 and 25, so a line that kept file order would run out to the
     * second guide and double back to the first.
     */
    @Test
    void sortsItsPointsByOrderIndexWhateverOrderTheyArriveIn() {
        GuideLine line = new GuideLine(List.of(FAR_ON, LATE, EARLY), 2, 1.0);

        assertThat(line.points()).containsExactly(EARLY, LATE, FAR_ON);
    }

    @Test
    void refusesTwoPointsClaimingTheSameOrderIndex() {
        // Not a duplicate position — two different places both claiming to be reached at 2450, which
        // is the one thing the order axis cannot answer.
        GuidePoint collides = new GuidePoint(2_450, new Vec3(0.0, 131.9, -82.4));

        assertThatThrownBy(() -> new GuideLine(List.of(EARLY, collides), 2, 1.0))
                .isInstanceOf(InvalidGuideLineException.class)
                .hasMessageContaining("2450");
    }

    @Test
    void acceptsACourseWithNoGuidePointsAtAll() {
        GuideLine line = new GuideLine(List.of(), 2, 1.0);

        assertThat(line.points()).isEmpty();
    }

    @Test
    void refusesALookAheadThatReachesNoRingAtAll() {
        assertThatThrownBy(() -> new GuideLine(List.of(EARLY), 0, 1.0))
                .isInstanceOf(InvalidGuideLineException.class)
                .hasMessageContaining("at least one ring ahead");
    }

    /**
     * The spacing divides a length, so the finer it is the more packets one racer's stretch costs
     * every refresh. The floor is where a typo stops being a denser line and starts being a flood.
     */
    @Test
    void refusesAParticleSpacingFinerThanTheFloorOrNotANumber() {
        assertThatThrownBy(() -> new GuideLine(List.of(EARLY), 2, 0.01))
                .isInstanceOf(InvalidGuideLineException.class)
                .hasMessageContaining("0.25");
        assertThatThrownBy(() -> new GuideLine(List.of(EARLY), 2, 0.0))
                .isInstanceOf(InvalidGuideLineException.class);
        assertThatThrownBy(() -> new GuideLine(List.of(EARLY), 2, -1.0))
                .isInstanceOf(InvalidGuideLineException.class);
        assertThatThrownBy(() -> new GuideLine(List.of(EARLY), 2, Double.NaN))
                .isInstanceOf(InvalidGuideLineException.class);
        assertThatCode(() -> new GuideLine(List.of(EARLY), 2, GuideLine.MINIMUM_PARTICLE_SPACING))
                .doesNotThrowAnyException();
    }

    @Test
    void copiesItsPointListSoACallerCannotChangeItAfterwards() {
        List<GuidePoint> mutable = new ArrayList<>(List.of(EARLY, LATE));

        GuideLine line = new GuideLine(mutable, 2, 1.0);
        mutable.clear();

        assertThat(line.points()).containsExactly(EARLY, LATE);
    }

    @Test
    void keepsBothNumbersItWasGiven() {
        GuideLine line = new GuideLine(List.of(), 7, 2.5);

        assertThat(line.lookAheadRings()).isEqualTo(7);
        assertThat(line.particleSpacing()).isEqualTo(2.5);
    }
}
