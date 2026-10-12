package net.elytrarace.voyager.api.race.exception;

import net.elytrarace.voyager.api.race.LobbyDefinition;

/**
 * Thrown when a {@link LobbyDefinition} is constructed with a value that cannot name a lobby world: a blank world
 * name, or no spawn at all.
 */
public final class InvalidLobbyException extends RuntimeException {

    private InvalidLobbyException(String message) {
        super(message);
    }

    public static InvalidLobbyException blankWorld(String world) {
        return new InvalidLobbyException(
                "the lobby world name must name a world directory, was '%s'".formatted(world));
    }
}
