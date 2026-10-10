package net.elytrarace.voyager.race.reset;

/** Why a racer is being sent back: they left the vertical bounds of their world, or they landed. */
public enum ResetCause {
    /** Below the floor or above the ceiling of the dimension of the racer's world. */
    OUT_OF_BOUNDS,
    /** Gliding on the previous tick, on the ground and no longer gliding on this one. */
    LANDED
}
