package net.elytrarace.server.ecs.component;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RingTrackerComponentTest {

    @Test
    void initiallyNoRingsPassed() {
        var tracker = new RingTrackerComponent();

        assertThat(tracker.passedCount()).isZero();
        assertThat(tracker.hasPassed(0)).isFalse();
        assertThat(tracker.hasPassed(5)).isFalse();
    }

    @Test
    void markPassedRegistersRing() {
        var tracker = new RingTrackerComponent();

        tracker.markPassed(3);

        assertThat(tracker.hasPassed(3)).isTrue();
        assertThat(tracker.hasPassed(0)).isFalse();
        assertThat(tracker.passedCount()).isEqualTo(1);
    }

    @Test
    void markPassedIsIdempotent() {
        var tracker = new RingTrackerComponent();

        tracker.markPassed(2);
        tracker.markPassed(2);

        assertThat(tracker.passedCount()).isEqualTo(1);
    }

    @Test
    void multipleRingsCanBePassed() {
        var tracker = new RingTrackerComponent();

        tracker.markPassed(0);
        tracker.markPassed(1);
        tracker.markPassed(4);

        assertThat(tracker.passedCount()).isEqualTo(3);
        assertThat(tracker.getPassedRings()).containsExactlyInAnyOrder(0, 1, 4);
    }

    @Test
    void resetClearsAllPassedRings() {
        var tracker = new RingTrackerComponent();
        tracker.markPassed(0);
        tracker.markPassed(1);

        tracker.reset();

        assertThat(tracker.passedCount()).isZero();
        assertThat(tracker.hasPassed(0)).isFalse();
        assertThat(tracker.hasPassed(1)).isFalse();
    }
}
