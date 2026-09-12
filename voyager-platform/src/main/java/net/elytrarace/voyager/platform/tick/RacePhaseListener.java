package net.elytrarace.voyager.platform.tick;

import net.elytrarace.voyager.race.flow.RaceClock;

import java.time.Duration;

/**
 * What {@link XerusPhaseDriver} reports as it drives a cup: a map starting, a movement tick to play,
 * a map ending.
 *
 * <p>A movement tick is handed the {@link RaceClock}, never {@code RaceState.inPhase()}, because the
 * clock is the race clock and {@code inPhase} is not — see {@link RaceClock} for the one-step gap
 * between them and why timing a finish from the wrong one is invisible.
 *
 * <p>Every method is abstract rather than a default no-op. A listener that silently ignores the
 * movement tick is a race in which nobody flies, and that should not be reachable by forgetting to
 * override something.
 */
public interface RacePhaseListener {

    /**
     * One tick of a {@code LOBBY} phase, with the map that lobby leads into and how much of it is
     * left after this tick.
     *
     * <p>This exists so a race can have a start. The last three seconds of a lobby are the start
     * countdown, and a countdown needs to know when it is three seconds from a launch — which is a
     * question about the lobby, not about the race, and the only place that can answer it is the
     * thing holding the phase durations.
     *
     * <p>{@code remaining} is never negative: the tick that would take it past zero is the tick that
     * enters {@code GAME}, and that tick reports {@link #mapStarted} instead. A lobby of zero length
     * reports nothing at all, which is what makes a skipped lobby degrade to a launch rather than
     * delay one.
     */
    void lobbyTick(int mapIndex, String mapName, Duration remaining);

    /**
     * A map's {@code GAME} phase has begun. Fired before that phase's first
     * {@link #raceTick(RaceClock)}, with the clock already reset.
     */
    void mapStarted(int mapIndex, String mapName);

    /**
     * One movement tick of the current {@code GAME} phase is to be played. The clock has already
     * been advanced, so {@code clock.gameTick()} names the tick being played, starting at one.
     */
    void raceTick(RaceClock clock);

    /**
     * A map's {@code GAME} phase has ended, whether on its own limit or early because every player
     * finished. {@code clock} is the final race clock of that phase — the number of movement ticks
     * actually played, and the elapsed race time they stand for.
     */
    void mapFinished(int mapIndex, String mapName, RaceClock clock);
}
