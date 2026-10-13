package net.elytrarace.voyager.race.com;

import net.elytrarace.voyager.race.progress.RingProgress;

import org.jetbrains.annotations.ApiStatus;

/**
 * One computer racer's state at the end of a game tick: where it is along the course, how fast it is going, how
 * many rings it has passed, and the bookkeeping that the detour and the watchdog need.
 *
 * <p>The state is a value. {@link ComPilot#step} returns the next one and never changes this one.
 *
 * @param seat the seat this racer holds; its seed drives the wander, the miss rolls and the detour side
 * @param distance blocks along the racing line from the first ring's centre; negative while the racer is still on
 *     the grid behind it
 * @param speed the speed of the current tick, in blocks per tick, between the floor and the cap
 * @param progress how far through the rings the racer is, in the same strict order a human's is
 * @param detour where the racer is in a missed-ring detour, {@link ComDetour#NONE} when it is running normally
 * @param detourRing the ring the current detour is for, or {@code -1} when there is none
 * @param boostDueTick the tick at which a boost passed earlier takes effect, or {@link #NO_BOOST}
 * @param stalledTicks the consecutive ticks without a passed ring, counted for the watchdog
 * @param status whether the racer is still racing, has finished, or was removed as did-not-finish
 */
@ApiStatus.Internal
public record ComState(ComSeat seat, double distance, double speed, RingProgress progress, ComDetour detour,
        int detourRing, long boostDueTick, int stalledTicks, ComStatus status) {

    /** The value of {@link #boostDueTick} when no boost is waiting to take effect. */
    static final long NO_BOOST = Long.MIN_VALUE;

    /** Where a racer starts on the grid: eight blocks behind the first ring's centre, so it passes the ring. */
    static final double START_DISTANCE = -8.0;

    public ComState {
        if (stalledTicks < 0) {
            throw new IllegalArgumentException(
                    "stalled ticks must not be negative, was %s".formatted(stalledTicks));
        }
        if (detourRing < -1) {
            throw new IllegalArgumentException(
                    "detour ring must be -1 for no detour or a ring index, was %s".formatted(detourRing));
        }
    }

    /**
     * The state of a racer on the grid, before its first tick: at the start distance, at the floor speed, with no
     * ring passed.
     *
     * @param seat the seat the racer holds
     * @return the starting state
     */
    public static ComState start(ComSeat seat) {
        return new ComState(seat, START_DISTANCE, ComTuning.FLOOR_SPEED, RingProgress.atStart(), ComDetour.NONE,
                -1, NO_BOOST, 0, ComStatus.RACING);
    }
}
