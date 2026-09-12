package net.elytrarace.voyager.platform.tick;

import net.elytrarace.voyager.api.physics.CollisionSpace;
import net.elytrarace.voyager.api.physics.FlightState;
import net.elytrarace.voyager.physics.ElytraSimulator;
import net.elytrarace.voyager.platform.flight.FlightTracker;
import net.elytrarace.voyager.platform.flight.FlightTransition;
import net.elytrarace.voyager.platform.tick.exception.UntrackedFlightException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Advances every flying player's server-side flight by one tick.
 *
 * <h2>The order within a tick, which is load-bearing</h2>
 *
 * <p><strong>Rotation is written, a world tick passes, and only then is the state sampled.</strong>
 * {@link #tick()} takes no arguments and calls {@link FlightSampler#sampleAtTickBoundary()} itself,
 * as the very first thing it does, so the input it applies is the one the player carries
 * <em>into</em> the tick and the transition it drives is the one <em>out of</em> that tick.
 *
 * <p>Getting this wrong is not a visible failure. The E2a trace recorder had it wrong twice, and
 * both times nothing downstream noticed:
 *
 * <ol>
 *   <li>The first input was never applied, because the first call came synchronously out of a
 *       command dispatch with no world tick in between. A 200-tick script produced 199 physics
 *       transitions, and the rotation recorded at index {@code k} had driven the transition
 *       <em>into</em> {@code k}.</li>
 *   <li>The guard written against that compared two samples for equality, and was inert on six of
 *       nine profiles — the rotation was written <em>before</em> the wait and read back into the
 *       same sample.</li>
 * </ol>
 *
 * <p>A driver that samples before advancing is right; one that samples after records an input that
 * never took effect, and every value it produces stays self-consistent while being shifted by one
 * tick. {@link FlightTick} carries {@code before}, {@code input} and {@code after} together so a
 * consumer can re-run the simulator and check the pairing rather than trusting it.
 *
 * <h2>Who holds the velocity</h2>
 *
 * <p>A flight is seeded once, from the observation on the tick it starts, and simulated from there.
 * Later observations of position and velocity are ignored: normal elytra flight is
 * client-authoritative, matching Vanilla, and the server tracks its own copy silently alongside for
 * ring collision and boost math. Nothing here calls {@code Player#setVelocity} — that is
 * {@code VelocityExit}'s and only {@code VelocityExit}'s, for the three external forces that
 * legitimately override the client, and {@code ApiPurityTest} now enforces it.
 *
 * <p>Not thread-safe, like the {@link FlightTracker} it wraps: every method must be called from the
 * single thread driving the tick loop.
 */
public final class FlightTickDriver {

    private final FlightSampler sampler;
    private final FlightTracker tracker;
    private final CollisionSpace space;
    private final Map<UUID, FlightState> stateByPlayer = new HashMap<>();

    /**
     * @param sampler observes the tracked players; called once at the top of every {@link #tick()}
     * @param tracker derives started/continuing/ended from the sampled flag
     * @param space the world movement resolves against — {@code MinestomCollisionSpace} in
     *     production, a recorded or empty space in a test. Typed as the port rather than the
     *     implementation so the loop can be driven without a server at all.
     */
    public FlightTickDriver(FlightSampler sampler, FlightTracker tracker, CollisionSpace space) {
        this.sampler = sampler;
        this.tracker = tracker;
        this.space = space;
    }

    /**
     * Samples every tracked player and advances each one whose flight started, continued or ended
     * this tick.
     *
     * @return one entry per player who had a transition, in the sampler's order; a player who was
     *     not flying before this tick and is not flying now produces no entry
     * @throws UntrackedFlightException if a flight is reported as already in progress for a player
     *     this driver holds no simulated state for
     */
    public List<FlightTick> tick() {
        List<FlightSample> samples = sampler.sampleAtTickBoundary();
        List<FlightTick> ticks = new ArrayList<>(samples.size());
        for (FlightSample sample : samples) {
            Optional<FlightTransition> transition = tracker.tick(sample.playerId(), sample.flyingWithElytra());
            transition.map(value -> advance(sample, value)).ifPresent(ticks::add);
        }
        return List.copyOf(ticks);
    }

    /** Drops everything tracked for {@code playerId} — call this when a player disconnects. */
    public void forget(UUID playerId) {
        tracker.forget(playerId);
        stateByPlayer.remove(playerId);
    }

    /**
     * Whether a simulated flight state is currently held for {@code playerId}. Package-private: it
     * exists because dropping that state is otherwise unobservable. Both places that drop it — a
     * flight ending, and {@link #forget(UUID)} — are followed by a {@code Started} transition on the
     * player's next flight, which re-seeds over whatever was left behind, so {@link #tick()} alone
     * cannot tell a cleared map from a stale one. Not part of the loop's contract.
     */
    boolean hasSimulatedState(UUID playerId) {
        return stateByPlayer.containsKey(playerId);
    }

    private FlightTick advance(FlightSample sample, FlightTransition transition) {
        return switch (transition) {
            case FlightTransition.Started ignored -> simulate(sample, transition, seed(sample));
            case FlightTransition.Continuing ignored -> simulate(sample, transition, tracked(sample.playerId()));
            case FlightTransition.Ended ignored -> {
                FlightState last = stateByPlayer.remove(sample.playerId());
                if (last == null) {
                    throw new UntrackedFlightException(sample.playerId());
                }
                yield new FlightTick(sample.playerId(), transition, sample.input(), last, last);
            }
        };
    }

    private FlightTick simulate(FlightSample sample, FlightTransition transition, FlightState before) {
        FlightState after = ElytraSimulator.tick(before, sample.input(), space);
        stateByPlayer.put(sample.playerId(), after);
        return new FlightTick(sample.playerId(), transition, sample.input(), before, after);
    }

    /**
     * The one point a client-reported position enters the simulation: the tick a flight starts.
     * Rotation comes from the tick's own input rather than from a separately observed value, so the
     * state a flight starts from and the input applied to it cannot disagree.
     */
    private static FlightState seed(FlightSample sample) {
        return new FlightState(sample.position(), sample.velocity(),
                sample.input().yaw(), sample.input().pitch(), sample.onGround());
    }

    private FlightState tracked(UUID playerId) {
        FlightState state = stateByPlayer.get(playerId);
        if (state == null) {
            throw new UntrackedFlightException(playerId);
        }
        return state;
    }
}
