package net.elytrarace.setup.session;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory implementation of {@link SetupSessionManager}.
 */
public final class SetupSessionManagerImpl implements SetupSessionManager {

    private final Map<UUID, SetupSession> sessions = new ConcurrentHashMap<>();

    @Override
    public SetupSession create(UUID playerId) {
        var session = new SetupSessionImpl(playerId);
        sessions.put(playerId, session);
        return session;
    }

    /**
     * Inserts a pre-built session (e.g. restored from JSON persistence).
     *
     * @param session the session to register
     */
    public void put(SetupSession session) {
        sessions.put(session.playerId(), session);
    }

    @Override
    public Optional<SetupSession> get(UUID playerId) {
        return Optional.ofNullable(sessions.get(playerId));
    }

    @Override
    public void remove(UUID playerId) {
        sessions.remove(playerId);
    }

    @Override
    public Collection<SetupSession> all() {
        return Collections.unmodifiableCollection(sessions.values());
    }
}
