package net.elytrarace.voyager.api.race;

import net.elytrarace.voyager.api.race.exception.InvalidCupException;

import net.kyori.adventure.key.Key;

import java.util.List;

/**
 * A rotation of maps played one after another under a single {@link GameMode}.
 *
 * <p>{@code key} is the cup's identifier: the key of the catalogue, the reference a map is named by and the key the
 * statistics store (ADR-0040). {@link #displayName()} is what players read. A blank or absent display name is the key's
 * value in title case, so it is never null. {@link #authors()} are the players who built the rotation's cup, if any.
 *
 * @param key         the cup's identifier, in the {@code elytrarace} namespace
 * @param mapNames    the keys of the maps of the rotation, in play order
 * @param mode        the mode the cup is played under
 * @param displayName the name players read; {@code null} or blank means the key's value in title case
 * @param authors     the players who built the cup, at most {@link Author#MAXIMUM}, none repeated
 */
public record CupDefinition(Key key, List<String> mapNames, GameMode mode, String displayName, List<Author> authors) {

    /** A cup built from a flat id, which is read as the key {@code elytrarace:<name>}; it has no authors. */
    public CupDefinition(String name, List<String> mapNames, GameMode mode, String displayName) {
        this(flatKey(name), mapNames, mode, displayName, List.of());
    }

    /** A cup built from a flat id whose players read its key: it has no display name and no authors. */
    public CupDefinition(String name, List<String> mapNames, GameMode mode) {
        this(name, mapNames, mode, null);
    }

    // @NotNullByDefault says none of these can be null, and the null checks are here anyway: the
    // record is built from map JSON by a deserialiser, which an annotation cannot constrain. Checking
    // one component and trusting the next is the part that was wrong — a null name was rejected while
    // a null mapNames reached isEmpty() and came back as an NPE with no cup name in it, and a null mode
    // passed clean through to the state machine.
    public CupDefinition {
        if (key == null) {
            throw InvalidCupException.blankName(null);
        }
        CatalogKeys.checked(key);
        if (mapNames == null || mapNames.isEmpty()) {
            throw InvalidCupException.emptyMapNames();
        }
        if (mode == null) {
            throw InvalidCupException.missingMode();
        }
        // A map named by a flat id is named by its key: the rotation holds keys, whichever form the caller wrote.
        mapNames = mapNames.stream().map(CatalogKeys::normalise).toList();
        displayName = displayName == null || displayName.isBlank() ? CatalogKeys.titleCase(key) : displayName;
        authors = authors == null ? List.of() : Author.checked(authors);
    }

    /** The flat id as its key, or {@code null} for a blank id, which the canonical constructor refuses as a blank name. */
    private static Key flatKey(String name) {
        return name == null || name.isBlank() ? null : CatalogKeys.of(name);
    }
}
