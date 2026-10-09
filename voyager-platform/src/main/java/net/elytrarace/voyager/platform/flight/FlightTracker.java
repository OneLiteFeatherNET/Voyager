package net.elytrarace.voyager.platform.flight;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks every player's elytra flight, tick by tick, and reports when one starts, continues, or
 * ends — including the ending Minestom {@code 2026.08.28-26.2} gives no signal for at all.
 *
 * <p><b>Why this polls the flag instead of trusting the event.</b>
 * {@code PlayerStopFlyingWithElytraEvent} fires from exactly one place in this Minestom version:
 * {@code Player.refreshOnGround} (net/minestom/server/entity/Player.java, lines 2294-2297), and
 * only under the condition {@code if (this.onGround && this.isFlyingWithElytra())}.
 * {@code setFlyingWithElytra(false)} is called nowhere else in the source tree — there is no event
 * for a flight that ends in the air, whether because the player closed the elytra or because
 * something else cleared the flag without the player ever touching the ground. So
 * {@link #tick(UUID, boolean)} reads {@code EntityMeta.isFlyingWithElytra()}
 * (net/minestom/server/entity/metadata/EntityMeta.java, line 91; exposed at
 * net/minestom/server/entity/LivingEntity.java, line 652) every tick and derives the transition
 * from the flag itself. The stop event, recorded through {@link #onStopFlyingWithElytraEvent(UUID)},
 * only decides which {@link FlightEndReason} an ending the flag already revealed gets reported
 * with — it is a confirmation, never the source of truth. Reaching for the event first, the way the
 * design this class replaces did, is exactly the mistake that leaves an in-air ending undetected.
 *
 * <p>A stop event that arrives in a tick where the flag still reads {@code true} by the time this
 * tracker is polled — the player touched the ground and immediately glided again before the next
 * {@link #tick(UUID, boolean)} — is not treated as an ending followed by a new flight. The flag
 * wins: {@link #tick(UUID, boolean)} reports a continuing flight, and the pending event is
 * discarded either way.
 *
 * <p>Not thread-safe. {@link #tick(UUID, boolean)}, {@link #onStopFlyingWithElytraEvent(UUID)} and
 * {@link #forget(UUID)} must all be called from the single thread driving the tick loop.
 */
public final class FlightTracker {

    private final Map<UUID, Boolean> flyingByPlayer = new HashMap<>();
    private final Set<UUID> stopEventObserved = new HashSet<>();

    /**
     * Records that {@code PlayerStopFlyingWithElytraEvent} fired for {@code playerId} since the
     * last {@link #tick(UUID, boolean)} call for that player. Consumed by the next {@code tick}
     * call whether or not it ends up mattering — see the class javadoc for why a flag that is
     * {@code true} again by then wins over it.
     */
    public void onStopFlyingWithElytraEvent(UUID playerId) {
        stopEventObserved.add(playerId);
    }

    /**
     * Advances {@code playerId}'s flight state by one tick.
     *
     * @param isFlyingWithElytra this tick's {@code EntityMeta.isFlyingWithElytra()} reading for
     *        {@code playerId}
     * @return the transition that happened this tick, or empty when none did — {@code playerId} was
     *         not flying before this tick and is not flying now
     */
    public Optional<FlightTransition> tick(UUID playerId, boolean isFlyingWithElytra) {
        boolean wasFlying = flyingByPlayer.getOrDefault(playerId, false);
        boolean stopConfirmed = stopEventObserved.remove(playerId);
        flyingByPlayer.put(playerId, isFlyingWithElytra);

        if (isFlyingWithElytra) {
            return Optional.of(wasFlying ? FlightTransition.continuing() : FlightTransition.started());
        }
        if (wasFlying) {
            FlightEndReason reason = stopConfirmed
                    ? FlightEndReason.CONFIRMED_BY_STOP_EVENT
                    : FlightEndReason.NO_STOP_EVENT_OBSERVED;
            return Optional.of(FlightTransition.ended(reason));
        }
        return Optional.empty();
    }

    /**
     * Discards every piece of state tracked for {@code playerId} — call this when a player
     * disconnects. Without it, this tracker holds an entry for every player who has ever flown, for
     * as long as the server runs, plus — the leak {@link #tick(UUID, boolean)} alone cannot close —
     * an entry in the pending-stop-event set for a player whose disconnect packet raced the stop
     * event, leaving one recorded with no further {@code tick} call to consume it. The next
     * {@link #tick(UUID, boolean)} for this {@code playerId} after {@code forget} is
     * indistinguishable from the first one ever seen.
     */
    public void forget(UUID playerId) {
        flyingByPlayer.remove(playerId);
        stopEventObserved.remove(playerId);
    }

    /**
     * Whether a stop event is currently recorded for {@code playerId}, awaiting the next
     * {@link #tick(UUID, boolean)} to consume it. Package-private: it exists because
     * {@link #forget(UUID)}'s clearing of that pending event is otherwise unobservable — any
     * subsequent {@code tick} call for the same {@code playerId} already consumes it regardless of
     * whether {@code forget} ran, so only inspecting this set directly can tell the two apart. Not
     * part of the tick driver's contract.
     */
    boolean hasPendingStopEvent(UUID playerId) {
        return stopEventObserved.contains(playerId);
    }
}
