package net.elytrarace.setup.session;

import java.util.UUID;

/**
 * Serializable snapshot of a {@link SetupSession} for JSON persistence.
 * <p>
 * This record is what gets written to and read from disk.
 * The undo stack is not persisted because undo operations reference
 * in-memory portal data that may no longer be valid after reconnect.
 */
public record SessionSnapshot(
        UUID playerId,
        SetupState state,
        UUID activeMapId,
        boolean portalPreviewEnabled,
        boolean splinePreviewEnabled
) {

    /**
     * Creates a snapshot from a live session.
     */
    public static SessionSnapshot from(SetupSession session) {
        return new SessionSnapshot(
                session.playerId(),
                session.state(),
                session.activeMapId().orElse(null),
                session.portalPreviewEnabled(),
                session.splinePreviewEnabled()
        );
    }

    /**
     * Restores a {@link SetupSessionImpl} from this snapshot.
     */
    public SetupSessionImpl toSession() {
        return new SetupSessionImpl(
                playerId,
                state,
                activeMapId,
                portalPreviewEnabled,
                splinePreviewEnabled
        );
    }
}
