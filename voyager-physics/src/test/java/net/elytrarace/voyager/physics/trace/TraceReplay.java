package net.elytrarace.voyager.physics.trace;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.physics.CollisionSpace;
import net.elytrarace.voyager.api.physics.FlightInput;
import net.elytrarace.voyager.api.physics.FlightState;
import net.elytrarace.voyager.physics.ElytraSimulator;
import net.elytrarace.voyager.physics.TickTrace;
import net.elytrarace.voyager.physics.step.ElytraStep;
import net.elytrarace.voyager.physics.trace.exception.InvalidTraceFixtureException;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Replays a recorded {@link TraceFixture} against {@link ElytraSimulator} and reports where the
 * physics port's output first departs from what was recorded.
 *
 * <p>Tick 0 seeds a {@link FlightState} directly from the recording — position and the recorded
 * internal velocity, not a derived or assumed value. Every subsequent tick builds a {@link
 * FlightInput} from that tick's recorded rotation, boost state and the fixture's own {@code
 * gravity} (never a hardcoded constant — see {@link TraceFixture.Metadata}), calls {@link
 * ElytraSimulator#tickTraced}, and compares the simulated position against the recorded one. The
 * replay runs free: a tick's simulated state is carried into the next tick's input as-is, never
 * reset to the recorded value, so a real divergence compounds instead of being silently corrected
 * away every tick.
 */
public abstract class TraceReplay {

    /**
     * The default per-tick position-error bound, in blocks. Chosen far below the smallest
     * meaningful physics difference so a clean replay of a bit-identical simulation reports zero
     * divergence while any genuine formula mismatch is still caught on its first tick.
     */
    public static final double DEFAULT_PER_TICK_THRESHOLD = 1.0E-6;

    /** The default cumulative position-error bound, in blocks, summed across the whole replay. */
    public static final double DEFAULT_CUMULATIVE_THRESHOLD = 1.0E-4;

    /**
     * How much closer an earlier step's output must be to the recorded velocity than the last
     * step's own output before that earlier step is treated as a genuine candidate rather than
     * floating-point noise around a tie. See {@link #attributeDivergingStep}.
     */
    private static final double STEP_ATTRIBUTION_TOLERANCE = 1.0E-9;

    private TraceReplay() {
    }

    /** Replays {@code fixture} against the default thresholds. */
    public static ReplayReport replay(TraceFixture fixture) {
        return replay(fixture, DEFAULT_PER_TICK_THRESHOLD, DEFAULT_CUMULATIVE_THRESHOLD);
    }

    /**
     * Replays {@code fixture}, reporting the first tick whose position error exceeds {@code
     * perTickThreshold} and whether the summed position error across the whole replay exceeds
     * {@code cumulativeThreshold}. The two bounds are independent: a replay can stay under the
     * per-tick bound on every single tick while its accumulated error still exceeds the cumulative
     * one.
     */
    public static ReplayReport replay(TraceFixture fixture, double perTickThreshold, double cumulativeThreshold) {
        if (fixture == null) {
            throw new InvalidTraceFixtureException("fixture must not be null");
        }
        if (!Double.isFinite(perTickThreshold) || perTickThreshold < 0.0) {
            throw new InvalidTraceFixtureException(
                    "perTickThreshold must be finite and >= 0, was %s".formatted(perTickThreshold));
        }
        if (!Double.isFinite(cumulativeThreshold) || cumulativeThreshold < 0.0) {
            throw new InvalidTraceFixtureException(
                    "cumulativeThreshold must be finite and >= 0, was %s".formatted(cumulativeThreshold));
        }

        List<TraceFixture.Tick> ticks = fixture.ticks();
        TraceFixture.Tick seed = ticks.get(0);
        FlightState state = new FlightState(
                new Vec3(seed.posX(), seed.posY(), seed.posZ()),
                new Vec3(seed.velX(), seed.velY(), seed.velZ()),
                seed.yaw(),
                seed.pitch(),
                seed.onGround());

        CollisionSpace space = new RecordedCollisionSpace(fixture.metadata().worldSlice());
        double gravity = fixture.metadata().gravity();

        OptionalInt firstDivergingTick = OptionalInt.empty();
        double firstDivergingTickError = 0.0;
        double firstDivergingTickVelocityResidual = 0.0;
        Optional<ElytraStep> divergingStep = Optional.empty();
        double cumulativeError = 0.0;

        for (int i = 1; i < ticks.size(); i++) {
            TraceFixture.Tick recorded = ticks.get(i);
            FlightInput input = new FlightInput(
                    recorded.yaw(),
                    recorded.pitch(),
                    recorded.fireworkBoostActive(),
                    recorded.fireworkTicksRemaining(),
                    gravity);

            TickTrace trace = ElytraSimulator.tickTraced(state, input, space);
            state = trace.result();

            Vec3 recordedPosition = new Vec3(recorded.posX(), recorded.posY(), recorded.posZ());
            double error = state.position().minus(recordedPosition).length();
            cumulativeError += error;

            if (firstDivergingTick.isEmpty() && error > perTickThreshold) {
                firstDivergingTick = OptionalInt.of(i);
                firstDivergingTickError = error;
                firstDivergingTickVelocityResidual = velocityResidual(trace, recorded);
                divergingStep = attributeDivergingStep(trace, recorded);
            }
        }

        return new ReplayReport(
                firstDivergingTick,
                firstDivergingTickError,
                firstDivergingTickVelocityResidual,
                divergingStep,
                cumulativeError,
                cumulativeError > cumulativeThreshold);
    }

    /**
     * The distance between the recorded velocity and the simulated velocity after the last step
     * ({@link ElytraStep#DRAG}) — the tick's overall velocity error, before any attempt at
     * attributing it to a particular step. Exposed on {@link ReplayReport} so a reader can judge for
     * themselves how much weight {@link ReplayReport#divergingStep()} deserves: a large residual
     * with no attributed step still says something went wrong, even though this format cannot say
     * where.
     */
    private static double velocityResidual(TickTrace trace, TraceFixture.Tick recorded) {
        List<ElytraStep> steps = ElytraStep.stepsInOrder();
        Vec3 lastStepVelocity = trace.velocityAfter().get(steps.get(steps.size() - 1));
        Vec3 recordedVelocity = new Vec3(recorded.velX(), recorded.velY(), recorded.velZ());
        return lastStepVelocity.minus(recordedVelocity).length();
    }

    /**
     * Attempts to attribute a tick's divergence to one {@link ElytraStep}, and is honest about how
     * rarely that attempt can succeed.
     *
     * <p>The fixture format carries exactly one recorded velocity per tick — the entity's actual
     * post-tick delta movement, i.e. the value that a correct simulation produces after {@code DRAG}
     * runs, the last of the five steps (see E2a's {@code TraceTick} in {@code
     * docs/superpowers/plans/2026-09-10-e2a-trace-recorder.md}, Task 2, and {@link
     * TraceFixture.Tick}'s own javadoc). That shape makes step-level attribution meaningful only in
     * a narrow case: when the recorded velocity sits closer to some <em>earlier</em> step's raw
     * output than it does to {@code DRAG}'s own output. That can only happen when the divergence is
     * large relative to the distances between consecutive steps' outputs — for example, a bug
     * confined to {@code DRAG} itself, where the four earlier steps still compute the correct
     * trajectory and one of them, unaffected by the bug, ends up closer to the recording than
     * {@code DRAG}'s wrong answer does.
     *
     * <p>In the ordinary case — a real recording, and a port that is correct or nearly so — the
     * recorded velocity sits closest to {@code DRAG}'s own output, exactly because that is what it
     * is measuring. Comparing distances then, correctly, finds {@code DRAG} closest to itself, and
     * there is no earlier step to blame instead: the residual, whatever its size, is not
     * attributable to any single step from this data alone. This method reports {@link
     * Optional#empty()} in that case rather than naming {@code DRAG} by default, because doing the
     * latter would silently turn "the format cannot say" into "the last step is always at fault," an
     * answer indistinguishable from a real defect. See {@link #velocityResidual} for the number a
     * caller can still read when attribution comes back empty.
     */
    private static Optional<ElytraStep> attributeDivergingStep(TickTrace trace, TraceFixture.Tick recorded) {
        Vec3 recordedVelocity = new Vec3(recorded.velX(), recorded.velY(), recorded.velZ());
        List<ElytraStep> steps = ElytraStep.stepsInOrder();
        int lastIndex = steps.size() - 1;

        int closestIndex = 0;
        double closestDistance = Double.POSITIVE_INFINITY;
        for (int i = 0; i < steps.size(); i++) {
            Vec3 velocity = trace.velocityAfter().get(steps.get(i));
            double distance = velocity.minus(recordedVelocity).length();
            if (distance < closestDistance) {
                closestDistance = distance;
                closestIndex = i;
            }
        }

        double lastStepDistance = trace.velocityAfter().get(steps.get(lastIndex)).minus(recordedVelocity).length();
        if (lastStepDistance - closestDistance <= STEP_ATTRIBUTION_TOLERANCE) {
            // The recorded velocity is within tolerance of DRAG's own output — the ordinary case.
            // No step is meaningfully closer, so there is nothing to attribute.
            return Optional.empty();
        }

        return Optional.of(steps.get(closestIndex + 1));
    }

    /**
     * The outcome of one replay.
     *
     * @param firstDivergingTick the index of the first tick whose position error exceeded the
     *     per-tick threshold, empty if none did
     * @param firstDivergingTickError the position error at {@code firstDivergingTick}, {@code 0.0}
     *     when it is empty
     * @param firstDivergingTickVelocityResidual the distance between the recorded velocity and the
     *     simulated velocity after the last step at {@code firstDivergingTick}, {@code 0.0} when it
     *     is empty. Meaningful on its own even when {@code divergingStep} is empty — see {@link
     *     #attributeDivergingStep}.
     * @param divergingStep the step whose output was meaningfully closer to the recorded velocity
     *     than the last step's own output, at {@code firstDivergingTick} — empty whenever no step
     *     meets that bar, which is the ordinary case for a real recording (see {@link
     *     #attributeDivergingStep}), and always empty when {@code firstDivergingTick} is empty
     * @param cumulativeError the sum of every tick's position error across the whole replay
     * @param cumulativeThresholdExceeded {@code true} when {@code cumulativeError} exceeded the
     *     cumulative threshold, independently of whether any single tick exceeded the per-tick one
     */
    public record ReplayReport(
            OptionalInt firstDivergingTick,
            double firstDivergingTickError,
            double firstDivergingTickVelocityResidual,
            Optional<ElytraStep> divergingStep,
            double cumulativeError,
            boolean cumulativeThresholdExceeded) {

        /** {@code true} when neither bound was exceeded anywhere in the replay. */
        public boolean isClean() {
            return firstDivergingTick.isEmpty() && !cumulativeThresholdExceeded;
        }
    }
}
