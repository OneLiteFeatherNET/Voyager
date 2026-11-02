package net.elytrarace.voyager.platform.rotation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Where a boot starts. The state file's cup wins when it is in the rotation and playable; otherwise the first playable cup
 * does, with one warning that says why. Every cup name here is canonical, except where a test says otherwise.
 */
class RotationPlanTest {

    private static final String ALPHA = "elytrarace:alpha_cup";
    private static final String LEGACY = "elytrarace:legacy_cup";
    private static final String THIRD = "elytrarace:third_cup";

    @Test
    void aBootWithoutAStateFileStartsAtTheFirstPlayableCup() {
        RotationPlan plan = RotationPlan.planned(List.of(ALPHA, LEGACY), Set.of(), Optional.empty(), true);

        assertThat(plan.boot()).contains(ALPHA);
        assertThat(plan.warnings()).isEmpty();
    }

    @Test
    void aBootStartsAtTheStoredCupWhenItIsInTheRotationAndPlayable() {
        RotationPlan plan = RotationPlan.planned(List.of(ALPHA, LEGACY), Set.of(), Optional.of(LEGACY), true);

        assertThat(plan.boot()).contains(LEGACY);
        assertThat(plan.warnings()).isEmpty();
    }

    @Test
    void aStoredCupWrittenBeforeTheKeysStartsAtItsKey() {
        RotationPlan plan = RotationPlan.planned(List.of(ALPHA, LEGACY), Set.of(), Optional.of("legacy_cup"), true);

        assertThat(plan.boot()).contains(LEGACY);
    }

    @Test
    void aStoredCupThatWasSkippedAtBootStartsAtTheFirstPlayableCupAndSaysSo() {
        RotationPlan plan = RotationPlan.planned(List.of(ALPHA, LEGACY), Set.of(LEGACY), Optional.of(LEGACY), true);

        assertThat(plan.boot()).contains(ALPHA);
        assertThat(plan.warnings()).singleElement().asString().contains(LEGACY).contains("skipped");
    }

    @Test
    void aStoredCupThatIsNotInTheRotationStartsAtTheFirstPlayableCupAndSaysSo() {
        RotationPlan plan = RotationPlan.planned(List.of(ALPHA, LEGACY), Set.of(), Optional.of(THIRD), true);

        assertThat(plan.boot()).contains(ALPHA);
        assertThat(plan.warnings()).singleElement().asString().contains(THIRD).contains("not in the rotation");
    }

    @Test
    void aRotationWithoutAStateFileWarnsOnceThatARestartBeginsAgain() {
        RotationPlan plan = RotationPlan.planned(List.of(ALPHA, LEGACY), Set.of(), Optional.empty(), false);

        assertThat(plan.warnings()).singleElement().asString()
                .contains("VOYAGER_CUP_ROTATION_STATE").contains("restart");
    }

    @Test
    void aRotationOfOneCupWithoutAStateFileDoesNotWarn() {
        RotationPlan plan = RotationPlan.planned(List.of(ALPHA), Set.of(), Optional.empty(), false);

        assertThat(plan.warnings()).isEmpty();
    }

    @Test
    void aPlanWithNoPlayableCupIsRefused() {
        assertThatThrownBy(() -> RotationPlan.planned(List.of(LEGACY), Set.of(LEGACY), Optional.empty(), true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aPlanWithoutARotationNamesOnlyTheCupTheSettingsNamed() {
        RotationPlan plan = RotationPlan.single(Optional.of(ALPHA));

        assertThat(plan.rotation()).isEmpty();
        assertThat(plan.boot()).contains(ALPHA);
    }
}
