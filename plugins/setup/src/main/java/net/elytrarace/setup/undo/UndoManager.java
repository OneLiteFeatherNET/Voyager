package net.elytrarace.setup.undo;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages per-player undo stacks.
 */
public final class UndoManager {

    private final Map<UUID, UndoStack> stacks = new ConcurrentHashMap<>();

    public UndoStack getStack(UUID playerId) {
        return stacks.computeIfAbsent(playerId, id -> new UndoStack());
    }

    public void push(UUID playerId, UndoOperation operation) {
        getStack(playerId).push(operation);
    }

    public Optional<UndoOperation> pop(UUID playerId) {
        var stack = stacks.get(playerId);
        if (stack == null) {
            return Optional.empty();
        }
        return stack.pop();
    }

    public void removePlayer(UUID playerId) {
        stacks.remove(playerId);
    }
}
