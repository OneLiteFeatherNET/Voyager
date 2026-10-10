package net.elytrarace.voyager.race.reset;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.GuideLine;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;
import net.elytrarace.voyager.race.flow.RaceClock;
import net.elytrarace.voyager.race.progress.RingProgress;
import net.elytrarace.voyager.race.reset.exception.RunNotResettableException;
import net.elytrarace.voyager.race.run.RaceRun;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * {@link RunReset}'s pure rules, on a course whose three rings are of three different types, so a
 * rule that reads the wrong ring, or that reads a type it should not, cannot pass by accident.
 */
class RunResetTest {

    private static final double FLOOR = -64.0;
    private static final double CEILING = 320.0;

    private static final Duration STEP = Duration.ofMillis(50);

    private static final Vec3 SPAWN = new Vec3(0.0, 65.0, 0.0);

    /** Checkpoint at ring 0: a plain flow along +z. */
    private static final Ring CHECKPOINT = new Ring(0, new Vec3(0.0, 64.0, 10.0), new Vec3(0.0, 0.0, 1.0), 5.0, 7,
            RingType.CHECKPOINT);

    /** Standard ring at ring 1, flowing diagonally, so the facing and the normal can be told apart. */
    private static final Vec3 STANDARD_NORMAL = new Vec3(0.6, 0.0, 0.8);
    private static final Ring STANDARD = new Ring(1, new Vec3(0.0, 70.0, 20.0), STANDARD_NORMAL, 5.0, 9,
            RingType.STANDARD);

    private static final Ring BOOST = new Ring(2, new Vec3(0.0, 66.0, 30.0), new Vec3(0.0, 0.0, 1.0), 5.0, 11,
            RingType.BOOST);

    private static final MapDefinition COURSE = new MapDefinition("reset-course", "reset_arena", SPAWN,
            List.of(CHECKPOINT, STANDARD, BOOST), Duration.ofSeconds(4), new BoostConfig(12, 25),
            new GuideLine(List.of(), 2, 1.0));

    // ------------------------------------------------------------------------------------------
    // Out of bounds
    // ------------------------------------------------------------------------------------------

    @Test
    void aPositionBelowTheFloorIsOutOfBounds() {
        assertThat(RunReset.isOutOfBounds(new Vec3(0, -65, 0), FLOOR, CEILING)).isTrue();
    }

    @Test
    void aPositionExactlyOnTheFloorIsInBounds() {
        assertThat(RunReset.isOutOfBounds(new Vec3(0, FLOOR, 0), FLOOR, CEILING)).isFalse();
    }

    @Test
    void aPositionAboveTheCeilingIsOutOfBounds() {
        assertThat(RunReset.isOutOfBounds(new Vec3(0, 321, 0), FLOOR, CEILING)).isTrue();
    }

    @Test
    void aPositionExactlyOnTheCeilingIsInBounds() {
        assertThat(RunReset.isOutOfBounds(new Vec3(0, CEILING, 0), FLOOR, CEILING)).isFalse();
    }

    @Test
    void aPositionBetweenTheFloorAndTheCeilingIsInBounds() {
        assertThat(RunReset.isOutOfBounds(new Vec3(0, 65, 0), FLOOR, CEILING)).isFalse();
    }

    @Test
    void aCeilingBelowTheFloorIsRefused() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> RunReset.isOutOfBounds(SPAWN, CEILING, FLOOR));
    }

    // ------------------------------------------------------------------------------------------
    // Landing
    // ------------------------------------------------------------------------------------------

    @Test
    void aGlidingRacerWhoStandsOnTheGroundHasLanded() {
        RaceRun gliding = gliding(progress(1), List.of(5));

        assertThat(RunReset.isLanded(gliding, false, true)).isTrue();
    }

    @Test
    void takingOffFromStandingIsNotALanding() {
        RaceRun standing = RaceRun.atStart();

        assertThat(RunReset.isLanded(standing, true, true)).isFalse();
    }

    @Test
    void aGlideThatStopsInTheAirIsNotALanding() {
        RaceRun gliding = gliding(progress(1), List.of(5));

        assertThat(RunReset.isLanded(gliding, false, false)).isFalse();
    }

    @Test
    void aRacerStillGlidingInTheAirIsNotALanding() {
        RaceRun gliding = gliding(progress(1), List.of(5));

        assertThat(RunReset.isLanded(gliding, true, false)).isFalse();
    }

    @Test
    void aFinishedRunCannotLand() {
        RaceRun finished = new RaceRun(progress(3), SPAWN, List.of(5, 9, 12),
                Optional.of(new RaceClock(12, STEP)), null, true);

        assertThat(RunReset.isLanded(finished, false, true)).isFalse();
    }

    @Test
    void aRacerWhoNeverGlidedCannotLand() {
        assertThat(RunReset.isLanded(RaceRun.atStart(), false, true)).isFalse();
    }

    // ------------------------------------------------------------------------------------------
    // The reset plan: target
    // ------------------------------------------------------------------------------------------

    @Test
    void theTargetOfAResetIsTheCentreOfTheLastRingPassed() {
        RaceRun run = gliding(progress(2), List.of(5, 9));

        ResetPlan plan = RunReset.plan(COURSE, run, ResetCause.OUT_OF_BOUNDS);

        assertThat(plan.position())
                .describedAs("the last ring passed is ring 1, a standard ring, not the checkpoint")
                .isEqualTo(STANDARD.center());
    }

    @Test
    void theRacerFacesAlongTheFlowNormalOfTheTargetRing() {
        RaceRun run = gliding(progress(2), List.of(5, 9));

        ResetPlan plan = RunReset.plan(COURSE, run, ResetCause.OUT_OF_BOUNDS);

        assertThat(plan.toward())
                .describedAs("a point one step along the normal from the ring's centre")
                .isEqualTo(STANDARD.center().plus(STANDARD_NORMAL));
    }

    @Test
    void aCheckpointIsATargetLikeAnyOtherRing() {
        RaceRun run = gliding(progress(1), List.of(5));

        ResetPlan plan = RunReset.plan(COURSE, run, ResetCause.LANDED);

        assertThat(plan.position()).isEqualTo(CHECKPOINT.center());
    }

    @Test
    void theTargetRingNumberIsTheOneBasedNumberOfTheLastRingPassed() {
        RaceRun run = gliding(progress(2), List.of(5, 9));

        ResetPlan plan = RunReset.plan(COURSE, run, ResetCause.LANDED);

        assertThat(plan.targetRing()).isEqualTo(OptionalInt.of(2));
    }

    @Test
    void aRacerWhoPassedNoRingIsSentToTheSpawn() {
        ResetPlan plan = RunReset.plan(COURSE, RaceRun.atStart(), ResetCause.LANDED);

        assertThat(plan.position()).isEqualTo(SPAWN);
    }

    @Test
    void aRacerSentToTheSpawnFacesTheFirstRingAsAMapStartDoes() {
        ResetPlan plan = RunReset.plan(COURSE, RaceRun.atStart(), ResetCause.LANDED);

        assertThat(plan.toward()).isEqualTo(CHECKPOINT.center());
    }

    @Test
    void aSpawnTargetHasNoRingNumber() {
        ResetPlan plan = RunReset.plan(COURSE, RaceRun.atStart(), ResetCause.LANDED);

        assertThat(plan.targetRing()).isEqualTo(OptionalInt.empty());
    }

    // ------------------------------------------------------------------------------------------
    // The reset plan: what the run becomes
    // ------------------------------------------------------------------------------------------

    @Test
    void everyRingAlreadyPassedStaysPassed() {
        RaceRun run = gliding(progress(2), List.of(5, 9));

        ResetPlan plan = RunReset.plan(COURSE, run, ResetCause.OUT_OF_BOUNDS);

        assertThat(plan.run().progress()).isEqualTo(new RingProgress(2, -1));
        assertThat(plan.run().passedOnGameTick()).containsExactly(5, 9);
    }

    @Test
    void aRunThatHasNotFinishedStaysUnfinished() {
        RaceRun run = gliding(progress(2), List.of(5, 9));

        ResetPlan plan = RunReset.plan(COURSE, run, ResetCause.OUT_OF_BOUNDS);

        assertThat(plan.run().finished()).isFalse();
    }

    @Test
    void theRunLosesItsSegmentSoTheFirstTickAfterTheResetTestsNothing() {
        RaceRun run = gliding(progress(2), List.of(5, 9));

        ResetPlan plan = RunReset.plan(COURSE, run, ResetCause.OUT_OF_BOUNDS);

        assertThat(plan.run().previous()).isNull();
    }

    @Test
    void theRunLosesItsGlideSoTheRelaunchIsNotALanding() {
        RaceRun run = gliding(progress(2), List.of(5, 9));

        ResetPlan plan = RunReset.plan(COURSE, run, ResetCause.LANDED);

        assertThat(plan.run().gliding()).isFalse();
    }

    @Test
    void theRunLosesTheRingOfItsTickSoItDescribesNoPass() {
        RaceRun run = gliding(progress(2), List.of(5, 9));

        ResetPlan plan = RunReset.plan(COURSE, run, ResetCause.LANDED);

        assertThat(plan.run().justPassed()).isNull();
    }

    @Test
    void aRunWithNoRingPassedStaysAtTheStart() {
        ResetPlan plan = RunReset.plan(COURSE, RaceRun.atStart(), ResetCause.OUT_OF_BOUNDS);

        assertThat(plan.run().progress()).isEqualTo(RingProgress.atStart());
    }

    @Test
    void aFinishedRunIsRefused() {
        RaceRun finished = new RaceRun(progress(3), SPAWN, List.of(5, 9, 12),
                Optional.of(new RaceClock(12, STEP)), null, false);

        assertThatExceptionOfType(RunNotResettableException.class)
                .isThrownBy(() -> RunReset.plan(COURSE, finished, ResetCause.OUT_OF_BOUNDS));
    }

    @Test
    void thePlanRecordsItsCause() {
        ResetPlan plan = RunReset.plan(COURSE, RaceRun.atStart(), ResetCause.LANDED);

        assertThat(plan.cause()).isEqualTo(ResetCause.LANDED);
    }

    // ------------------------------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------------------------------

    /** A progress with {@code passed} rings passed, the last checkpoint index left at -1. */
    private static RingProgress progress(int passed) {
        return new RingProgress(passed, -1);
    }

    /** A run that is gliding on the tick it produced, with one tick per ring passed. */
    private static RaceRun gliding(RingProgress progress, List<Integer> ticks) {
        return new RaceRun(progress, SPAWN, ticks, Optional.empty(), null, true);
    }
}
