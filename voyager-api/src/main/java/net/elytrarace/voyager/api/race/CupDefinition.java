package net.elytrarace.voyager.api.race;

import net.elytrarace.voyager.api.race.exception.InvalidCupException;

import java.util.List;

/** A rotation of maps played one after another under a single {@link GameMode}. */
public record CupDefinition(String name, List<String> mapNames, GameMode mode) {

    // @NotNullByDefault says none of these can be null, and the null checks are here anyway: the
    // record is built from map JSON by a loader that is not written yet, and a deserialiser is
    // exactly the caller an annotation cannot constrain. Checking one component and trusting the
    // next is the part that was wrong — a null name was rejected while a null mapNames reached
    // isEmpty() and came back as an NPE with no cup name in it, and a null mode passed clean
    // through to the state machine.
    public CupDefinition {
        if (name == null || name.isBlank()) {
            throw InvalidCupException.blankName(name);
        }
        if (mapNames == null || mapNames.isEmpty()) {
            throw InvalidCupException.emptyMapNames();
        }
        if (mode == null) {
            throw InvalidCupException.missingMode();
        }
        mapNames = List.copyOf(mapNames);
    }
}
