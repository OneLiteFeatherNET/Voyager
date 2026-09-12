package net.elytrarace.voyager.race.flow;

import java.time.Duration;

/**
 * <strong>The race clock.</strong> It counts the movement ticks a driver has actually played in the
 * current {@link RacePhase#GAME} phase, and {@link #elapsed()} is the race time that count stands
 * for. Every race time a run records — a ring pass, a finish, a split — is taken from here.
 *
 * <h2>Why this type exists at all</h2>
 *
 * <p>{@link RaceState#inPhase()} looks like a race clock and is not one. It is the clock a tick
 * <em>starts from</em>: entering {@code GAME} it reads {@code 0} (or the lobby's overshoot), the
 * {@code N}-th movement tick of the phase is played against {@code (N-1) * step}, and the last one
 * of an 8 s phase reads 7.950 s at a 50 ms step and never 8.000 s. A driver that times a finish from
 * it therefore records <em>every</em> finish one step early, consistently, with nothing downstream
 * able to tell — which is precisely the gap E3 left open and E4 had to close.
 *
 * <p>So the two clocks stand in a fixed relation, and it is worth stating in the direction it
 * exists: on every movement tick of a {@code GAME} phase,
 *
 * <pre>
 *   clock.elapsed() == state.inPhase() + step
 * </pre>
 *
 * <p>{@code RaceClockTest} asserts exactly that, on every tick of a whole phase, and
 * {@code XerusPhaseDriverTest} asserts it again through the driver that produces both values. A
 * change that quietly made {@code inPhase()} the race clock turns both red.
 *
 * <h2>The order a driver has to keep</h2>
 *
 * <p>{@link #advanced()} is called <strong>before</strong> the movement tick it names is played, so
 * the tick being played is the {@code gameTick}-th and the first tick of a phase is tick one, not
 * tick zero. A driver that counted afterwards would name every tick one less than it is and
 * reintroduce the same off-by-one from the other side.
 *
 * <p>{@link #startingAt(Duration)} is called on entry to each {@code GAME} phase: the clock measures
 * the current map's race, not the cup.
 *
 * @param gameTick how many movement ticks of the current {@code GAME} phase have been played
 * @param step the wall-clock duration one movement tick stands for; 50 ms at Vanilla's 20 TPS
 */
public record RaceClock(int gameTick, Duration step) {

    public RaceClock {
        if (gameTick < 0) {
            throw new IllegalArgumentException("gameTick must not be negative, was %s".formatted(gameTick));
        }
        if (step.isNegative() || step.isZero()) {
            throw new IllegalArgumentException("step must be positive, was %s".formatted(step));
        }
    }

    /** A clock that has played nothing yet, ticking at {@code step}. */
    public static RaceClock startingAt(Duration step) {
        return new RaceClock(0, step);
    }

    /** The clock after one more movement tick — called before that tick is played, never after. */
    public RaceClock advanced() {
        return new RaceClock(gameTick + 1, step);
    }

    /** The race time this many played movement ticks stand for: {@code step * gameTick}. */
    public Duration elapsed() {
        return step.multipliedBy(gameTick);
    }
}
