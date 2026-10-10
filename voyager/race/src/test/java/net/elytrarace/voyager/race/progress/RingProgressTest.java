package net.elytrarace.voyager.race.progress;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The compact constructor's guards, which nothing exercised until the final review deleted both of
 * them and watched the whole suite stay green. They throw {@link IllegalArgumentException} because a
 * {@code RingProgress} is only ever built by {@link ProgressTracker}, so a bad value is a
 * programming error rather than bad data — but "only a bug can cause it" is a reason for the
 * exception type, not a reason to leave the guard undefended.
 */
class RingProgressTest {

    @Test
    void atStartHasPassedNothingAndReachedNoCheckpoint() {
        assertThat(RingProgress.atStart().passedCount()).isZero();
        assertThat(RingProgress.atStart().lastCheckpointIndex()).isEqualTo(-1);
    }

    @Test
    void rejectsANegativePassedCount() {
        assertThatThrownBy(() -> new RingProgress(-1, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("passedCount");
    }

    @Test
    void rejectsALastCheckpointIndexBelowMinusOne() {
        // -1 is the legal "no checkpoint reached yet" value, so the guard's boundary is -2, not 0.
        assertThat(new RingProgress(3, -1).lastCheckpointIndex()).isEqualTo(-1);

        assertThatThrownBy(() -> new RingProgress(3, -2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lastCheckpointIndex");
    }
}
