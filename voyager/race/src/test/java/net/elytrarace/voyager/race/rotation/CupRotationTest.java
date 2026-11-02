package net.elytrarace.voyager.race.rotation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The rotation rule: the cup a round plays is the next one in the list, the last one wraps to the first, and a
 * rotation is never empty and never names a cup twice.
 *
 * <p>The cup names are distinct from one another and from every index, so a rotation that armed a position where a
 * name belonged would not pass. Each test builds its own rotation: nothing is shared between them.
 */
class CupRotationTest {

    private static final String ALPHA = "elytrarace:alpha_cup";
    private static final String LEGACY = "elytrarace:legacy_cup";
    private static final String THIRD = "elytrarace:third_cup";

    @Test
    void aFinishedCupArmsTheNextCupOfTheList() {
        CupRotation rotation = CupRotation.of(List.of(ALPHA, LEGACY, THIRD));

        assertThat(rotation.advance().armed()).isEqualTo(LEGACY);
    }

    @Test
    void theLastCupOfTheListWrapsToTheFirst() {
        CupRotation rotation = CupRotation.starting(List.of(ALPHA, LEGACY, THIRD), THIRD);

        assertThat(rotation.advance().armed()).isEqualTo(ALPHA);
    }

    @Test
    void aRotationStartsAtTheCupNamedAsArmed() {
        CupRotation rotation = CupRotation.starting(List.of(ALPHA, LEGACY), LEGACY);

        assertThat(rotation.armed()).isEqualTo(LEGACY);
    }

    @Test
    void advancingLeavesTheRotationItWasCalledOn() {
        CupRotation rotation = CupRotation.of(List.of(ALPHA, LEGACY));

        rotation.advance();

        assertThat(rotation.armed()).isEqualTo(ALPHA);
    }

    @Test
    void aRotationOfOneCupDoesNotRotateAndArmsItAgain() {
        CupRotation rotation = CupRotation.of(List.of(ALPHA));

        assertThat(rotation.rotates()).isFalse();
        assertThat(rotation.advance().armed()).isEqualTo(ALPHA);
    }

    @Test
    void aRotationOfTwoOrMoreCupsRotates() {
        assertThat(CupRotation.of(List.of(ALPHA, LEGACY)).rotates()).isTrue();
    }

    @Test
    void anEmptyRotationIsRefused() {
        assertThatThrownBy(() -> CupRotation.of(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one cup");
    }

    @Test
    void aCupNamedTwiceIsRefusedAndTheMessageNamesIt() {
        assertThatThrownBy(() -> CupRotation.of(List.of(ALPHA, LEGACY, ALPHA)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ALPHA)
                .hasMessageContaining("twice");
    }

    @Test
    void aStartingCupThatIsNotInTheRotationIsRefused() {
        assertThatThrownBy(() -> CupRotation.starting(List.of(ALPHA, LEGACY), THIRD))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(THIRD);
    }
}
