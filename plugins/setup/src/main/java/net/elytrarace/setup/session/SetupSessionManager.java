package net.elytrarace.setup.session;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages the lifecycle of {@link SetupSession} instances.
 */
public interface SetupSessionManager {

    /**
     * Creates a new session for the given player.
     * If a session already exists, it is replaced.
     *
     * @param playerId the player UUID
     * @return the newly created session
     */
    SetupSession create(UUID playerId);

    /**
     * Returns the session for the given player, if one exists.
     */
    Optional<SetupSession> get(UUID playerId);

    /**
     * Removes and discards the session for the given player.
     */
    void remove(UUID playerId);

    /**
     * Returns all active sessions.
     */
    Collection<SetupSession> all();
}
