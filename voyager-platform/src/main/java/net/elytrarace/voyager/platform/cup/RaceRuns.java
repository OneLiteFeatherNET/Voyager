package net.elytrarace.voyager.platform.cup;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.platform.cup.exception.UnstartedRunException;
import net.elytrarace.voyager.race.flow.RaceClock;
import net.elytrarace.voyager.race.run.RaceRun;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Every racer's {@link RaceRun} over the map currently being played: one instance, keyed by
 * {@link UUID}, held for as long as that one map lasts.
 *
 * <h2>Why the holder is here and not in {@code voyager-race}</h2>
 *
 * <p>{@link RaceRun} is a value advanced by a pure call, and Task 6 deliberately left it without a
 * holder. It cannot have one on its own side: {@code raceDoesNotDependOnPlatform} forbids
 * {@code voyager-race} from reaching back into this module, so the thing that keys runs by a
 * Minestom player's {@code UUID} and is emptied when the cup advances has to be platform-side. It
 * sits next to {@link MapTransition} rather than next to the tick drivers because the transition is
 * what fills and empties it, and because that lets {@link #startFresh(UUID)} and {@link #clearAll()}
 * stay package-private — see below.
 *
 * <h2>The lifecycle, which is the whole point</h2>
 *
 * <p><strong>A run belongs to one player on one map.</strong> There are exactly three ways an entry
 * appears or disappears, and every one of them is here:
 *
 * <ul>
 *   <li>{@link #startFresh(UUID)} — a player arrived at a map's spawn. Package-private, and
 *       deliberately: {@link MapTransition} is the only caller, so "holds a run" and "is standing on
 *       the map that run is over" cannot come apart. A public starter would let a driver create a
 *       run for a player who is still on the previous map, and nothing would say so until the
 *       results.</li>
 *   <li>{@link #clearAll()} — the cup advanced. Every run is dropped, not carried: the next map has
 *       different rings, and a run that survived the move would be scored against them.</li>
 *   <li>{@link #forget(UUID)} — the player disconnected. Public, because the disconnect handler is
 *       outside this package. Without it this map holds an entry for a player who is gone, and
 *       {@link #everyRacerFinished()} then waits for a finish that cannot arrive — the same leak
 *       {@code FlightTracker.forget} exists to close, with a visible symptom instead of a silent
 *       one.</li>
 * </ul>
 *
 * <p>Not thread-safe, like {@code FlightTracker} and {@code FlightTickDriver}: every method must be
 * called from the single thread driving the tick loop. {@link MapTransition} blocks rather than
 * completing asynchronously for exactly this reason.
 */
public final class RaceRuns {

    private final Map<UUID, RaceRun> byPlayer = new HashMap<>();

    /**
     * The run {@code playerId} is flying, or empty when they hold none — they were never moved to
     * this map, they disconnected, or the cup has advanced and the next transition has not reached
     * them yet.
     */
    public Optional<RaceRun> of(UUID playerId) {
        return Optional.ofNullable(byPlayer.get(playerId));
    }

    /**
     * Advances {@code playerId}'s run by one movement tick and stores the result.
     *
     * <p>The stored run is replaced rather than mutated — {@link RaceRun#advance} is pure — so this
     * is the one place a run is written, and a caller cannot hold a stale one and put it back.
     *
     * @param map the course being flown; the same map every run in this object is over
     * @param clock the race clock, already advanced, so it names the tick being played
     * @return the run after the tick
     * @throws UnstartedRunException if {@code playerId} holds no run on this map
     */
    public RaceRun advance(UUID playerId, MapDefinition map, RaceClock clock, Vec3 position, boolean gliding) {
        RaceRun run = byPlayer.get(playerId);
        if (run == null) {
            throw new UnstartedRunException(playerId);
        }
        RaceRun advanced = run.advance(map, clock, position, gliding);
        byPlayer.put(playerId, advanced);
        return advanced;
    }

    /**
     * Whether every racer holding a run has passed the map's last ring — what ends a {@code GAME}
     * phase before its time limit.
     *
     * <p><strong>Empty is {@code false}, not {@code true}.</strong> A universal quantifier over
     * nothing is vacuously true, and taking that answer would end the {@code GAME} phase on its
     * first tick whenever the transition has not filled this object yet, or whenever every racer has
     * disconnected — reporting a map as won by everybody that nobody flew.
     */
    public boolean everyRacerFinished() {
        if (byPlayer.isEmpty()) {
            return false;
        }
        return byPlayer.values().stream().allMatch(RaceRun::finished);
    }

    /**
     * Drops the run held for {@code playerId} — call this when a player disconnects. A player who
     * holds none is not an error; a disconnect can arrive between two maps.
     */
    public void forget(UUID playerId) {
        byPlayer.remove(playerId);
    }

    /**
     * Gives {@code playerId} a run at the start of the current map, replacing whatever they held.
     *
     * <p>Package-private on purpose: see the class javadoc. {@link MapTransition} calls this once it
     * has the player standing at the map's spawn, and nothing else may.
     */
    void startFresh(UUID playerId) {
        byPlayer.put(playerId, RaceRun.atStart());
    }

    /**
     * Drops every run, because the map they were over is no longer being played.
     *
     * <p>Package-private on purpose: see the class javadoc. {@link MapTransition} calls this before
     * it moves anybody, so no run can be advanced while its player is in transit.
     */
    void clearAll() {
        byPlayer.clear();
    }
}
