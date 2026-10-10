package net.elytrarace.voyager.platform.flight;

/**
 * What happened to one player's elytra flight on a single tick, as {@link FlightTracker} derives
 * it from the flag and, for an ending, the stop event.
 *
 * <p>There is no variant for "nothing happened" — a player who was not flying before the tick and
 * is not flying now is not a transition at all. {@link FlightTracker#tick(java.util.UUID, boolean)}
 * represents that as an absent {@code Optional}, not as a fourth member here; see its javadoc for
 * why treating that absence as a continuing flight is the mistake this split guards against.
 */
public sealed interface FlightTransition
        permits FlightTransition.Started, FlightTransition.Continuing, FlightTransition.Ended {

    /** A flight began this tick: the flag was {@code false} last tick and is {@code true} now. */
    record Started() implements FlightTransition {
    }

    /** A flight already in progress is still going: the flag was, and remains, {@code true}. */
    record Continuing() implements FlightTransition {
    }

    /** A flight in progress ended this tick: the flag was {@code true} last tick and is {@code false} now. */
    record Ended(FlightEndReason reason) implements FlightTransition {
    }

    /** Returns {@link Started}. */
    static FlightTransition started() {
        return new Started();
    }

    /** Returns {@link Continuing}. */
    static FlightTransition continuing() {
        return new Continuing();
    }

    /** Returns {@link Ended} carrying {@code reason}. */
    static FlightTransition ended(FlightEndReason reason) {
        return new Ended(reason);
    }
}
