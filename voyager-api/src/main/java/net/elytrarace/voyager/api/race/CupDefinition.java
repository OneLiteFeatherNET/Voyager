package net.elytrarace.voyager.api.race;

import net.elytrarace.voyager.api.race.exception.InvalidCupException;

import java.util.List;

/** A rotation of maps played one after another under a single {@link GameMode}. */
public record CupDefinition(String name, List<String> mapNames, GameMode mode) {

    public CupDefinition {
        if (name == null || name.isBlank()) {
            throw InvalidCupException.blankName(name);
        }
        if (mapNames.isEmpty()) {
            throw InvalidCupException.emptyMapNames();
        }
        mapNames = List.copyOf(mapNames);
    }
}
