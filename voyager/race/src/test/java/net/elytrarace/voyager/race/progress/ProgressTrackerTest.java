package net.elytrarace.voyager.race.progress;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProgressTrackerTest {

    /**
     * Three rings that differ in more than position: the middle one is tilted and is the checkpoint,
     * and the radii differ. Three identical rings at three z values would let a tracker that ignores
     * everything but the index pass every test here.
     */
    private static final Ring FIRST =
            new Ring(0, new Vec3(0.0, 64.0, 100.0), new Vec3(0.0, 0.0, 1.0), 5.0, 10, RingType.STANDARD);
    private static final Ring SECOND =
            new Ring(1, new Vec3(0.0, 64.0, 200.0), new Vec3(0.6, 0.0, 0.8), 7.0, 10, RingType.CHECKPOINT);
    private static final Ring THIRD =
            new Ring(2, new Vec3(0.0, 64.0, 300.0), new Vec3(0.0, 0.0, 1.0), 3.0, 10, RingType.BOOST);
    private static final List<Ring> RINGS = List.of(FIRST, SECOND, THIRD);

    private static final RingProgress START = RingProgress.atStart();

    private static ProgressUpdate flyThrough(RingProgress from, double fromZ, double toZ) {
        return ProgressTracker.advance(from, RINGS,
                new Vec3(0.0, 64.0, fromZ), new Vec3(0.0, 64.0, toZ), true);
    }

    @Test
    void startsWithNothingPassedAndNoCheckpoint() {
        assertThat(START.passedCount()).isZero();
        assertThat(START.lastCheckpointIndex()).isEqualTo(-1);
    }

    @Test
    void countsTheFirstRing() {
        ProgressUpdate update = flyThrough(START, 99.0, 101.0);

        assertThat(update.passed()).isEqualTo(FIRST);
        assertThat(update.progress().passedCount()).isEqualTo(1);
    }

    @Test
    void ignoresTheSecondRingWhileTheFirstIsStillOpen() {
        // Flying clean through ring 1's plane changes nothing: only ring 0 is under test.
        ProgressUpdate update = flyThrough(START, 199.0, 201.0);

        assertThat(update.passed()).isNull();
        assertThat(update.progress()).isEqualTo(START);
    }

    @Test
    void countsAtMostOneRingPerTick() {
        // A step long enough to cross both planes in one tick. Only the next one in order counts;
        // the other is still open on the following tick.
        ProgressUpdate update = flyThrough(START, 99.0, 201.0);

        assertThat(update.passed()).isEqualTo(FIRST);
        assertThat(update.progress().passedCount()).isEqualTo(1);
    }

    @Test
    void doesNotCountTheSameRingTwice() {
        ProgressUpdate first = flyThrough(START, 99.0, 101.0);
        ProgressUpdate second = flyThrough(first.progress(), 101.0, 99.0);

        assertThat(second.passed()).isNull();
        assertThat(second.progress().passedCount()).isEqualTo(1);
    }

    @Test
    void countsNothingOnTheFirstTickAfterATeleport() {
        // No previous position means no segment, and a teleport across a ring's plane would
        // otherwise register as a pass.
        ProgressUpdate update = ProgressTracker.advance(START, RINGS,
                null, new Vec3(0.0, 64.0, 101.0), true);

        assertThat(update.passed()).isNull();
        assertThat(update.progress()).isEqualTo(START);
    }

    @Test
    void countsNothingWhileNotGliding() {
        ProgressUpdate update = ProgressTracker.advance(START, RINGS,
                new Vec3(0.0, 64.0, 99.0), new Vec3(0.0, 64.0, 101.0), false);

        assertThat(update.passed()).isNull();
        assertThat(update.progress()).isEqualTo(START);
    }

    @Test
    void remembersTheLastCheckpointAndLeavesItAloneOtherwise() {
        RingProgress afterFirst = flyThrough(START, 99.0, 101.0).progress();
        assertThat(afterFirst.lastCheckpointIndex()).isEqualTo(-1);

        RingProgress afterSecond = flyThrough(afterFirst, 199.0, 201.0).progress();
        assertThat(afterSecond.lastCheckpointIndex()).isEqualTo(1);

        RingProgress afterThird = flyThrough(afterSecond, 299.0, 301.0).progress();
        assertThat(afterThird.lastCheckpointIndex())
                .as("a BOOST ring must not move the checkpoint")
                .isEqualTo(1);
    }

    @Test
    void staysPutOnceEveryRingIsPassed() {
        RingProgress finished = flyThrough(
                flyThrough(flyThrough(START, 99.0, 101.0).progress(), 199.0, 201.0).progress(),
                299.0, 301.0).progress();
        assertThat(finished.passedCount()).isEqualTo(3);

        ProgressUpdate afterwards = flyThrough(finished, 299.0, 301.0);

        assertThat(afterwards.passed()).isNull();
        assertThat(afterwards.progress()).isEqualTo(finished);
    }

    // --- Fixture sweep additions (task-3 step 6) ---
    //
    // In every case above, RINGS.get(i).index() equals i: FIRST is at position 0 and has index 0,
    // SECOND at position 1 has index 1, THIRD at position 2 has index 2. A tracker that set
    // lastCheckpointIndex from current.passedCount() (the list position) instead of next.index()
    // (the ring's own identity) would still pass every test above, because at the moment SECOND is
    // passed both values happen to be 1. This ring list breaks that coincidence by giving the
    // checkpoint an index far from its position in the list.

    private static final Ring OUT_OF_ORDER_CHECKPOINT =
            new Ring(9, new Vec3(0.0, 64.0, 200.0), new Vec3(0.6, 0.0, 0.8), 7.0, 10, RingType.CHECKPOINT);
    private static final List<Ring> RINGS_WITH_DIVERGENT_INDEX = List.of(FIRST, OUT_OF_ORDER_CHECKPOINT, THIRD);

    @Test
    void usesTheRingsOwnIndexForTheCheckpointNotItsListPosition() {
        ProgressUpdate afterFirst = ProgressTracker.advance(START, RINGS_WITH_DIVERGENT_INDEX,
                new Vec3(0.0, 64.0, 99.0), new Vec3(0.0, 64.0, 101.0), true);

        ProgressUpdate afterCheckpoint = ProgressTracker.advance(afterFirst.progress(), RINGS_WITH_DIVERGENT_INDEX,
                new Vec3(0.0, 64.0, 199.0), new Vec3(0.0, 64.0, 201.0), true);

        assertThat(afterCheckpoint.passed()).isEqualTo(OUT_OF_ORDER_CHECKPOINT);
        assertThat(afterCheckpoint.progress().lastCheckpointIndex()).isEqualTo(9);
    }
}
