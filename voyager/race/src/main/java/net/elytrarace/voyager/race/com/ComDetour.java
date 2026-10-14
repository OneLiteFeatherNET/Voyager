package net.elytrarace.voyager.race.com;

import org.jetbrains.annotations.ApiStatus;

/**
 * Where a computer racer is in a missed-ring detour (D13). {@link #NONE} is normal running along the line.
 *
 * <p>A detour runs out past the ring with its lateral offset outside the disc, turns back and returns to the
 * ring from the far side with no offset, so the ring is crossed through its disc and counted once.
 */
@ApiStatus.Internal
public enum ComDetour {
    /** Running along the line with the wander offset. */
    NONE,
    /** Going forward past the ring, offset outside its disc. */
    OUTBOUND,
    /** Going back along the same stretch, still offset outside the disc. */
    BACKTRACK,
    /** Going forward again from six blocks before the ring, offset zero, so the crossing is inside the disc. */
    REJOIN
}
