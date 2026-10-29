package net.elytrarace.voyager.platform.cup;

import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The bursts that mark a cup's top three, as a schedule the tick loop walks.
 *
 * <p>The first place gets five bursts, the second and third two each, and every other place none. The bursts are spread over
 * the first {@link #LENGTH_TICKS} ticks after the cup ends, four seconds, so the last one is off before the title leaves the
 * screen. {@link #tick()} is called once per tick and answers the bursts due on that tick; it holds no clock of its own.
 */
@ApiStatus.Internal
public final class CupFinale {

    /** Four seconds, in ticks: the length of the whole finale. */
    public static final int LENGTH_TICKS = 80;

    /**
     * One firework to set off.
     *
     * @param racer the racer the burst is for, at whose position it goes off
     * @param place the racer's place, which chooses the colour
     */
    public record Burst(UUID racer, int place) {
    }

    private final Map<Integer, List<Burst>> dueByTick;
    private int tick;

    /**
     * @param placeByRacer the place of each racer of the cup, from one; places below three have no burst
     */
    public CupFinale(Map<UUID, Integer> placeByRacer) {
        this.dueByTick = new HashMap<>();
        placeByRacer.forEach((racer, place) -> {
            for (int offset : burstTicks(place)) {
                dueByTick.computeIfAbsent(offset, ignored -> new ArrayList<>()).add(new Burst(racer, place));
            }
        });
    }

    /**
     * The tick offsets of a place's bursts, from the tick the cup ends. The winner's five are spread across the four seconds;
     * second and third get two each; every other place gets none.
     */
    static List<Integer> burstTicks(int place) {
        return switch (place) {
            case 1 -> List.of(0, 16, 32, 48, 64);
            case 2, 3 -> List.of(0, 40);
            default -> List.of();
        };
    }

    /**
     * The bursts due on the current tick, and advances the schedule by one tick.
     *
     * @return the bursts to set off now, empty on every other tick
     */
    public List<Burst> tick() {
        List<Burst> due = dueByTick.getOrDefault(tick, List.of());
        tick++;
        return due;
    }

    /** Whether the whole schedule has run: no burst is due after this tick. */
    public boolean over() {
        return tick >= LENGTH_TICKS;
    }
}
