package net.elytrarace.voyager.physics.trace;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.physics.CollisionSpace;
import net.elytrarace.voyager.api.physics.FlightInput;
import net.elytrarace.voyager.api.physics.FlightState;
import net.elytrarace.voyager.physics.ElytraSimulator;
import net.elytrarace.voyager.physics.TickTrace;
import net.elytrarace.voyager.physics.trace.exception.InvalidReplayThresholdException;
import net.elytrarace.voyager.physics.trace.exception.InvalidTraceFixtureException;

import java.util.ArrayList;
import java.util.List;
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
 *
 * <p><b>This does not, and cannot, name which {@link
 * net.elytrarace.voyager.physics.step.ElytraStep} caused a divergence.</b> An earlier revision
 * tried: it compared {@link ElytraSimulator#tickTraced}'s per-step {@code velocityAfter} snapshots
 * against the recorded velocity and reported whichever step's output sat closest, on the theory
 * that a bug confined to one step would leave an earlier, unaffected step's output closer to the
 * recording than the buggy final step's. Measured against 245 realistically-shaped cases, that
 * theory did not hold: {@code DRAG}, the last of the five steps, scales the horizontal components
 * by {@code 0.99} — so the distance between {@code DIRECTION_ALIGNMENT}'s output and {@code
 * DRAG}'s is only about {@code 0.01 · |velocity|}, the same order of magnitude as a typical
 * velocity divergence. Once a divergence reaches that size, an earlier step's output becomes the
 * closest match to the recording essentially at random, and which step got named tracked the
 * <em>sign and magnitude of the perturbation</em>, not its cause. A test built to confirm the fix
 * passed only because its one hand-picked perturbation vector happened to land on the right side of
 * that coin flip; the same magnitude with one sign flipped named a different step.
 *
 * <p>The root problem is the fixture format itself, not this method: E2a's recorded fixture (see
 * {@code docs/superpowers/plans/2026-09-10-e2a-trace-recorder.md}, Task 2, and {@link
 * TraceFixture.Tick}) carries exactly one velocity per tick — the entity's actual post-tick delta
 * movement, the value after every step (and collision restitution) has already run. No recording
 * ever carries a per-step breakdown to compare against, so nothing this replay does with one final
 * velocity can reliably identify which of the five steps that precede it is at fault. Reporting a
 * step anyway, even conditionally, means a diverging trace can point at an innocent step exactly as
 * often as it points at the guilty one — worse than reporting nothing, because a reader has no way
 * to tell the difference from here.
 *
 * <p>The per-step decomposition itself is not wasted: {@link ElytraSimulator#tickTraced} still
 * returns every step's velocity for a single tick a human is already looking at, for interactive
 * inspection against hand-computed or independently-reasoned expectations. What this class declines
 * to do is turn that decomposition into an automatic verdict from fixture data alone.
 */
public abstract class TraceReplay {

    /**
     * The per-tick position-error bound, in blocks: the distance between the simulated and the
     * recorded position on any single tick.
     *
     * <p><b>Zero, measured.</b> The E2b plan carried {@code 1.0E-6} as a stated assumption awaiting
     * the real traces; Task 7 measured it against all nine and it is six orders of magnitude too
     * loose. Every comparable tick of every fixture — free flight, a sustained turn at non-zero yaw,
     * ±90° pitch, a firework burn, a wall clamp, a touchdown — reproduces the recorded position
     * <em>bit-for-bit</em>, both as a one-step residual and across a free-running replay of the
     * whole trace. The per-profile figures are in {@code docs/reference/elytra-physics-26.2.md}.
     *
     * <p>A non-zero bound here would not buy safety, it would spend it: at {@code 1e-6} the port
     * could drop {@code LivingEntity.aiStep}'s deadzone and still pass most profiles, and the
     * {@code float} half-width that decides where the glider rests against a wall
     * ({@code 1.19e-08}) would be invisible. Both defects were real and both were found by this
     * bound being zero.
     */
    public static final double DEFAULT_PER_TICK_THRESHOLD = 0.0;

    /**
     * The default drift bound, in blocks: how far the simulated position sits from the recorded one
     * at the <em>end</em> of the replay. This is the spec's "cumulative drift over 200 ticks", and
     * it is a single final position error, not a sum — a replay whose trajectory wanders and comes
     * back is not drifting.
     *
     * <p>An earlier revision bounded the <em>sum</em> of every tick's error at {@code 1.0E-4}, which
     * was neither the spec's metric nor internally consistent with it: for a steadily drifting trace
     * the sum runs about two orders of magnitude above the final error, and a replay sitting exactly
     * at the {@code 1e-6} per-tick bound accumulates {@code 2e-4} over 200 ticks and was reported
     * dirty without ever exceeding the per-tick bound. The sum survives as {@link
     * ReplayReport#summedTickError()}, reported but not bounded.
     *
     * <p><b>Zero, measured.</b> Like the per-tick bound this was a stated assumption ({@code
     * 1.0E-2}) awaiting the real traces. Replayed free-running from tick 0, eight of the nine
     * fixtures end on the recorded position bit-for-bit after 199 or 219 ticks; the ninth,
     * {@code chained-boosts}, ends {@code 5.0e-01} out for a reason no threshold should absorb —
     * its fixture cannot say how many rockets were burning, so the replay applies one impulse where
     * Vanilla applied two, and the error is in the recording, not in the port. It is excluded by
     * name and by derivation rather than tolerated; see {@link RecordedGlide}.
     *
     * <p>The spec's earlier {@code 1.0E-2} would have passed a replay that drifted a centimetre
     * over ten seconds of flight — enough to move a ring pass into a ring miss — and is now
     * recorded in {@code docs/reference/elytra-physics-26.2.md} as the assumption it was.
     */
    public static final double DEFAULT_DRIFT_THRESHOLD = 0.0;

    private TraceReplay() {
    }

    /** Replays {@code fixture} against the default thresholds. */
    public static ReplayReport replay(TraceFixture fixture) {
        return replay(fixture, DEFAULT_PER_TICK_THRESHOLD, DEFAULT_DRIFT_THRESHOLD);
    }

    /**
     * Replays {@code fixture}, reporting the first tick whose position error exceeds {@code
     * perTickThreshold} and whether the position error at the last replayed tick exceeds {@code
     * driftThreshold}. Both are distances in blocks between the simulated and the recorded position:
     * the first is measured on every tick, the second only at the end.
     *
     * <p>The two bounds are independent. A replay can stay under the per-tick bound on every tick
     * and still finish outside the drift bound only when {@code driftThreshold < perTickThreshold};
     * with the defaults it is the other way round, and the drift bound is the coarser gate on how
     * far a trajectory that already diverged is allowed to end up. The summed per-tick error is
     * reported alongside as {@link ReplayReport#summedTickError()} and is not bounded by either.
     */
    public static ReplayReport replay(TraceFixture fixture, double perTickThreshold, double driftThreshold) {
        if (fixture == null) {
            throw new InvalidTraceFixtureException("fixture must not be null");
        }
        return replay(fixture, literalInputs(fixture), fixture.ticks().size() - 1,
                perTickThreshold, driftThreshold);
    }

    /**
     * Replays the comparable part of a real recording: {@link RecordedGlide} supplies both the
     * per-tick inputs — which are not a literal reading of {@code fireworkBoostActive}, see its
     * javadoc — and the tick at which the recording stops describing a glide at all.
     */
    public static ReplayReport replay(RecordedGlide glide, double perTickThreshold, double driftThreshold) {
        if (glide == null) {
            throw new InvalidTraceFixtureException("glide must not be null");
        }
        return replay(glide.fixture(), glide.inputs(), glide.lastComparableTick(),
                perTickThreshold, driftThreshold);
    }

    /**
     * A literal reading of the fixture's own fields, one input per tick. Correct for the synthetic
     * fixtures this harness was built against, where {@code fireworkBoostActive} means exactly
     * "a boost ran on this tick"; {@link RecordedGlide} exists because a real recording's flag is
     * one tick short at the end of a burn.
     */
    private static List<FlightInput> literalInputs(TraceFixture fixture) {
        double gravity = fixture.metadata().gravity();
        List<FlightInput> inputs = new ArrayList<>(fixture.ticks().size());
        inputs.add(null);
        for (int index = 1; index < fixture.ticks().size(); index++) {
            TraceFixture.Tick tick = fixture.ticks().get(index);
            inputs.add(new FlightInput(
                    tick.yaw(), tick.pitch(), tick.fireworkBoostActive(),
                    tick.fireworkTicksRemaining(), gravity));
        }
        return inputs;
    }

    private static ReplayReport replay(
            TraceFixture fixture,
            List<FlightInput> inputs,
            int lastTick,
            double perTickThreshold,
            double driftThreshold) {
        if (!Double.isFinite(perTickThreshold) || perTickThreshold < 0.0) {
            throw new InvalidReplayThresholdException(
                    "perTickThreshold must be finite and >= 0, was %s".formatted(perTickThreshold));
        }
        if (!Double.isFinite(driftThreshold) || driftThreshold < 0.0) {
            throw new InvalidReplayThresholdException(
                    "driftThreshold must be finite and >= 0, was %s".formatted(driftThreshold));
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

        OptionalInt firstDivergingTick = OptionalInt.empty();
        double firstDivergingTickError = 0.0;
        double firstDivergingTickVelocityError = 0.0;
        double summedTickError = 0.0;
        double finalDrift = 0.0;

        for (int i = 1; i <= lastTick; i++) {
            TraceFixture.Tick recorded = ticks.get(i);
            FlightInput input = inputs.get(i);

            TickTrace trace = ElytraSimulator.tickTraced(state, input, space);
            state = trace.result();

            Vec3 recordedPosition = new Vec3(recorded.posX(), recorded.posY(), recorded.posZ());
            double error = state.position().minus(recordedPosition).length();
            summedTickError += error;
            finalDrift = error;

            if (firstDivergingTick.isEmpty() && error > perTickThreshold) {
                firstDivergingTick = OptionalInt.of(i);
                firstDivergingTickError = error;
                firstDivergingTickVelocityError = velocityError(state, recorded);
            }
        }

        return new ReplayReport(
                firstDivergingTick,
                firstDivergingTickError,
                firstDivergingTickVelocityError,
                finalDrift,
                summedTickError,
                finalDrift > driftThreshold);
    }

    /**
     * The distance between the recorded velocity and {@code state.velocity()} — the simulated
     * velocity <em>after</em> collision restitution, which is what a recorder actually measures
     * ({@link TraceFixture.Tick}'s own javadoc: "the entity's real internal delta movement"). Using
     * {@code ElytraSimulator.tickTraced}'s pre-restitution {@code velocityAfter(DRAG)} here instead
     * would be wrong on any tick with a collision: restitution zeroes a collided axis outright (see
     * {@link ElytraSimulator}'s class javadoc), so a landing tick with zero physics divergence would
     * still show a large, entirely spurious "error" — exactly the gap between the pre- and
     * post-restitution velocity, not a sign of anything wrong.
     */
    private static double velocityError(FlightState state, TraceFixture.Tick recorded) {
        Vec3 recordedVelocity = new Vec3(recorded.velX(), recorded.velY(), recorded.velZ());
        return state.velocity().minus(recordedVelocity).length();
    }

    /**
     * The outcome of one replay.
     *
     * @param firstDivergingTick the index of the first tick whose position error exceeded the
     *     per-tick threshold, empty if none did
     * @param firstDivergingTickError the position error at {@code firstDivergingTick}, {@code 0.0}
     *     when it is empty
     * @param firstDivergingTickVelocityError the velocity error at {@code firstDivergingTick} —
     *     the distance between the recorded velocity and the simulated velocity after restitution,
     *     {@code 0.0} when {@code firstDivergingTick} is empty. Not attributable to any single
     *     {@link net.elytrarace.voyager.physics.step.ElytraStep}; see this class's javadoc for why.
     * @param finalDrift the position error at the last replayed tick — how far the simulation
     *     ended up from the recording, in blocks. {@code 0.0} for a fixture with no tick past the
     *     seed
     * @param summedTickError the sum of every tick's position error across the whole replay. A
     *     diagnostic, bounded by neither threshold: for a steadily drifting trace it runs about two
     *     orders of magnitude above {@code finalDrift}, and for a trajectory that wanders and comes
     *     back it can be large while {@code finalDrift} is near zero
     * @param driftThresholdExceeded {@code true} when {@code finalDrift} exceeded the drift
     *     threshold, independently of whether any single tick exceeded the per-tick one
     */
    public record ReplayReport(
            OptionalInt firstDivergingTick,
            double firstDivergingTickError,
            double firstDivergingTickVelocityError,
            double finalDrift,
            double summedTickError,
            boolean driftThresholdExceeded) {

        /** {@code true} when neither bound was exceeded anywhere in the replay. */
        public boolean isClean() {
            return firstDivergingTick.isEmpty() && !driftThresholdExceeded;
        }
    }
}
