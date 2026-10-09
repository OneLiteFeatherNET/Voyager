package net.elytrarace.voyager.setup.adapter;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The open map of each builder. One builder has one open map; opening another replaces the session, and the previous
 * draft is already saved, because every change is saved when it is made.
 */
public final class BuilderSessions {

    private final Map<UUID, MapSession> sessions = new HashMap<>();

    public Optional<MapSession> find(UUID builder) {
        return Optional.ofNullable(sessions.get(builder));
    }

    public void open(UUID builder, MapSession session) {
        sessions.put(builder, session);
    }

    public void forget(UUID builder) {
        sessions.remove(builder);
    }
}
