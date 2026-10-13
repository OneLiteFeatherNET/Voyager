package net.elytrarace.voyager.race.com;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.GuideLine;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;
import net.elytrarace.voyager.api.race.com.ComDifficulty;
import net.elytrarace.voyager.race.line.RacingLine;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The movement brain for one computer racer, driven tick by tick.
 *
 * <p>Every course is built in the test from {@link MapDefinition} values: no file, no clock, no server. Each test
 * builds its own state and its own tuning, so no test can see another's result. The rings sit on a straight course
 * along the z axis, the first at 20 blocks, so a racer's lateral offset is the only thing that moves it off a ring.
 */
class ComPilotTest {

    private static final long SEED = 42L;
    private static final ComSeat SEAT = new ComSeat(0, SEED);
    private static final double RING_RADIUS = 3.0;
    private static final long TICK_LIMIT = 2_000L;

    /**
     * Three rings on the z axis at 20, 50 and 80 blocks, each facing {@code normal}, and the grid at the spawn on the
     * origin, as the field builds it. Only the middle ring takes {@code middleType}: a boost on the first ring would
     * land before the racer has reached its target speed.
     */
    private static ComPath course(Vec3 normal, RingType middleType) {
        List<Ring> rings = List.of(
                new Ring(0, new Vec3(0.0, 64.0, 20.0), normal, RING_RADIUS, 8, RingType.STANDARD),
                new Ring(1, new Vec3(0.0, 64.0, 50.0), normal, RING_RADIUS, 8, middleType),
                new Ring(2, new Vec3(0.0, 64.0, 80.0), normal, RING_RADIUS, 8, RingType.STANDARD));
        MapDefinition map = new MapDefinition("fixture", "fixture", new Vec3(0.0, 64.0, 0.0), rings,
                Duration.ofSeconds(30), new BoostConfig(12, 25), new GuideLine(List.of(), 2, 1.0));
        return ComPath.fromGrid(map.spawn(), RacingLine.of(map), map.rings());
    }

    /** The straight course with rings that face down the course, so a racer can pass them. */
    private static ComPath straightCourse(RingType middleType) {
        return course(new Vec3(0.0, 0.0, 1.0), middleType);
    }

    /** A course whose rings face straight up: a racer that stays at one height never crosses one. */
    private static ComPath flatCourse() {
        return course(new Vec3(0.0, 1.0, 0.0), RingType.STANDARD);
    }

    /** Steps the racer through the ticks from {@code fromTick} to {@code toTick}, both included. */
    private static ComState advance(ComState state, ComTuning tuning, ComPath path, long fromTick, long toTick) {
        ComState current = state;
        for (long tick = fromTick; tick <= toTick; tick++) {
            current = ComPilot.step(current, tuning, path, tick);
        }
        return current;
    }

    @Test
    void oneStepAdvancesTheDistanceByTheSpeed() {
        ComPath path = straightCourse(RingType.STANDARD);
        ComTuning medium = ComTuning.of(ComDifficulty.MEDIUM, SEED);
        ComState start = ComState.start(SEAT);

        ComState next = ComPilot.step(start, medium, path, 1L);

        assertThat(next.distance())
                .as("one step moves the racer forward by its speed of %s blocks", start.speed())
                .isCloseTo(start.distance() + start.speed(), within(1e-9));
    }

    @Test
    void theSameSeedAndTickGiveTheSamePosition() {
        ComPath path = straightCourse(RingType.STANDARD);
        ComTuning medium = ComTuning.of(ComDifficulty.MEDIUM, SEED);
        ComState first = advance(ComState.start(new ComSeat(0, SEED)), medium, path, 1L, 50L);
        ComState second = advance(ComState.start(new ComSeat(0, SEED)), medium, path, 1L, 50L);
        ComTuning otherTuning = ComTuning.of(ComDifficulty.MEDIUM, SEED + 1);
        ComState otherSeed = advance(ComState.start(new ComSeat(0, SEED + 1)), otherTuning, path, 1L, 50L);

        assertThat(ComPilot.positionOf(second, medium, path))
                .as("the same seed and tick give the same position on every run")
                .isEqualTo(ComPilot.positionOf(first, medium, path));
        assertThat(ComPilot.positionOf(otherSeed, otherTuning, path))
                .as("a different seed drives a different line")
                .isNotEqualTo(ComPilot.positionOf(first, medium, path));
    }

    @Test
    void theSpeedNeverExceedsTheCap() {
        ComPath path = straightCourse(RingType.STANDARD);
        ComTuning hard = ComTuning.of(ComDifficulty.HARD, SEED);
        ComState state = ComState.start(SEAT);
        double highest = 0.0;
        for (long tick = 1L; tick <= 500L; tick++) {
            state = ComPilot.step(state, hard, path, tick);
            highest = Math.max(highest, state.speed());
        }

        assertThat(highest)
                .as("the fastest speed of a HARD racer over 500 ticks, in blocks per tick")
                .isLessThanOrEqualTo(ComTuning.REFERENCE_SPEED);
        assertThat(highest)
                .as("the racer does accelerate, so the cap is the thing holding it back")
                .isGreaterThan(ComTuning.REFERENCE_SPEED - 0.1);
    }

    @Test
    void theSpeedChangesByNoMoreThanTheAccelerationBoundInOneTick() {
        ComPath path = straightCourse(RingType.STANDARD);
        ComTuning medium = ComTuning.of(ComDifficulty.MEDIUM, SEED);
        ComState start = ComState.start(SEAT);
        ComState afterOne = ComPilot.step(start, medium, path, 1L);

        assertThat(afterOne.speed() - start.speed())
                .as("from the floor the racer speeds up by the full bound, not by the whole gap to its target")
                .isCloseTo(ComPilot.ACCELERATION_BOUND, within(1e-9));

        ComState state = afterOne;
        double largestChange = 0.0;
        for (long tick = 2L; tick <= 200L; tick++) {
            ComState next = ComPilot.step(state, medium, path, tick);
            largestChange = Math.max(largestChange, Math.abs(next.speed() - state.speed()));
            state = next;
        }

        assertThat(largestChange)
                .as("the largest speed change in one tick, in blocks per tick per tick")
                .isLessThanOrEqualTo(ComPilot.ACCELERATION_BOUND + 1e-12);
    }

    @Test
    void aMissedRingIsReCrossedThroughItsDiscAndCountedOnce() {
        ComPath path = straightCourse(RingType.STANDARD);
        ComTuning missesEveryRing = new ComTuning(0.93, 1.0, 0.6, 60.0, 1.0, 4);
        ComState state = ComState.start(SEAT);
        double farthestBeforePass = Double.NEGATIVE_INFINITY;
        long tick = 1L;
        while (state.progress().passedCount() == 0 && tick <= TICK_LIMIT) {
            state = ComPilot.step(state, missesEveryRing, path, tick);
            if (state.progress().passedCount() == 0) {
                farthestBeforePass = Math.max(farthestBeforePass, state.distance());
            }
            tick++;
        }
        Vec3 atPass = ComPilot.positionOf(state, missesEveryRing, path);

        assertThat(state.progress().passedCount())
                .as("ring 0 is passed once the racer re-crosses it")
                .isEqualTo(1);
        assertThat(farthestBeforePass)
                .as("the racer flew past ring 0 before it came back, so it really missed the ring")
                .isGreaterThanOrEqualTo(3.0);
        assertThat(atPass.distanceTo(new Vec3(0.0, 64.0, 20.0)))
                .as("the pass is recorded from inside the ring's disc")
                .isLessThanOrEqualTo(RING_RADIUS);

        ComState later = advance(state, missesEveryRing, path, tick, tick + 20L);
        assertThat(later.progress().passedCount())
                .as("the same ring is not counted a second time")
                .isEqualTo(1);
    }

    @Test
    void hardNeverMissesARing() {
        ComPath path = straightCourse(RingType.STANDARD);
        ComTuning hard = ComTuning.of(ComDifficulty.HARD, SEED);
        ComState state = ComState.start(SEAT);
        boolean detoured = false;
        long tick = 1L;
        while (state.status() == ComStatus.RACING && tick <= TICK_LIMIT) {
            state = ComPilot.step(state, hard, path, tick);
            detoured |= state.detour() != ComDetour.NONE;
            tick++;
        }

        assertThat(detoured)
                .as("a HARD racer never starts a detour for a missed ring")
                .isFalse();
        assertThat(state.status())
                .as("a HARD racer finishes the course without a detour")
                .isEqualTo(ComStatus.FINISHED);
    }

    @Test
    void aBoostTakesEffectAfterTheReactionDelayWithAFakeRingEffect() {
        ComPath path = straightCourse(RingType.BOOST);
        // The boost ring is the middle one, so the racer has reached its target speed before the boost lands.
        ComTuning medium = ComTuning.of(ComDifficulty.MEDIUM, SEED);
        ComSpeedEffect fakeBoost = target -> target + 0.5;
        ComState plain = ComState.start(SEAT);
        ComState boosted = ComState.start(SEAT);
        long passedAt = -1L;
        long divergedAt = -1L;
        for (long tick = 1L; tick <= TICK_LIMIT && divergedAt < 0L; tick++) {
            ComState nextPlain = ComPilot.step(plain, medium, path, tick);
            ComState nextBoosted = ComPilot.step(boosted, medium, path, tick, fakeBoost);
            if (passedAt < 0L && nextBoosted.progress().passedCount() == 2) {
                passedAt = tick;
            }
            if (nextBoosted.speed() != nextPlain.speed()) {
                divergedAt = tick;
            }
            plain = nextPlain;
            boosted = nextBoosted;
        }

        assertThat(passedAt)
                .as("the boost ring, the second ring, is passed")
                .isPositive();
        assertThat(divergedAt)
                .as("the boost changes the speed on the tick its reaction delay ends, after a pass on tick %s",
                        passedAt)
                .isEqualTo(passedAt + medium.boostReactionDelayTicks());
    }

    @Test
    void aRacerWithNoProgressFor400TicksIsMarkedDidNotFinish() {
        ComPath path = flatCourse();
        ComTuning hard = ComTuning.of(ComDifficulty.HARD, SEED);
        ComState state = advance(ComState.start(SEAT), hard, path, 1L, 399L);

        assertThat(state.status())
                .as("399 ticks without progress is not yet did-not-finish")
                .isEqualTo(ComStatus.RACING);

        state = ComPilot.step(state, hard, path, 400L);

        assertThat(state.status())
                .as("400 ticks without progress marks the racer did-not-finish")
                .isEqualTo(ComStatus.DID_NOT_FINISH);
    }

    @Test
    void aWholeLapOfAFixtureCourseIsRunWithNoServerAndNoClock() {
        ComPath path = straightCourse(RingType.STANDARD);
        ComTuning medium = ComTuning.of(ComDifficulty.MEDIUM, SEED);
        ComState state = ComState.start(SEAT);
        long tick = 1L;
        while (state.status() == ComStatus.RACING && tick <= TICK_LIMIT) {
            state = ComPilot.step(state, medium, path, tick);
            tick++;
        }

        assertThat(state.status())
                .as("the racer finishes the three-ring course within %s ticks", TICK_LIMIT)
                .isEqualTo(ComStatus.FINISHED);
        assertThat(state.progress().passedCount())
                .as("every ring of the course is passed")
                .isEqualTo(3);
    }
}
