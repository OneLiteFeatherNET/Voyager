package net.elytrarace.setup.undo;

import net.elytrarace.common.map.model.FilePortalDTO;

import java.util.UUID;

/**
 * Represents a reversible portal operation.
 */
public sealed interface UndoOperation permits UndoOperation.PlaceOperation, UndoOperation.DeleteOperation {

    UUID mapUuid();
    FilePortalDTO portal();

    /**
     * A portal was placed — undo by removing it.
     */
    record PlaceOperation(UUID mapUuid, FilePortalDTO portal) implements UndoOperation {}

    /**
     * A portal was deleted — undo by re-adding it.
     */
    record DeleteOperation(UUID mapUuid, FilePortalDTO portal) implements UndoOperation {}
}
