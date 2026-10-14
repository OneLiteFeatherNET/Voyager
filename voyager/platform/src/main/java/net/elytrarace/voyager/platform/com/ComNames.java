package net.elytrarace.voyager.platform.com;

import net.elytrarace.voyager.platform.text.Messages;
import net.elytrarace.voyager.race.com.ComSeat;
import net.kyori.adventure.text.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * The names and ids of computer racers, derived from their seats.
 *
 * <p>A computer racer's id is a name-based UUID of its seed and index, so the same round seed always gives the same
 * id, and nothing is drawn from a clock or an unseeded generator. The top 32 bits of every such id carry a fixed
 * marker, which is how {@link #isComputer} tells a computer racer apart from a human without any stored state.
 */
public final class ComNames {

    /** The top 32 bits of every computer racer's id: the ASCII letters {@code COM} followed by a zero byte. */
    private static final long MARKER = 0x434F4D00L;

    private ComNames() {
    }

    /**
     * The name a seat is shown under.
     *
     * @param seat the seat on the grid
     * @return {@code COM n}, where n counts the computer racers from one
     */
    public static String nameOf(ComSeat seat) {
        return seat.name();
    }

    /**
     * The name a seat is shown under, as a translated component for the body and the tab list.
     *
     * @param seat the seat on the grid
     * @return the translation of {@code COM n}, where n counts the computer racers from one
     */
    public static Component displayNameOf(ComSeat seat) {
        return Messages.computerRacerName(seat.index() + 1);
    }

    /**
     * The id of the computer racer that holds {@code seat}.
     *
     * @param seat the seat on the grid
     * @return a name-based UUID of the seat's seed and index, carrying the computer racer marker
     */
    public static UUID idOf(ComSeat seat) {
        UUID base = UUID.nameUUIDFromBytes("voyager-com:%d:%d".formatted(seat.seed(), seat.index())
                .getBytes(StandardCharsets.UTF_8));
        long mostSignificantBits = (MARKER << 32) | (base.getMostSignificantBits() & 0xFFFF_FFFFL);
        return new UUID(mostSignificantBits, base.getLeastSignificantBits());
    }

    /**
     * Whether an id belongs to a computer racer. The persistence change reads this flag, so a computer racer is never
     * stored as a player.
     *
     * @param id the id to test
     * @return {@code true} when the id carries the computer racer marker
     */
    public static boolean isComputer(UUID id) {
        return (id.getMostSignificantBits() >>> 32) == MARKER;
    }
}
