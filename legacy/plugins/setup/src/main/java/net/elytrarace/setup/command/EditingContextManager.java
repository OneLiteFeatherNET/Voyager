package net.elytrarace.setup.command;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages per-player portal editing state.
 */
public final class EditingContextManager {

    private final Map<UUID, EditingContext> contexts = new ConcurrentHashMap<>();

    public void startEditing(UUID playerId, EditingContext context) {
        contexts.put(playerId, context);
    }

    public Optional<EditingContext> getContext(UUID playerId) {
        return Optional.ofNullable(contexts.get(playerId));
    }

    public void clearContext(UUID playerId) {
        contexts.remove(playerId);
    }

    public boolean isEditing(UUID playerId) {
        return contexts.containsKey(playerId);
    }
}
