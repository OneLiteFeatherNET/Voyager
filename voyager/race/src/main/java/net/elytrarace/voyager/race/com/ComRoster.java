package net.elytrarace.voyager.race.com;

import net.elytrarace.voyager.race.com.exception.InvalidComCountException;

import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * Plans the computer racer seats that fill a grid to its target count.
 *
 * <p>Humans count toward the target, so a grid with more humans than the target gets no computer seats. The seats
 * are a pure function of the target, the human count and the round seed, so the same inputs always give the same
 * seats. The round seed is passed in; this class reads no clock and no unseeded randomness.
 */
@ApiStatus.Internal
public final class ComRoster {

    /** The most computer racers a cup can hold (owner decision OQ7). */
    public static final int MAXIMUM_COUNT = 15;

    private ComRoster() {}

    /**
     * Plans the computer seats for a grid.
     *
     * @param targetCount the grid size to fill, humans included, between zero and {@link #MAXIMUM_COUNT}
     * @param humans the human racers already on the grid, never negative
     * @param seed the round seed that every seat's state is derived from
     * @return the computer seats in order, empty when the humans already fill the target
     * @throws InvalidComCountException if the target is outside its range or the human count is negative
     */
    public static List<ComSeat> plan(int targetCount, int humans, long seed) {
        if (targetCount < 0 || targetCount > MAXIMUM_COUNT) {
            throw InvalidComCountException.outOfRange(targetCount, MAXIMUM_COUNT);
        }
        if (humans < 0) {
            throw InvalidComCountException.negativeHumans(humans);
        }
        int seats = Math.max(0, targetCount - humans);
        List<ComSeat> plan = new ArrayList<>(seats);
        for (int index = 0; index < seats; index++) {
            plan.add(new ComSeat(index, seatSeed(seed, index)));
        }
        return List.copyOf(plan);
    }

    /** Mixes the round seed with the seat index (SplitMix64 finaliser), so neighbouring seats get unrelated seeds. */
    private static long seatSeed(long roundSeed, int index) {
        long z = roundSeed + (index + 1L) * 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
