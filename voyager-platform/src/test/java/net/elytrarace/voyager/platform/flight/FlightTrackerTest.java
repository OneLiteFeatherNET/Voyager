package net.elytrarace.voyager.platform.flight;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every case here is driven by a fake source for "is currently flying" — plain {@code boolean}
 * readings and event notifications handed to {@link FlightTracker} directly — never a live
 * Minestom player, per the brief for this task.
 */
class FlightTrackerTest {

    private final UUID playerId = UUID.randomUUID();
    private final FlightTracker tracker = new FlightTracker();

    @Test
    void reportsNothingWhileTheFlagStaysOff() {
        Optional<FlightTransition> transition = tracker.tick(playerId, false);

        assertThat(transition).isEmpty();
    }

    @Test
    void reportsStartedWhenTheFlagTurnsOn() {
        tracker.tick(playerId, false);

        Optional<FlightTransition> transition = tracker.tick(playerId, true);

        assertThat(transition).contains(FlightTransition.started());
    }

    @Test
    void reportsStartedOnTheVeryFirstTickIfTheFlagIsAlreadyOn() {
        // No prior tick exists for this player at all — the tracker must not mistake "never
        // observed" for "already flying" and silently swallow the start. This is exactly the
        // "treat a missing transition as a continuing flight" mutation, checked from the other
        // direction: an unknown player is not a continuing flight either.
        Optional<FlightTransition> transition = tracker.tick(playerId, true);

        assertThat(transition).contains(FlightTransition.started());
    }

    @Test
    void reportsContinuingOnEachSubsequentTickWhileStillFlying() {
        tracker.tick(playerId, true);

        Optional<FlightTransition> second = tracker.tick(playerId, true);
        Optional<FlightTransition> third = tracker.tick(playerId, true);

        assertThat(second).contains(FlightTransition.continuing());
        assertThat(third).contains(FlightTransition.continuing());
    }

    @Test
    void reportsEndedConfirmedByEventWhenTheStopEventFiredBeforeTheFlagCleared() {
        tracker.tick(playerId, true);
        tracker.onStopFlyingWithElytraEvent(playerId);

        Optional<FlightTransition> transition = tracker.tick(playerId, false);

        assertThat(transition).contains(FlightTransition.ended(FlightEndReason.CONFIRMED_BY_STOP_EVENT));
    }

    @Test
    void reportsEndedWithNoEventObservedWhenTheFlagClearsInTheAir() {
        // The case the spec did not plan for: no PlayerStopFlyingWithElytraEvent at all, because
        // 2026.08.28-26.2 fires it only when the player is on the ground, and this player never was.
        tracker.tick(playerId, true);

        Optional<FlightTransition> transition = tracker.tick(playerId, false);

        assertThat(transition).contains(FlightTransition.ended(FlightEndReason.NO_STOP_EVENT_OBSERVED));
    }

    @Test
    void aStopEventFollowedByTheFlagStillOnIsNotTwoFlights() {
        // The stop event arrived this tick — the player briefly touched the ground — but by the
        // time the tracker is polled the flag reads true again (a re-glide off the same touch).
        // The flag is the source of truth: this must read as one continuing flight, not an ended
        // flight immediately followed by a new one.
        tracker.tick(playerId, true);
        tracker.onStopFlyingWithElytraEvent(playerId);

        Optional<FlightTransition> transition = tracker.tick(playerId, true);

        assertThat(transition).contains(FlightTransition.continuing());
    }

    @Test
    void aFlightAfterAnEndedOneIsStartedAgainNotContinuing() {
        tracker.tick(playerId, true);
        tracker.tick(playerId, false);

        Optional<FlightTransition> transition = tracker.tick(playerId, true);

        assertThat(transition).contains(FlightTransition.started());
    }

    @Test
    void aStopEventWithNoPriorFlightHasNoEffect() {
        tracker.onStopFlyingWithElytraEvent(playerId);

        Optional<FlightTransition> transition = tracker.tick(playerId, false);

        assertThat(transition).isEmpty();
    }

    @Test
    void tracksEachPlayerIndependently() {
        UUID otherPlayerId = UUID.randomUUID();

        Optional<FlightTransition> firstPlayerTransition = tracker.tick(playerId, true);
        Optional<FlightTransition> secondPlayerTransition = tracker.tick(otherPlayerId, false);

        assertThat(firstPlayerTransition).contains(FlightTransition.started());
        assertThat(secondPlayerTransition).isEmpty();
    }

    @Test
    void aConfirmedEndingDoesNotLeakIntoTheOtherPlayersNextTick() {
        UUID otherPlayerId = UUID.randomUUID();
        tracker.tick(playerId, true);
        tracker.onStopFlyingWithElytraEvent(playerId);
        tracker.tick(playerId, false);

        tracker.tick(otherPlayerId, true);
        Optional<FlightTransition> transition = tracker.tick(otherPlayerId, false);

        assertThat(transition).contains(FlightTransition.ended(FlightEndReason.NO_STOP_EVENT_OBSERVED));
    }

    @Test
    void forgetClearsTrackedStateSoTheNextFlightStartsFresh() {
        tracker.tick(playerId, true);
        tracker.tick(playerId, true);

        tracker.forget(playerId);
        Optional<FlightTransition> transition = tracker.tick(playerId, true);

        assertThat(transition).contains(FlightTransition.started());
    }

    @Test
    void forgetDiscardsAPendingStopEventToo() {
        // A pending stop event is otherwise invisible through tick() alone: the very next tick()
        // call for this playerId consumes it regardless of whether forget() ran. Only the
        // package-private accessor can tell the two apart, and that is the point of this test — see
        // FlightTracker#forget's javadoc for why the pending event needs discarding independently
        // of the flying-state map (a disconnect racing the stop event, with no further tick to
        // consume it).
        tracker.onStopFlyingWithElytraEvent(playerId);
        assertThat(tracker.hasPendingStopEvent(playerId)).isTrue();

        tracker.forget(playerId);

        assertThat(tracker.hasPendingStopEvent(playerId)).isFalse();
    }
}
