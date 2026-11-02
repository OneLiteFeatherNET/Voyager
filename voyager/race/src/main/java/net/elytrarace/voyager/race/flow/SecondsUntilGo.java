package net.elytrarace.voyager.race.flow;

import java.time.Duration;

/**
 * The time left until the next map's GO, read from the race state and the timings alone.
 *
 * <p>A wait runs from the end of a map to the GO of the next one: the rest of that map's {@code END} phase, then the
 * whole lobby of the next map, or the rest of that lobby once the phase has begun. Nothing here reads a clock, so the
 * count a racer sees is the count the state machine will keep. The caller asks only during a wait, that is in an
 * {@code END} phase that another map follows or in a {@code LOBBY} phase after the first; a {@code GAME} phase has no
 * wait and is refused.
 */
public abstract class SecondsUntilGo {

    private static final long MILLIS_PER_SECOND = 1_000L;

    private SecondsUntilGo() {
    }

    /**
     * The time left until the GO of the next map.
     *
     * @param state the race state of the wait, an {@code END} phase with another map to come, or a {@code LOBBY} phase
     * @param timings the lengths the cup is played with
     * @return the time left, never negative
     * @throws IllegalArgumentException if the state is in a {@code GAME} phase, which has no wait
     */
    public static Duration remaining(RaceState state, RaceTimings timings) {
        return switch (state.phase()) {
            case LOBBY -> nonNegative(timings.lobby().minus(state.inPhase()));
            case END -> nonNegative(timings.endBetweenMaps().minus(state.inPhase())).plus(timings.lobby());
            case GAME -> throw new IllegalArgumentException(
                    "a game phase is not a wait, so no time to the next GO is left in it");
        };
    }

    /**
     * The fraction of the whole wait that has passed, counted in whole seconds so that a bar moves once a second.
     *
     * @param state the race state of the wait, as for {@link #remaining}
     * @param timings the lengths the cup is played with
     * @return a value from zero to one; one when the wait has no length
     * @throws IllegalArgumentException if the state is in a {@code GAME} phase
     */
    public static double fractionPassed(RaceState state, RaceTimings timings) {
        long wait = wholeSeconds(timings.endBetweenMaps().plus(timings.lobby()));
        if (wait == 0) {
            return 1.0;
        }
        long passed = switch (state.phase()) {
            case LOBBY -> wholeSeconds(timings.endBetweenMaps()) + wholeSeconds(state.inPhase());
            case END -> wholeSeconds(state.inPhase());
            case GAME -> throw new IllegalArgumentException(
                    "a game phase is not a wait, so no part of a wait has passed in it");
        };
        return (double) Math.min(passed, wait) / wait;
    }

    /**
     * A duration in whole seconds, rounded up, so that the count reads 1 until the GO and never shows a second already gone.
     *
     * @param remaining the time left
     * @return the seconds left, never negative
     */
    public static long wholeSecondsUp(Duration remaining) {
        long millis = Math.max(0L, remaining.toMillis());
        return (millis + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND;
    }

    private static Duration nonNegative(Duration duration) {
        return duration.isNegative() ? Duration.ZERO : duration;
    }

    private static long wholeSeconds(Duration duration) {
        return Math.max(0L, duration.toMillis()) / MILLIS_PER_SECOND;
    }
}
