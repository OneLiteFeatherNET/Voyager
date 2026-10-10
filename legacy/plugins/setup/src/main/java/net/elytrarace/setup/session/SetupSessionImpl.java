package net.elytrarace.setup.session;

import net.elytrarace.setup.undo.UndoStack;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Default implementation of {@link SetupSession}.
 * <p>
 * State transitions are enforced via a switch expression so that invalid
 * transitions fail fast with a clear error message.
 */
public final class SetupSessionImpl implements SetupSession {

    private final UUID playerId;
    private final UndoStack undoStack;

    private SetupState state;
    private UUID activeMapId;
    private boolean portalPreviewEnabled;
    private boolean splinePreviewEnabled;

    public SetupSessionImpl(UUID playerId) {
        this(playerId, SetupState.IDLE, null, false, false);
    }

    /**
     * Restore constructor — used when loading a persisted session from JSON.
     */
    public SetupSessionImpl(UUID playerId, SetupState state, UUID activeMapId,
                            boolean portalPreviewEnabled, boolean splinePreviewEnabled) {
        this.playerId = Objects.requireNonNull(playerId, "playerId must not be null");
        this.state = Objects.requireNonNull(state, "state must not be null");
        this.activeMapId = activeMapId;
        this.portalPreviewEnabled = portalPreviewEnabled;
        this.splinePreviewEnabled = splinePreviewEnabled;
        this.undoStack = new UndoStack();
    }

    @Override
    public UUID playerId() {
        return playerId;
    }

    @Override
    public SetupState state() {
        return state;
    }

    @Override
    public void transition(SetupEvent event) {
        this.state = nextState(this.state, event);
    }

    @Override
    public Optional<UUID> activeMapId() {
        return Optional.ofNullable(activeMapId);
    }

    @Override
    public void setActiveMapId(UUID mapId) {
        this.activeMapId = mapId;
    }

    @Override
    public UndoStack undoStack() {
        return undoStack;
    }

    @Override
    public boolean portalPreviewEnabled() {
        return portalPreviewEnabled;
    }

    @Override
    public void setPortalPreview(boolean enabled) {
        this.portalPreviewEnabled = enabled;
    }

    @Override
    public boolean splinePreviewEnabled() {
        return splinePreviewEnabled;
    }

    @Override
    public void setSplinePreview(boolean enabled) {
        this.splinePreviewEnabled = enabled;
    }

    /**
     * Computes the next state given the current state and an incoming event.
     * Invalid transitions throw {@link IllegalStateException}.
     */
    private static SetupState nextState(SetupState current, SetupEvent event) {
        return switch (current) {
            case IDLE -> switch (event) {
                case ENTER_MAP_WORLD -> SetupState.MAP_SELECTED;
                default -> throw invalidTransition(current, event);
            };
            case MAP_SELECTED -> switch (event) {
                case LEAVE_MAP_WORLD -> SetupState.IDLE;
                case START_PORTAL_EDIT -> SetupState.PORTAL_EDITING;
                case START_TESTFLY -> SetupState.TESTFLY_ACTIVE;
                default -> throw invalidTransition(current, event);
            };
            case PORTAL_PLACING -> switch (event) {
                case LEAVE_MAP_WORLD -> SetupState.IDLE;
                case START_PORTAL_EDIT -> SetupState.PORTAL_EDITING;
                default -> throw invalidTransition(current, event);
            };
            case PORTAL_EDITING -> switch (event) {
                case FINISH_PORTAL_EDIT -> SetupState.MAP_SELECTED;
                case LEAVE_MAP_WORLD -> SetupState.IDLE;
                default -> throw invalidTransition(current, event);
            };
            case TESTFLY_ACTIVE -> switch (event) {
                case FINISH_TESTFLY -> SetupState.MAP_SELECTED;
                case LEAVE_MAP_WORLD -> SetupState.IDLE;
                default -> throw invalidTransition(current, event);
            };
        };
    }

    private static IllegalStateException invalidTransition(SetupState current, SetupEvent event) {
        return new IllegalStateException(
                "Cannot handle event " + event + " in state " + current);
    }
}
