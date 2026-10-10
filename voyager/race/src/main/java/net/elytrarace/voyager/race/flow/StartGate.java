package net.elytrarace.voyager.race.flow;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;

import java.time.Duration;

/**
 * When a cup may start, and what happens to a start that is already counting down when racers leave.
 *
 * <h2>Why a pure table and not a state machine</h2>
 *
 * <p>The waiting lobby is a rule that sits above {@link RaceStateMachine}, not inside it. The state machine owns
 * the cup's own phases and their lengths; this decides only <em>whether to call</em> the cup's start and whether
 * to cancel, commit or abort it. The platform reports the {@link Situation} it is in and the number of racers
 * online, and this answers one {@link Verdict}. Nothing here reads a clock or a server, so the rule is one table
 * with one test per row.
 *
 * <h2>The commit window</h2>
 *
 * <p>{@link #COMMIT_WINDOW} is the length of the three-digit start countdown. Once the countdown has begun its last
 * three seconds, the start is committed: a racer leaving no longer cancels it, and only an empty server aborts it.
 * Before that point a drop below the minimum cancels the countdown and the room returns to waiting.
 */
@ApiStatus.Internal
public abstract class StartGate {

    /** The last three seconds of the first map's lobby, in which the start can no longer be cancelled. */
    public static final Duration COMMIT_WINDOW = Duration.ofSeconds(3);

    private StartGate() {
    }

    /**
     * Where the cup stands for the purpose of starting it.
     */
    public enum Situation {
        /** No cup is running, or the last cup has finished. */
        WAITING,
        /** The first map's lobby is running and more than {@link #COMMIT_WINDOW} of it remains. */
        COUNTDOWN,
        /** The first map's lobby is running with at most {@link #COMMIT_WINDOW} remaining. */
        COMMITTED,
        /** A cup is running past its first lobby, or a map has been entered in this cup. */
        RUNNING
    }

    /**
     * What the platform does about the situation.
     */
    public enum Verdict {
        /** Nothing changes. */
        HOLD,
        /** Start the cup, which begins its first lobby. */
        START_CUP,
        /** Cancel the countdown and return the room to waiting. */
        CANCEL_COUNTDOWN,
        /** Stop the cup and return the room to waiting, without a result. */
        ABORT_CUP
    }

    /**
     * The verdict for one tick.
     *
     * @param situation where the cup stands
     * @param online    how many racers are online
     * @param minimum   how many racers a cup needs to start; at least one
     * @return what to do now
     * @throws IllegalArgumentException if {@code minimum} is below one or {@code online} is negative
     */
    @Contract(pure = true)
    public static Verdict decide(Situation situation, int online, int minimum) {
        if (minimum < 1) {
            throw new IllegalArgumentException("the minimum racer count must be at least 1, was %s".formatted(minimum));
        }
        if (online < 0) {
            throw new IllegalArgumentException("the online racer count must not be negative, was %s".formatted(online));
        }
        boolean enough = online >= minimum;
        return switch (situation) {
            case WAITING -> enough ? Verdict.START_CUP : Verdict.HOLD;
            case COUNTDOWN -> enough ? Verdict.HOLD : Verdict.CANCEL_COUNTDOWN;
            case COMMITTED, RUNNING -> online == 0 ? Verdict.ABORT_CUP : Verdict.HOLD;
        };
    }
}
