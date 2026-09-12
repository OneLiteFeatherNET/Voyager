package net.elytrarace.voyager.race.run;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;
import net.elytrarace.voyager.race.collision.RingPass;
import net.elytrarace.voyager.race.flow.RaceClock;
import net.elytrarace.voyager.race.progress.RingProgress;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * {@link RaceRun} against its own surface, on a lane whose every crossing tick is hand-derived.
 *
 * <h2>Why the fixture is shaped this way</h2>
 *
 * <p>The lane is off-axis in {@code x} and {@code y}, the course starts at {@code z = 3} rather than
 * at zero, and every normal is tilted, so the crossing of the lane with a ring's plane is
 *
 * <pre>
 *   crossing z = ring.cz + (nx * (ring.cx - laneX) + ny * (ring.cy - laneY)) / nz
 *   game tick  = ceil((crossing z - spawn z) / blocksPerTick)
 * </pre>
 *
 * <p>and not simply the ring's own {@code z} over the speed. The three crossing ticks that follow —
 * 5, 12 and 19 — are therefore distinct from the ring indices 0, 1, 2, from the ring counts 1, 2, 3
 * that produce them, and from each other, so a run that recorded a ring index where a tick belongs,
 * or a count where a tick belongs, cannot pass. The ring types differ too (STANDARD, BOOST,
 * CHECKPOINT), because {@link RaceRun#justPassed()} exists precisely so a driver can read the type
 * off the ring it just passed.
 *
 * <pre>
 *   ring  centre                     normal                  crossing z   tick
 *     0   ( 0.0, 64.0,  28.0)        ( 0.60, 0.00, 0.80)        27.0625      5
 *     1   ( 2.5, 67.0,  61.0)        ( 0.00,-0.60, 0.80)        59.8750     12
 *     2   (-1.0, 63.0,  94.0)        (-0.36, 0.48, 0.80)        93.5125     19
 * </pre>
 */
class RaceRunTest {

    /** One Minecraft tick at 20 TPS. */
    private static final Duration TICK = Duration.ofMillis(50);

    /**
     * A phase length that is not a multiple of any crossing tick's race time, so a DNF handed it
     * cannot be mistaken for a finish time.
     */
    private static final Duration RACE_LENGTH = Duration.ofMillis(8_000);

    private static final double LANE_X = 1.25;
    private static final double LANE_Y = 65.5;
    private static final double SPAWN_Z = 3.0;
    private static final double BLOCKS_PER_TICK = 5.0;

    private static final Vec3 SPAWN = new Vec3(LANE_X, LANE_Y, SPAWN_Z);

    private static final MapDefinition COURSE = new MapDefinition("ridge-run", "ridge_arena", SPAWN,
            List.of(
                    new Ring(0, new Vec3(0.0, 64.0, 28.0), new Vec3(0.6, 0.0, 0.8), 6.0, 7, RingType.STANDARD),
                    new Ring(1, new Vec3(2.5, 67.0, 61.0), new Vec3(0.0, -0.6, 0.8), 7.0, 11, RingType.BOOST),
                    new Ring(2, new Vec3(-1.0, 63.0, 94.0), new Vec3(-0.36, 0.48, 0.8), 8.0, 21,
                            RingType.CHECKPOINT)),
            Duration.ofSeconds(4));

    /**
     * A second course whose only ring sits at crossing {@code z} 5.5625 — between the spawn and the
     * position the first movement tick reaches at {@code z} 8.0. Flying it is the only way to tell a
     * run that declines to test the first tick's segment from one that never got near a ring.
     */
    private static final MapDefinition START_LINE = new MapDefinition("start-line", "ridge_arena", SPAWN,
            List.of(new Ring(0, new Vec3(0.0, 64.0, 6.5), new Vec3(0.6, 0.0, 0.8), 6.0, 5, RingType.STANDARD)),
            Duration.ofSeconds(4));

    /** Ring counts after which the elytra closes; the course has three rings. */
    private static final int NEVER_STOPS_GLIDING = Integer.MAX_VALUE;

    private static final int FIRST_RING_TICK = 5;
    private static final int SECOND_RING_TICK = 12;
    private static final int LAST_RING_TICK = 19;

    // ------------------------------------------------------------------------------------------
    // Passing rings
    // ------------------------------------------------------------------------------------------

    @Test
    void aRunStartsWithNothingRecorded() {
        RaceRun run = RaceRun.atStart();

        assertThat(run.progress()).isEqualTo(new RingProgress(0, -1));
        assertThat(run.previous()).isNull();
        assertThat(run.passedOnGameTick()).isEmpty();
        assertThat(run.finishedAt()).isEmpty();
        assertThat(run.justPassed()).isNull();
        assertThat(run.finished()).isFalse();
    }

    @Test
    void aRunRecordsTheTickItPassedARingOnAndTheRingItself() {
        RaceRun run = fly(RaceRun.atStart(), COURSE, TICK, FIRST_RING_TICK, NEVER_STOPS_GLIDING);

        assertThat(run.passedOnGameTick()).containsExactly(FIRST_RING_TICK);
        assertThat(run.progress().passedCount()).isEqualTo(1);
        assertThat(run.justPassed()).isEqualTo(COURSE.rings().getFirst());
        assertThat(run.justPassed().type()).isEqualTo(RingType.STANDARD);
        assertThat(run.finishedAt()).isEmpty();
        assertThat(run.finished()).isFalse();
    }

    /**
     * {@code justPassed} describes the tick that produced the run, so it is gone again on the next
     * one — a driver that applied a ring's effect from a stored run rather than from the one
     * {@link RaceRun#advance} returned would otherwise apply it on every tick until the next ring.
     */
    @Test
    void theTickAfterAPassCarriesNoRingButKeepsEverythingElse() {
        RaceRun run = fly(RaceRun.atStart(), COURSE, TICK, FIRST_RING_TICK + 1, NEVER_STOPS_GLIDING);

        assertThat(run.justPassed()).isNull();
        assertThat(run.passedOnGameTick()).containsExactly(FIRST_RING_TICK);
        assertThat(run.progress().passedCount()).isEqualTo(1);
    }

    @Test
    void everyRingIsRecordedOnTheTickTheLaneGeometrySays() {
        RaceRun run = fly(RaceRun.atStart(), COURSE, LAST_RING_TICK, NEVER_STOPS_GLIDING);

        assertThat(run.passedOnGameTick())
                .containsExactly(FIRST_RING_TICK, SECOND_RING_TICK, LAST_RING_TICK);
        assertThat(run.justPassed()).isEqualTo(COURSE.rings().getLast());
        assertThat(run.justPassed().type()).isEqualTo(RingType.CHECKPOINT);

        // The progress the tracker produced is carried through untouched, checkpoint and all.
        assertThat(run.progress()).isEqualTo(new RingProgress(3, 2));
    }

    @Test
    void everyTicksPositionBecomesTheNextTicksSegmentStart() {
        RaceRun run = fly(RaceRun.atStart(), COURSE, 3, NEVER_STOPS_GLIDING);

        assertThat(run.previous()).isEqualTo(positionAt(3));
        assertThat(run.previous()).isNotEqualTo(positionAt(2));
    }

    // ------------------------------------------------------------------------------------------
    // Finishing, and not finishing
    // ------------------------------------------------------------------------------------------

    @Test
    void aRunFinishesOnTheTickItPassesTheLastRing() {
        RaceRun run = fly(RaceRun.atStart(), COURSE, LAST_RING_TICK, NEVER_STOPS_GLIDING);

        assertThat(run.finished()).isTrue();
        assertThat(run.finishedAt()).contains(new RaceClock(LAST_RING_TICK, TICK));
        assertThat(run.finishedAt().orElseThrow().gameTick()).isEqualTo(LAST_RING_TICK);
    }

    /** The tick before the last ring is not a finish; the finish is not recorded one tick early. */
    @Test
    void aRunIsNotFinishedOnTheTickBeforeItsLastRing() {
        RaceRun run = fly(RaceRun.atStart(), COURSE, LAST_RING_TICK - 1, NEVER_STOPS_GLIDING);

        assertThat(run.finished()).isFalse();
        assertThat(run.progress().passedCount()).isEqualTo(2);
        assertThat(run.passedOnGameTick()).containsExactly(FIRST_RING_TICK, SECOND_RING_TICK);
    }

    /** Flying on past the last ring changes nothing: the finish keeps the tick it happened on. */
    @Test
    void aFinishedRunKeepsItsFinishTickWhenItKeepsFlying() {
        RaceRun run = fly(RaceRun.atStart(), COURSE, LAST_RING_TICK + 7, NEVER_STOPS_GLIDING);

        assertThat(run.finishedAt()).contains(new RaceClock(LAST_RING_TICK, TICK));
        assertThat(run.passedOnGameTick())
                .containsExactly(FIRST_RING_TICK, SECOND_RING_TICK, LAST_RING_TICK);
        assertThat(run.justPassed()).isNull();
    }

    /**
     * A racer who closes the elytra after two rings keeps both of them and never finishes — and the
     * ring he declined was one he flew straight through, so "he did not pass it" cannot quietly
     * become "he never got near it".
     */
    @Test
    void aRunThatStopsGlidingNeverFinishesButKeepsTheRingsItPassed() {
        RaceRun run = fly(RaceRun.atStart(), COURSE, LAST_RING_TICK + 5, 2);

        assertThat(run.finished()).isFalse();
        assertThat(run.finishedAt()).isEmpty();
        assertThat(run.passedOnGameTick()).containsExactly(FIRST_RING_TICK, SECOND_RING_TICK);
        assertThat(run.progress().passedCount()).isEqualTo(2);

        assertThat(RingPass.crosses(positionAt(LAST_RING_TICK - 1), positionAt(LAST_RING_TICK),
                COURSE.rings().getLast())).isTrue();
    }

    // ------------------------------------------------------------------------------------------
    // The first tick after a teleport
    // ------------------------------------------------------------------------------------------

    /**
     * The tick a player is teleported to the start has no previous position and therefore no segment
     * to test. The positive control below flies the same tick with the spawn as its previous
     * position and does pass, so this asserts a declined segment and not an unreachable ring.
     */
    @Test
    void theFirstTickHasNoSegmentToTestAndPassesNothing() {
        RaceClock clock = RaceClock.startingAt(TICK).advanced();

        RaceRun afterTeleport = RaceRun.atStart().advance(START_LINE, clock, positionAt(1), true);

        assertThat(afterTeleport.progress().passedCount()).isZero();
        assertThat(afterTeleport.passedOnGameTick()).isEmpty();
        assertThat(afterTeleport.justPassed()).isNull();
        assertThat(afterTeleport.previous()).isEqualTo(positionAt(1));

        RaceRun alreadyOnCourse = new RaceRun(RingProgress.atStart(), SPAWN, List.of(), Optional.empty(), null)
                .advance(START_LINE, clock, positionAt(1), true);

        assertThat(alreadyOnCourse.passedOnGameTick()).containsExactly(1);
        assertThat(alreadyOnCourse.finishedAt()).contains(new RaceClock(1, TICK));
    }

    // ------------------------------------------------------------------------------------------
    // The time a run is scored on
    // ------------------------------------------------------------------------------------------

    @Test
    void aFinishersTimeOnCourseIsTheRaceTimeOfTheTickItFinishedOn() {
        RaceRun run = fly(RaceRun.atStart(), COURSE, LAST_RING_TICK, NEVER_STOPS_GLIDING);

        // 19 movement ticks at 50 ms, and not the 8 s the phase runs for.
        assertThat(run.timeOnCourse(RACE_LENGTH)).isEqualTo(Duration.ofMillis(950));
        assertThat(run.timeOnCourse(RACE_LENGTH)).isNotEqualTo(RACE_LENGTH);
    }

    /**
     * The race time comes from the clock's own step, not from a 50 ms this type assumed. At a 40 ms
     * step the same nineteen ticks are 760 ms, and the crossing ticks do not move — the lane is a
     * function of the tick number, not of wall-clock time.
     */
    @Test
    void aFinishersTimeOnCourseFollowsTheClocksStep() {
        Duration fasterStep = Duration.ofMillis(40);

        RaceRun run = fly(RaceRun.atStart(), COURSE, fasterStep, LAST_RING_TICK, NEVER_STOPS_GLIDING);

        assertThat(run.passedOnGameTick())
                .containsExactly(FIRST_RING_TICK, SECOND_RING_TICK, LAST_RING_TICK);
        assertThat(run.timeOnCourse(RACE_LENGTH)).isEqualTo(Duration.ofMillis(760));
    }

    /**
     * An unfinished run is scored against the whole phase, whatever it did on the way. Both halves
     * matter: the value is the phase length, and it is not the time of the last ring this run
     * happened to pass — nor of the last ring anyone else passed.
     */
    @Test
    void anUnfinishedRunIsScoredAgainstTheFullPhaseLength() {
        RaceRun unfinished = fly(RaceRun.atStart(), COURSE, LAST_RING_TICK + 5, 2);

        assertThat(unfinished.timeOnCourse(RACE_LENGTH)).isEqualTo(RACE_LENGTH);
        assertThat(unfinished.timeOnCourse(RACE_LENGTH))
                .isNotEqualTo(TICK.multipliedBy(SECOND_RING_TICK));

        // A different phase length gives a different answer, so the value is read and not a constant.
        Duration shorterPhase = Duration.ofMillis(6_500);
        assertThat(unfinished.timeOnCourse(shorterPhase)).isEqualTo(shorterPhase);

        // And a finisher ignores the phase length entirely.
        RaceRun finished = fly(RaceRun.atStart(), COURSE, LAST_RING_TICK, NEVER_STOPS_GLIDING);
        assertThat(finished.timeOnCourse(shorterPhase)).isEqualTo(Duration.ofMillis(950));
    }

    @Test
    void aRunThatHasNotStartedIsScoredAgainstTheFullPhaseLength() {
        assertThat(RaceRun.atStart().timeOnCourse(RACE_LENGTH)).isEqualTo(RACE_LENGTH);
    }

    // ------------------------------------------------------------------------------------------
    // Invariants
    // ------------------------------------------------------------------------------------------

    @Test
    void aRunRecordsExactlyOneTickPerRingPassed() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RaceRun(new RingProgress(2, -1), SPAWN, List.of(FIRST_RING_TICK),
                        Optional.empty(), null))
                .withMessageContaining("1 ticks for 2 rings");
    }

    @Test
    void ringTicksMustIncrease() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RaceRun(new RingProgress(2, -1),
                        SPAWN, List.of(SECOND_RING_TICK, FIRST_RING_TICK), Optional.empty(), null))
                .withMessageContaining("must increase");
    }

    /** The first movement tick of a phase is tick one — a pass on tick zero is a clock read too early. */
    @Test
    void aRingCannotBePassedBeforeTheFirstMovementTick() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RaceRun(new RingProgress(1, -1), SPAWN, List.of(0),
                        Optional.empty(), null))
                .withMessageContaining("must increase and start at 1");
    }

    @Test
    void aFinishClockMustNameTheTickTheLastRingWasPassedOn() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RaceRun(new RingProgress(1, -1), SPAWN, List.of(FIRST_RING_TICK),
                        Optional.of(new RaceClock(FIRST_RING_TICK + 1, TICK)), null))
                .withMessageContaining("not on %d".formatted(FIRST_RING_TICK));
    }

    @Test
    void theRecordedTicksCannotBeChangedThroughTheListHandedIn() {
        List<Integer> ticks = new ArrayList<>(List.of(FIRST_RING_TICK));
        RaceRun run = new RaceRun(new RingProgress(1, -1), SPAWN, ticks, Optional.empty(), null);

        ticks.add(SECOND_RING_TICK);

        assertThat(run.passedOnGameTick()).containsExactly(FIRST_RING_TICK);
    }

    // ------------------------------------------------------------------------------------------
    // Fixture
    // ------------------------------------------------------------------------------------------

    private static RaceRun fly(RaceRun run, MapDefinition map, int throughTick, int glidesUntilRingCount) {
        return fly(run, map, TICK, throughTick, glidesUntilRingCount);
    }

    private static RaceRun fly(RaceRun run, MapDefinition map, Duration step, int throughTick,
            int glidesUntilRingCount) {
        RaceClock clock = RaceClock.startingAt(step);
        RaceRun flown = run;
        for (int tick = 1; tick <= throughTick; tick++) {
            clock = clock.advanced();
            boolean gliding = flown.progress().passedCount() < glidesUntilRingCount;
            flown = flown.advance(map, clock, positionAt(tick), gliding);
        }
        return flown;
    }

    private static Vec3 positionAt(int gameTick) {
        return new Vec3(LANE_X, LANE_Y, SPAWN_Z + BLOCKS_PER_TICK * gameTick);
    }
}
