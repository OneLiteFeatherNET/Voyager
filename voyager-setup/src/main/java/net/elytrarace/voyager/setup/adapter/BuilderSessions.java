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

    /** Opens a session for the builder, closing the one it replaces. */
    public void open(UUID builder, MapSession session) {
        MapSession previous = sessions.put(builder, session);
        if (previous != null) {
            previous.close();
        }
    }

    /** Closes the builder's session and removes its previews. */
    public void forget(UUID builder) {
        MapSession previous = sessions.remove(builder);
        if (previous != null) {
            previous.close();
        }
    }
}
