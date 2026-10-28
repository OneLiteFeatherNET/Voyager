package net.elytrarace.voyager.api.race;

import net.elytrarace.voyager.api.race.exception.InvalidAuthorException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A player who built a map or a cup, as the catalogue records them: the player's UUID and the name the player is shown
 * under. The name is kept as the file states it and is not looked up at load (ADR-0040).
 *
 * @param uuid the player's UUID
 * @param name the player's name, {@code [A-Za-z0-9_]{1,16}}
 */
public record Author(UUID uuid, String name) {

    /** The most authors one map or cup may state. */
    public static final int MAXIMUM = 8;

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    public Author {
        if (uuid == null) {
            throw InvalidAuthorException.missingUuid();
        }
        if (name == null || !NAME.matcher(name).matches()) {
            throw InvalidAuthorException.badName(name);
        }
    }

    /**
     * The authors of one map or cup, copied, after the limits of the catalogue: at most {@link #MAXIMUM} authors, and no
     * UUID stated twice.
     *
     * @throws InvalidAuthorException if the list is too long or repeats a UUID
     */
    public static List<Author> checked(List<Author> authors) {
        if (authors.size() > MAXIMUM) {
            throw InvalidAuthorException.tooMany(authors.size(), MAXIMUM);
        }
        Set<UUID> seen = new HashSet<>();
        for (Author author : authors) {
            if (!seen.add(author.uuid())) {
                throw InvalidAuthorException.repeated(author.uuid().toString());
            }
        }
        return List.copyOf(authors);
    }
}
