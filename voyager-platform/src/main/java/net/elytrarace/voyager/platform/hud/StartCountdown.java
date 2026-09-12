package net.elytrarace.voyager.platform.hud;

import java.time.Duration;

/**
 * The last three seconds of a lobby, counted out loud.
 *
 * <h2>The problem</h2>
 *
 * <p>Without this, a map begins by teleporting every racer onto the spawn and firing them into the
 * air <em>in the same tick</em>. The player arrives in a world whose chunks are still arriving,
 * facing a direction the server chose for them, and is already flying before they have registered
 * that the map changed. Three things a racer needs go at once: a clear goal, a sense of control, and
 * any chance of matching the challenge in the first two seconds.
 *
 * <h2>Where the time comes from</h2>
 *
 * <p>Out of the lobby, not out of the race. The {@code GAME} phase still begins with the launch on
 * its first tick and the race clock keeps exactly the meaning it had — no clock runs while the player
 * is not playing. The racer spends the last sixty ticks of the lobby standing on the spawn, already
 * turned toward the first ring, watching the count.
 *
 * <h2>It degrades, it does not delay</h2>
 *
 * <p>A lobby shorter than {@link #LENGTH} — a dev run, or {@code /race start} with the lobby skipped
 * entirely — gets whatever digits fit, down to none. A countdown that reintroduced a three-second
 * wait into the one tool that exists to remove waiting would defeat its own purpose.
 *
 * <h2>Why it is stateful</h2>
 *
 * <p>Because a digit is shown once, on the tick it becomes true, and not on the nineteen ticks after
 * it that are still inside the same second. This object remembers the last digit it handed out; a
 * caller drives it with the lobby's remaining time and acts only when it answers with something.
 */
public final class StartCountdown {

    /** How long the count lasts: three seconds. Longer and the racer disengages before the start. */
    public static final Duration LENGTH = Duration.ofSeconds(3);

    /** What {@link #digitFor} answers when there is no digit to show. */
    public static final int NONE = 0;

    private static final long MILLIS_PER_SECOND = 1_000L;

    private int shown = NONE;

    /**
     * The digit to put on screen now, or {@link #NONE} if there is nothing new to show.
     *
     * @param remainingLobby how much of the lobby is left after the tick being played
     * @return the digit, once per digit; {@link #NONE} on every other tick
     */
    public int show(Duration remainingLobby) {
        int digit = digitFor(remainingLobby);
        if (digit == NONE || digit == shown) {
            return NONE;
        }
        shown = digit;
        return digit;
    }

    /** Whether any digit has been shown for the map this countdown is counting into. */
    public boolean started() {
        return shown != NONE;
    }

    /** Forgets the count, for the next map. */
    public void reset() {
        shown = NONE;
    }

    /**
     * Which digit {@code remainingLobby} is inside.
     *
     * <p>Rounded up, so "3" covers every tick from three seconds left down to just over two. The
     * boundaries are therefore exact and inclusive at the top: at exactly 3.000 s left the answer is
     * 3, at 3.050 s it is {@link #NONE}, and at exactly 2.000 s it has already become 2. A racer
     * reads a digit that is true for the second it names, which is the only reading that makes the
     * last one land on the launch.
     *
     * <p>Zero left is {@link #NONE} and not "0": that tick is the launch, and the launch says
     * {@code GO}.
     */
    static int digitFor(Duration remainingLobby) {
        if (remainingLobby.isNegative() || remainingLobby.isZero()
                || remainingLobby.compareTo(LENGTH) > 0) {
            return NONE;
        }
        long millis = remainingLobby.toMillis();
        return (int) ((millis + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND);
    }
}
