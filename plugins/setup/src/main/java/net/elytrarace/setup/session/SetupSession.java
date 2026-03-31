package net.elytrarace.setup.session;

import net.elytrarace.setup.undo.UndoStack;

import java.util.Optional;
import java.util.UUID;

/**
 * Holds all per-player state for an active setup session.
 * <p>
 * Centralizes undo stack, preview toggles, active map, and state machine
 * so that commands and listeners do not need to query multiple scattered managers.
 */
public interface SetupSession {

    /**
     * Returns the UUID of the player who owns this session.
     */
    UUID playerId();

    /**
     * Returns the current setup state.
     */
    SetupState state();

    /**
     * Applies a state transition triggered by the given event.
     *
     * @param event the event that occurred
     * @throws IllegalStateException if the transition is not valid from the current state
     */
    void transition(SetupEvent event);

    /**
     * Returns the UUID of the map the builder is currently working on, if any.
     */
    Optional<UUID> activeMapId();

    /**
     * Sets the active map the builder is working on.
     *
     * @param mapId the map UUID, or {@code null} to clear
     */
    void setActiveMapId(UUID mapId);

    /**
     * Returns this session's undo stack for portal operations.
     */
    UndoStack undoStack();

    /**
     * Returns whether portal particle preview is enabled.
     */
    boolean portalPreviewEnabled();

    /**
     * Enables or disables portal particle preview.
     */
    void setPortalPreview(boolean enabled);

    /**
     * Returns whether spline path preview is enabled.
     */
    boolean splinePreviewEnabled();

    /**
     * Enables or disables spline path preview.
     */
    void setSplinePreview(boolean enabled);
}
