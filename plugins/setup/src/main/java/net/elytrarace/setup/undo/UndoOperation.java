package net.elytrarace.setup.undo;

import net.elytrarace.common.map.model.FilePortalDTO;

import java.util.UUID;

/**
 * Represents a reversible portal operation.
 */
public sealed interface UndoOperation
        permits UndoOperation.PlaceOperation, UndoOperation.DeleteOperation, UndoOperation.EditOperation {

    UUID mapUuid();

    /**
     * A portal was placed — undo by removing it.
     */
    record PlaceOperation(UUID mapUuid, FilePortalDTO portal) implements UndoOperation {}

    /**
     * A portal was deleted — undo by re-adding it.
     */
    record DeleteOperation(UUID mapUuid, FilePortalDTO portal) implements UndoOperation {}

    /**
     * A portal was edited — undo by removing the new version and re-adding the old one.
     */
    record EditOperation(UUID mapUuid, FilePortalDTO oldPortal, FilePortalDTO newPortal) implements UndoOperation {}
}
