package net.elytrarace.voyager.race.com;

import org.jetbrains.annotations.ApiStatus;

/**
 * One computer racer's seat on the grid: its place among the computer racers and the seed its personal state
 * is drawn from.
 *
 * @param index the place among the computer racers, counted from zero
 * @param seed the seed of this racer's state, derived from the round seed and the index
 */
@ApiStatus.Internal
public record ComSeat(int index, long seed) {

    /**
     * The name players see for this racer, {@code COM n} where n counts from one.
     *
     * @return the name, for example {@code COM 1} for index zero
     */
    public String name() {
        return "COM %s".formatted(index + 1);
    }
}
