package net.elytrarace.voyager.platform.lobby;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;

import java.util.ArrayList;
import java.util.List;

/**
 * Groups joins that arrive close together, so a lobby of eight does not print eight lines in a few seconds.
 *
 * <p>The first join of a burst is told at once. Each later join inside the window is held, and the held names
 * are told as one line when the window closes. The window is {@link #WINDOW_TICKS} game ticks, counted from the
 * burst's first join, and the caller passes the tick in: nothing here reads a clock or schedules anything.
 */
@ApiStatus.Internal
public final class JoinBurst {

    /** Three seconds at 20 TPS. A join this many ticks after the burst opened still belongs to it. */
    public static final int WINDOW_TICKS = 60;

    private static final int NO_WINDOW = Integer.MIN_VALUE;

    private int windowStart = NO_WINDOW;
    private final List<String> held = new ArrayList<>();

    /**
     * What a join means for the lines the room sends.
     *
     * @param sendNow whether this join opens a burst and is told at once
     * @param flushed the names held by the burst that just closed, which are told before this join; empty if none
     */
    public record Outcome(boolean sendNow, List<String> flushed) {

        public Outcome {
            flushed = List.copyOf(flushed);
        }
    }

    /**
     * Records a join at {@code gameTick}.
     *
     * @param gameTick the game tick of the join, which must not go backwards between calls
     * @param name the racer who joined, as it is told to the others
     */
    @Contract(mutates = "this")
    public Outcome join(int gameTick, String name) {
        List<String> flushed = closeIfOver(gameTick);
        if (windowStart == NO_WINDOW) {
            windowStart = gameTick;
            return new Outcome(true, flushed);
        }
        held.add(name);
        return new Outcome(false, flushed);
    }

    /**
     * Closes the window if it has run out by {@code gameTick}, and returns the names it held, in the order they joined.
     *
     * @param gameTick the current game tick
     */
    @Contract(mutates = "this")
    public List<String> flush(int gameTick) {
        return closeIfOver(gameTick);
    }

    private List<String> closeIfOver(int gameTick) {
        if (windowStart == NO_WINDOW || gameTick - windowStart <= WINDOW_TICKS) {
            return List.of();
        }
        List<String> flushed = List.copyOf(held);
        held.clear();
        windowStart = NO_WINDOW;
        return flushed;
    }
}
