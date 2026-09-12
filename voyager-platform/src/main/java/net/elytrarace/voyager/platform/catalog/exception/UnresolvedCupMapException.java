package net.elytrarace.voyager.platform.catalog.exception;

import java.util.List;

/**
 * Thrown when a cup plays a map the map catalogue does not know.
 *
 * <p>This is the failure with the longest fuse in the tree being replaced, and the reason the check
 * exists at all. Each catalogue on its own is perfectly consistent: the cup loads, the maps load,
 * nothing is malformed. The mismatch only becomes visible when that map comes up in the rotation —
 * which may be a fortnight after the typo, halfway through a race night, to a lobby full of players.
 *
 * <p>Every unresolved name is listed rather than just the first, because a renamed map usually
 * breaks several cups at once and fixing them one boot at a time is its own kind of outage.
 */
public final class UnresolvedCupMapException extends RuntimeException {

    public UnresolvedCupMapException(List<String> problems) {
        super("%s cup entr%s name a map no map definition provides: %s".formatted(
                problems.size(), problems.size() == 1 ? "y does" : "ies do", String.join("; ", problems)));
    }
}
