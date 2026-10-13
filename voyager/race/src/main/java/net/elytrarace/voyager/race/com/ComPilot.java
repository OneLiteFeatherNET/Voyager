package net.elytrarace.voyager.race.com;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.RingType;
import net.elytrarace.voyager.race.progress.ProgressTracker;
import net.elytrarace.voyager.race.progress.ProgressUpdate;

import org.jetbrains.annotations.ApiStatus;

import java.util.Random;

/**
 * The movement brain of a computer racer (D1, D13, D14): an arc-length follower on the racing line.
 *
 * <p>Each tick the racer advances its distance by its speed, reads the point and tangent of the line there, and
 * adds a lateral offset in the horizontal plane. The offset is {@code A sin(2 pi s / lambda + phi) + m}, with the
 * phase and the bias drawn from the seat's seed. The speed moves toward the difficulty's target under the
 * {@link #ACCELERATION_BOUND}, and is kept between the floor and the cap.
 *
 * <p>A ring is passed by the same test a human uses: {@link ProgressTracker} tests only the next ring, in order, and
 * {@link net.elytrarace.voyager.race.collision.RingPass} decides the crossing. A missed ring is a detour that goes out
 * past the ring, turns back and returns through the disc with no offset, so it costs time and never a ring.
 *
 * <p>There is no server, no clock and no unseeded randomness here: {@link #step} is a pure function of its inputs.
 * It takes the tick as a number, so a test drives it with explicit ticks.
 */
@ApiStatus.Internal
public final class ComPilot {

    /** The most the speed may change in one tick, in blocks per tick. */
    public static final double ACCELERATION_BOUND = 0.05;

    /** Ticks in a row without a passed ring after which a racer is did-not-finish (D13). */
    public static final int WATCHDOG_TICKS = 400;

    /** Blocks before a ring at which a detour begins; the racer later steps back to this point. */
    static final double DETOUR_LEAD = 6.0;

    /** Blocks past a ring that the outbound leg runs before the racer turns back. */
    static final double DETOUR_OVERSHOOT = 3.0;

    /** Blocks over which the offset ramps out to the detour side, and back in on the way to the ring. */
    static final double DETOUR_RAMP = 3.0;

    /** Blocks past a ring over which the racer rejoins its own wander. */
    static final double DETOUR_REJOIN = 6.0;

    /** Blocks by which the detour's outward offset clears a ring's radius. */
    static final double DETOUR_CLEARANCE = 1.5;

    private static final double TAU = 2.0 * Math.PI;
    private static final double MINIMUM_SIDE = 1e-9;
    private static final long MIX = 0x9E3779B97F4A7C15L;
    private static final long PHASE_STREAM = 1L;
    private static final long BIAS_STREAM = 2L;

    private ComPilot() {
    }

    /**
     * Advances one racer by one tick with no boost effect.
     *
     * @param state the racer before the tick
     * @param tuning the racer's tuning
     * @param path the course it races on
     * @param tick the game tick, counted from the start of the race
     * @return the racer after the tick
     */
    public static ComState step(ComState state, ComTuning tuning, ComPath path, long tick) {
        return step(state, tuning, path, tick, ComSpeedEffect.NONE);
    }

    /**
     * Advances one racer by one tick. A boost ring passed on tick {@code t} applies {@code boost} to the target speed
     * on tick {@code t + boostReactionDelayTicks}, once. How long a boost lasts is not modelled here yet: it belongs
     * to the change that gives each map its burn duration.
     *
     * @param state the racer before the tick
     * @param tuning the racer's tuning
     * @param path the course it races on
     * @param tick the game tick, counted from the start of the race
     * @param boost the effect of a boost on the target speed
     * @return the racer after the tick; the same state when the racer has already finished or was removed
     */
    public static ComState step(ComState state, ComTuning tuning, ComPath path, long tick, ComSpeedEffect boost) {
        if (state.status() != ComStatus.RACING) {
            return state;
        }
        Vec3 previous = positionOf(state, tuning, path);
        ComState moved = moveAlongCourse(state, tuning, path);
        Vec3 current = positionOf(moved, tuning, path);
        ProgressUpdate update = ProgressTracker.advance(state.progress(), path.rings(), previous, current, true);

        long boostDue = state.boostDueTick();
        if (update.passed() != null && update.passed().type() == RingType.BOOST) {
            boostDue = tick + tuning.boostReactionDelayTicks();
        }
        double target = clampSpeed(tuning.targetSpeed());
        if (boostDue != ComState.NO_BOOST && tick >= boostDue) {
            target = clampSpeed(boost.apply(target));
            boostDue = ComState.NO_BOOST;
        }
        double speed = moveToward(state.speed(), target);

        int passed = update.progress().passedCount();
        int stalled = passed == state.progress().passedCount() ? state.stalledTicks() + 1 : 0;
        ComStatus status = ComStatus.RACING;
        if (passed >= path.ringCount()) {
            status = ComStatus.FINISHED;
        } else if (stalled >= WATCHDOG_TICKS) {
            status = ComStatus.DID_NOT_FINISH;
        }
        return new ComState(state.seat(), moved.distance(), speed, update.progress(), moved.detour(),
                moved.detourRing(), boostDue, stalled, status);
    }

    /**
     * Where a racer is in the world: its point on the line, pushed sideways by its offset.
     *
     * <p>Before the first ring the line is extended straight back along its first tangent, so a racer on the grid
     * stands behind the first ring rather than on it.
     *
     * @param state the racer
     * @param tuning the racer's tuning
     * @param path the course it races on
     * @return the position in blocks
     */
    public static Vec3 positionOf(ComState state, ComTuning tuning, ComPath path) {
        double distance = state.distance();
        Vec3 onLine = distance >= 0.0
                ? path.pointAt(distance)
                : path.pointAt(0.0).plus(path.tangentAt(0.0).scale(distance));
        Vec3 side = sideways(path.tangentAt(Math.max(0.0, distance)));
        return onLine.plus(side.scale(offset(state, tuning, path)));
    }

    /** Moves the racer along the course for this tick and enters or leaves a detour. */
    private static ComState moveAlongCourse(ComState state, ComTuning tuning, ComPath path) {
        double speed = state.speed();
        double distance = state.distance();
        ComDetour detour = state.detour();
        int detourRing = state.detourRing();
        switch (detour) {
            case NONE -> {
                distance += speed;
                int next = state.progress().passedCount();
                if (next < path.ringCount()
                        && distance >= path.ringDistance(next) - DETOUR_LEAD
                        && distance < path.ringDistance(next)
                        && draw(state.seat().seed(), missStream(next)) < tuning.missChance()) {
                    detour = ComDetour.OUTBOUND;
                    detourRing = next;
                }
            }
            case OUTBOUND -> {
                distance += speed;
                if (distance >= path.ringDistance(detourRing) + DETOUR_OVERSHOOT) {
                    detour = ComDetour.BACKTRACK;
                }
            }
            case BACKTRACK -> {
                distance -= speed;
                if (distance <= path.ringDistance(detourRing) - DETOUR_LEAD) {
                    detour = ComDetour.REJOIN;
                }
            }
            case REJOIN -> {
                distance += speed;
                if (distance >= path.ringDistance(detourRing) + DETOUR_REJOIN) {
                    detour = ComDetour.NONE;
                    detourRing = -1;
                }
            }
        }
        return new ComState(state.seat(), distance, speed, state.progress(), detour, detourRing,
                state.boostDueTick(), state.stalledTicks(), state.status());
    }

    /** The lateral offset of the racer at its current distance and detour phase, in blocks. */
    private static double offset(ComState state, ComTuning tuning, ComPath path) {
        long seed = state.seat().seed();
        double distance = state.distance();
        return switch (state.detour()) {
            case NONE -> wander(seed, tuning, distance);
            case OUTBOUND -> {
                double start = path.ringDistance(state.detourRing()) - DETOUR_LEAD;
                yield lerp(wander(seed, tuning, start), outward(state, path), unit(distance - start, DETOUR_RAMP));
            }
            case BACKTRACK -> {
                double start = path.ringDistance(state.detourRing()) - DETOUR_LEAD;
                yield outward(state, path) * unit(distance - start, DETOUR_RAMP);
            }
            case REJOIN -> {
                double ring = path.ringDistance(state.detourRing());
                yield distance < ring ? 0.0 : wander(seed, tuning, distance) * unit(distance - ring, DETOUR_REJOIN);
            }
        };
    }

    /** The offset of the detour's outward leg: the side the seed picks, clear of the ring's disc. */
    private static double outward(ComState state, ComPath path) {
        int ring = state.detourRing();
        double sign = draw(state.seat().seed(), sideStream(ring)) < 0.5 ? 1.0 : -1.0;
        return sign * (path.ring(ring).radius() + DETOUR_CLEARANCE);
    }

    /** The wander {@code A sin(2 pi s / lambda + phi) + m} at a distance along the course. */
    private static double wander(long seed, ComTuning tuning, double distance) {
        double amplitude = tuning.lineOffsetAmplitude();
        double phase = TAU * draw(seed, PHASE_STREAM);
        double bias = (2.0 * draw(seed, BIAS_STREAM) - 1.0) * amplitude / 2.0;
        return amplitude * Math.sin(TAU * distance / tuning.wobblePeriod() + phase) + bias;
    }

    /** The horizontal unit vector perpendicular to the tangent, so an offset never changes the height. */
    private static Vec3 sideways(Vec3 tangent) {
        Vec3 side = new Vec3(-tangent.z(), 0.0, tangent.x());
        double length = side.length();
        return length < MINIMUM_SIDE ? new Vec3(1.0, 0.0, 0.0) : side.scale(1.0 / length);
    }

    /** The target speed kept between the floor and the cap. */
    private static double clampSpeed(double speed) {
        return Math.max(ComTuning.FLOOR_SPEED, Math.min(ComTuning.REFERENCE_SPEED, speed));
    }

    /** The speed one tick later: toward the target, by at most the acceleration bound. */
    private static double moveToward(double speed, double target) {
        double change = Math.max(-ACCELERATION_BOUND, Math.min(ACCELERATION_BOUND, target - speed));
        return clampSpeed(speed + change);
    }

    /** A value in [0, 1]: how far {@code value} has got along a stretch of {@code length}. */
    private static double unit(double value, double length) {
        return Math.max(0.0, Math.min(1.0, value / length));
    }

    private static double lerp(double from, double to, double t) {
        return from + (to - from) * t;
    }

    /** One uniform draw in [0, 1) from a stream of the seed; the same seed and stream always give the same draw. */
    private static double draw(long seed, long stream) {
        return new Random(seed + stream * MIX).nextDouble();
    }

    /** The stream of the miss roll for a ring. */
    private static long missStream(int ring) {
        return 1_000L + 2L * ring;
    }

    /** The stream of the detour side for a ring. */
    private static long sideStream(int ring) {
        return 1_001L + 2L * ring;
    }
}
