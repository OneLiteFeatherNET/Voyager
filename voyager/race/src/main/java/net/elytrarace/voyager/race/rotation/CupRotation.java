package net.elytrarace.voyager.race.rotation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The cups a server plays in turn, and the one a round arms to play next.
 *
 * <p>An immutable value: {@link #advance()} returns the rotation one cup further on and leaves this one as it was, so
 * a caller that decides not to move (an aborted cup, a restart) keeps the position it had. The list is the play order.
 * The last cup is followed by the first.
 *
 * <p>Names are compared as they are given. The platform resolves each entry to its canonical cup name before it builds
 * a rotation, so two spellings of one cup are one name here.
 *
 * @param cups     the cups in play order, never empty and never naming a cup twice
 * @param position the index of the armed cup in {@code cups}
 */
public record CupRotation(List<String> cups, int position) {

    /**
     * @throws IllegalArgumentException if the list is empty, names a cup twice, or the position is outside the list
     */
    public CupRotation {
        cups = List.copyOf(cups);
        if (cups.isEmpty()) {
            throw new IllegalArgumentException("a cup rotation names at least one cup");
        }
        Set<String> seen = new HashSet<>();
        for (String cup : cups) {
            if (!seen.add(cup)) {
                throw new IllegalArgumentException("cup '%s' is named twice in the rotation".formatted(cup));
            }
        }
        if (position < 0 || position >= cups.size()) {
            throw new IllegalArgumentException("position %d is outside the %d cup(s) of the rotation"
                    .formatted(position, cups.size()));
        }
    }

    /**
     * A rotation with its first cup armed.
     *
     * @param cups the cups in play order
     * @throws IllegalArgumentException if the list is empty or names a cup twice
     */
    public static CupRotation of(List<String> cups) {
        return new CupRotation(cups, 0);
    }

    /**
     * A rotation with the named cup armed.
     *
     * @param cups  the cups in play order
     * @param armed the cup to arm; it must be one of {@code cups}
     * @throws IllegalArgumentException if the list is empty, names a cup twice, or does not name {@code armed}
     */
    public static CupRotation starting(List<String> cups, String armed) {
        int index = cups.indexOf(armed);
        if (index < 0) {
            throw new IllegalArgumentException("cup '%s' is not in the rotation %s".formatted(armed, cups));
        }
        return new CupRotation(cups, index);
    }

    /** The cup a round plays next. */
    public String armed() {
        return cups.get(position);
    }

    /** The rotation with the cup after the armed one armed, the last cup's successor being the first. */
    public CupRotation advance() {
        return new CupRotation(cups, (position + 1) % cups.size());
    }

    /** Whether more than one cup takes part, so that a round can play a different cup from the last one. */
    public boolean rotates() {
        return cups.size() > 1;
    }
}
