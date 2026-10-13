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
import java.util.ArrayList;
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

    /** Airborne race ticks a racer needs before a landing counts (design D4). */
    private static final int LANDING_GRACE_TICKS = 20;

    /** Ten movement ticks, one for each ring of {@link #LONG_COURSE}. */
    private static final List<Integer> TEN_TICKS = List.of(5, 9, 12, 15, 18, 21, 24, 27, 30, 33);

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
    void aResetTargetsTheLastCheckpointPassed() {
        RaceRun run = gliding(new RingProgress(10, 6), TEN_TICKS);

        ResetPlan plan = RunReset.plan(LONG_COURSE, run, ResetCause.OUT_OF_BOUNDS);

        Ring checkpoint = LONG_COURSE.rings().get(6);
        assertThat(plan.position())
                .describedAs("ring 6 is the last checkpoint passed, so it is the target and not ring 9")
                .isEqualTo(checkpoint.center());
        assertThat(plan.toward())
                .describedAs("one step along the normal of ring 6, which is tilted and so tells it apart")
                .isEqualTo(checkpoint.center().plus(checkpoint.normal()));
    }

    @Test
    void aResetWithNoCheckpointTargetsTheSpawn() {
        RaceRun run = gliding(new RingProgress(10, -1), TEN_TICKS);

        ResetPlan plan = RunReset.plan(LONG_COURSE, run, ResetCause.OUT_OF_BOUNDS);

        assertThat(plan.position())
                .describedAs("no checkpoint was passed, so the target is the spawn and not ring 9")
                .isEqualTo(LONG_COURSE.spawn());
        assertThat(plan.toward())
                .describedAs("the spawn faces the centre of the first ring, as a map start does")
                .isEqualTo(LONG_COURSE.rings().getFirst().center());
    }

    @Test
    void theTargetRingNumberIsTheOneBasedNumberOfTheCheckpoint() {
        RaceRun run = gliding(new RingProgress(10, 6), TEN_TICKS);

        ResetPlan plan = RunReset.plan(LONG_COURSE, run, ResetCause.LANDED);

        assertThat(plan.targetRing()).isEqualTo(OptionalInt.of(7));
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
    void aCheckpointResetRemovesTheRingsPassedAfterIt() {
        RaceRun run = gliding(new RingProgress(10, 6), TEN_TICKS);

        ResetPlan plan = RunReset.plan(LONG_COURSE, run, ResetCause.OUT_OF_BOUNDS);

        assertThat(plan.run().progress())
                .describedAs("rings 0 to 6 stay passed, and the checkpoint stays the last one passed")
                .isEqualTo(new RingProgress(7, 6));
        assertThat(plan.run().passedOnGameTick())
                .describedAs("one tick per ring kept, and the ticks of rings 7 to 9 are gone")
                .containsExactly(5, 9, 12, 15, 18, 21, 24);
    }

    @Test
    void aSpawnResetKeepsNoRing() {
        RaceRun run = gliding(new RingProgress(10, -1), TEN_TICKS);

        ResetPlan plan = RunReset.plan(LONG_COURSE, run, ResetCause.OUT_OF_BOUNDS);

        assertThat(plan.run().progress().passedCount()).isZero();
        assertThat(plan.run().passedOnGameTick()).isEmpty();
    }

    @Test
    void aResetRunIsGlidingWithACooldownAndNoAirborneTicks() {
        RaceRun run = gliding(new RingProgress(10, 6), TEN_TICKS);

        ResetPlan plan = RunReset.plan(LONG_COURSE, run, ResetCause.LANDED);

        assertThat(plan.run().gliding()).describedAs("the racer is placed gliding").isTrue();
        assertThat(plan.run().resetCooldownTicks()).describedAs("the cooldown after a reset").isEqualTo(40);
        assertThat(plan.run().airborneTicks()).describedAs("no tick airborne yet").isZero();
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

    /**
     * A run that is gliding on the tick it produced, past the landing grace and outside any cooldown, so
     * that only the ground can make a landing of it.
     */
    private static RaceRun gliding(RingProgress progress, List<Integer> ticks) {
        return new RaceRun(progress, SPAWN, ticks, Optional.empty(), null, true, LANDING_GRACE_TICKS, 0);
    }

    /** A course of ten rings with checkpoints at 2 and 6, every normal tilted the same way. */
    private static final MapDefinition LONG_COURSE = longCourse();

    private static MapDefinition longCourse() {
        List<Ring> rings = new ArrayList<>();
        for (int index = 0; index < TEN_TICKS.size(); index++) {
            RingType type = index == 2 || index == 6 ? RingType.CHECKPOINT : RingType.STANDARD;
            rings.add(new Ring(index, new Vec3(0.0, 64.0, 10.0 + 10.0 * index), STANDARD_NORMAL, 5.0, 9, type));
        }
        return new MapDefinition("long-reset-course", "reset_arena", SPAWN, rings, Duration.ofSeconds(4),
                new BoostConfig(12, 25), new GuideLine(List.of(), 2, 1.0));
    }
}
