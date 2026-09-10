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
                divergingStep = Optional.of(mostDivergentStep(trace, recorded));
            }
        }

        return new ReplayReport(
                firstDivergingTick,
                firstDivergingTickError,
                divergingStep,
                cumulativeError,
                cumulativeError > cumulativeThreshold);
    }

    /**
     * The fixture format carries one recorded velocity per tick, not a per-step breakdown — {@link
     * TickTrace}'s own javadoc notes that even the firework boost cannot be attributed to a single
     * step from {@code velocityAfter} alone. This walks the steps in Vanilla's order and finds the
     * one whose simulated output sits closest to the recorded velocity; the step immediately after
     * it is where the simulated chain stops tracking the recording, and is reported as the
     * divergence's source. A tick whose closest match is already the last step reports that last
     * step, since there is no later step left to blame instead.
     */
    private static ElytraStep mostDivergentStep(TickTrace trace, TraceFixture.Tick recorded) {
        Vec3 recordedVelocity = new Vec3(recorded.velX(), recorded.velY(), recorded.velZ());
        List<ElytraStep> steps = ElytraStep.stepsInOrder();

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

        int divergingIndex = Math.min(closestIndex + 1, steps.size() - 1);
        return steps.get(divergingIndex);
    }

    /**
     * The outcome of one replay.
     *
     * @param firstDivergingTick the index of the first tick whose position error exceeded the
     *     per-tick threshold, empty if none did
     * @param firstDivergingTickError the position error at {@code firstDivergingTick}, {@code 0.0}
     *     when it is empty
     * @param divergingStep the step whose simulated velocity best explains where the chain stopped
     *     matching the recording at {@code firstDivergingTick}, empty when it is empty
     * @param cumulativeError the sum of every tick's position error across the whole replay
     * @param cumulativeThresholdExceeded {@code true} when {@code cumulativeError} exceeded the
     *     cumulative threshold, independently of whether any single tick exceeded the per-tick one
     */
    public record ReplayReport(
            OptionalInt firstDivergingTick,
            double firstDivergingTickError,
            Optional<ElytraStep> divergingStep,
            double cumulativeError,
            boolean cumulativeThresholdExceeded) {

        /** {@code true} when neither bound was exceeded anywhere in the replay. */
        public boolean isClean() {
            return firstDivergingTick.isEmpty() && !cumulativeThresholdExceeded;
        }
    }
}
