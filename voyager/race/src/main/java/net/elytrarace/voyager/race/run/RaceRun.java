package net.elytrarace.voyager.race.run;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.race.flow.RaceClock;
import net.elytrarace.voyager.race.progress.ProgressTracker;
import net.elytrarace.voyager.race.progress.ProgressUpdate;
import net.elytrarace.voyager.race.progress.RingProgress;
import net.elytrarace.voyager.race.scoring.MapScorer;

import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * <strong>One player's run over one map, as a value.</strong> Everything a driver has to carry from
 * one movement tick of a {@code GAME} phase to the next, and nothing else.
 *
 * <p>It exists because every driver needs exactly this and each one would otherwise write it again.
 * E3's {@code CupPlaythroughTest} had to invent it as a mutable inner class to play a cup at all,
 * and the composition root would have been the second copy — in the place a copy is hardest to get
 * back out of. The rest of this module is values and pure functions ({@link ProgressTracker},
 * {@link MapScorer}, {@code RaceStateMachine}, {@link RaceClock}); this is the piece that was
 * missing, so it is one too: {@link #advance} takes a tick's inputs and returns the next run.
 *
 * <h2>What one call takes</h2>
 *
 * <p>{@link #advance(MapDefinition, RaceClock, Vec3, boolean)} takes the map, <em>the race
 * clock</em>, the position this tick and whether the player is gliding. It takes the clock rather
 * than a bare tick number on purpose: {@link RaceClock} already counts the movement ticks actually
 * played and already knows what elapsed time they stand for, and it is advanced <em>before</em> the
 * tick it names. A run that took an {@code int} and multiplied it by a step of its own would be a
 * second source of truth for the same quantity, and the off-by-one between it and the clock would
 * be invisible — which is the exact failure {@link RaceClock} was introduced to close.
 *
 * <h2>How a finish is represented</h2>
 *
 * <p>{@link #finishedAt()} is the clock of the tick the last ring was passed on, or empty. There is
 * no {@code -1} sentinel: the test this type replaces used one, and it is why its scoring step had
 * to branch on a magic number before it could call {@link MapScorer#score}. Callers do not branch at
 * all now — {@link #timeOnCourse(Duration)} answers with the completion time for a finisher and with
 * the full phase length for a run that did not finish. <strong>That rule is domain, not driver:</strong>
 * "an unfinished run is scored against how long the phase lasted" is a statement about how this game
 * scores, and every driver deriving it separately is how two drivers come to disagree.
 *
 * <h2>What is deliberately not in it</h2>
 *
 * <p><strong>The velocity a racer carries is not here, and neither is a count of effects applied.</strong>
 * They were in the harness's version because its assertions read them, which is not a reason. On a
 * server the velocity a {@code BOOST} or {@code SLOW} ring multiplies is the flight state
 * {@code FlightTickDriver} simulates and {@code VelocityExit} writes back — a copy kept here would
 * be a second one, diverging from the first the moment the simulation moved, with nothing able to
 * tell which was right. What a driver actually needs in order to apply an effect is <em>which ring
 * was just passed</em>, and that is {@link #justPassed()}.
 *
 * <h2>What is deliberately in it</h2>
 *
 * <p>{@link #passedOnGameTick()} — the tick each ring was passed on — is kept. It looks like the one
 * unbounded field and is not: {@link ProgressTracker} tests only the next ring in order and stops
 * once every ring is passed, so the list grows to the map's ring count and then never again. It is
 * also the only record of <em>when</em> a ring was passed, which is what makes a playthrough's
 * crossing ticks falsifiable at all, and what a split time or a replay marker would be read from.
 * Dropping it would leave a run able to say that it finished and unable to say when anything
 * happened on the way.
 *
 * @param progress how far through the map's rings this run has got
 * @param previous the position the previous movement tick ended at, or {@code null} before the first
 *     one — the tick after a teleport has no segment to test, and {@link ProgressTracker} expects
 *     exactly that
 * @param passedOnGameTick the movement tick each ring was passed on, in ring order: entry {@code i}
 *     belongs to ring {@code i}, because progression is strictly in order
 * @param finishedAt the clock of the tick the final ring was passed on, or empty while the run has
 *     not finished
 * @param justPassed the ring passed on the tick that produced this run, or {@code null} if that tick
 *     passed none. It describes that one tick and not the run as a whole, which is why a driver must
 *     read it from the run {@link #advance} returned rather than from one it stored earlier.
 * @param gliding whether the player was gliding on the tick that produced this run. A landing is the
 *     transition from gliding to standing on the ground, so the next tick needs this, not the one
 *     before it; {@code false} for a run that has played no tick
 */
public record RaceRun(RingProgress progress, @Nullable Vec3 previous, List<Integer> passedOnGameTick,
        Optional<RaceClock> finishedAt, @Nullable Ring justPassed, boolean gliding) {

    /** The first movement tick of a {@code GAME} phase is tick one; see {@link RaceClock}. */
    private static final int FIRST_GAME_TICK = 1;

    /**
     * A run from a tick that did not record whether the player was gliding: the flag is {@code false},
     * which is what a run that has played no tick says.
     */
    public RaceRun(RingProgress progress, @Nullable Vec3 previous, List<Integer> passedOnGameTick,
            Optional<RaceClock> finishedAt, @Nullable Ring justPassed) {
        this(progress, previous, passedOnGameTick, finishedAt, justPassed, false);
    }

    public RaceRun {
        passedOnGameTick = List.copyOf(passedOnGameTick);
        if (passedOnGameTick.size() != progress.passedCount()) {
            throw new IllegalArgumentException(
                    "a run records one tick per ring passed, but has %d ticks for %d rings"
                            .formatted(passedOnGameTick.size(), progress.passedCount()));
        }
        int last = FIRST_GAME_TICK - 1;
        for (int tick : passedOnGameTick) {
            if (tick <= last) {
                throw new IllegalArgumentException(
                        "ring ticks must increase and start at %d, was %s"
                                .formatted(FIRST_GAME_TICK, passedOnGameTick));
            }
            last = tick;
        }
        if (finishedAt.isPresent() && finishedAt.get().gameTick() != last) {
            throw new IllegalArgumentException(
                    "a run finished on tick %d must have passed its last ring on that tick, not on %d"
                            .formatted(finishedAt.get().gameTick(), last));
        }
    }

    /** A run that has not yet played a movement tick: no progress, no position, nothing passed. */
    public static RaceRun atStart() {
        return new RaceRun(RingProgress.atStart(), null, List.of(), Optional.empty(), null, false);
    }

    /**
     * Returns this run after one movement tick: the player moved to {@code position} while
     * {@code gliding}, on the tick {@code clock} names.
     *
     * <p>Pure — nothing here is mutated, and the returned run is the only result.
     *
     * @param map the course being flown; its rings are demanded in list order
     * @param clock the race clock, already advanced, so {@link RaceClock#gameTick()} is the tick
     *     being played
     */
    public RaceRun advance(MapDefinition map, RaceClock clock, Vec3 position, boolean gliding) {
        ProgressUpdate update = ProgressTracker.advance(progress, map.rings(), previous, position, gliding);
        Ring passed = update.passed();
        if (passed == null) {
            return new RaceRun(progress, position, passedOnGameTick, finishedAt, null, gliding);
        }

        List<Integer> ticks = new ArrayList<>(passedOnGameTick);
        ticks.add(clock.gameTick());
        Optional<RaceClock> finished = update.progress().passedCount() == map.rings().size()
                ? Optional.of(clock)
                : finishedAt;
        return new RaceRun(update.progress(), position, ticks, finished, passed, gliding);
    }

    /** Whether every ring of the map has been passed. */
    public boolean finished() {
        return finishedAt.isPresent();
    }

    /**
     * The time to score this run on: how long the player was on the course.
     *
     * <p>For a finisher that is the race time of the tick they passed the final ring — taken from
     * the clock, never recomputed — and for a run that did not finish it is {@code raceLength}, the
     * full length of the {@code GAME} phase. That is the value {@link MapScorer#score} wants, and
     * asking for it here is what keeps the rule in one place.
     *
     * @param raceLength how long the {@code GAME} phase runs, from {@code RaceTimings.race()}
     */
    public Duration timeOnCourse(Duration raceLength) {
        return finishedAt.map(RaceClock::elapsed).orElse(raceLength);
    }
}
