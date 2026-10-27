package net.elytrarace.voyager.api.race;

import net.elytrarace.voyager.api.race.exception.InvalidCupException;

import java.util.List;

/**
 * A rotation of maps played one after another under a single {@link GameMode}.
 *
 * <p>{@code name} is the cup's id: the key of the catalogue and the name operators type. {@link #displayName()} is what
 * players read. A blank or absent display name is the id, so it is never null.
 *
 * @param name        the cup's id
 * @param mapNames    the maps of the rotation, in play order
 * @param mode        the mode the cup is played under
 * @param displayName the name players read; {@code null} or blank means the id
 */
public record CupDefinition(String name, List<String> mapNames, GameMode mode, String displayName) {

    /** A cup whose players read its id: the id is its display name. */
    public CupDefinition(String name, List<String> mapNames, GameMode mode) {
        this(name, mapNames, mode, name);
    }

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
        displayName = displayName == null || displayName.isBlank() ? name : displayName;
    }
}
