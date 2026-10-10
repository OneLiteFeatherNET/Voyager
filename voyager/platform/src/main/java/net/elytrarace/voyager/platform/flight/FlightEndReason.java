package net.elytrarace.voyager.platform.flight;

/**
 * Why {@link FlightTracker} reported a flight as ended.
 *
 * <p>Both members describe what {@link FlightTracker} itself can observe — whether
 * {@code PlayerStopFlyingWithElytraEvent} arrived before the tick that saw the flag clear — not a
 * claim about what the player physically did. See {@link FlightTracker}'s class javadoc for why
 * that event cannot be trusted as the sole signal, and for the one condition under which it fires
 * at all.
 */
public enum FlightEndReason {

    /**
     * {@code PlayerStopFlyingWithElytraEvent} was observed for this player before the flag cleared.
     * In Minestom {@code 2026.08.28-26.2} that event fires from exactly one place —
     * {@code Player.refreshOnGround}, under {@code if (this.onGround && this.isFlyingWithElytra())}
     * — so this reason means the ending coincided with the player touching the ground.
     */
    CONFIRMED_BY_STOP_EVENT,

    /**
     * The flag cleared with no confirming event observed for this player. This is the ending
     * {@code 2026.08.28-26.2} gives no signal for at all: the player closed the elytra in the air,
     * or something else cleared the flag, and {@code setFlyingWithElytra(false)} was reached from
     * neither of the two call sites this tracker knows how to hear about.
     */
    NO_STOP_EVENT_OBSERVED
}
