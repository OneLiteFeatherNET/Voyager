package net.elytrarace.voyager.cloudnet.bridge;

import net.elytrarace.voyager.api.handoff.PlayerHandoff;
import net.elytrarace.voyager.api.handoff.exception.HandoffUnavailableException;

import org.jetbrains.annotations.NotNullByDefault;

import java.util.UUID;

/**
 * Sends a racer to the network hub through CloudNet's fallback (ADR-0033). The server asks for the move through the
 * {@link PlayerHandoff} port; this class answers it with one call to CloudNet, which connects the player to the best matching
 * fallback that the proxy's configuration names.
 *
 * <p>The one CloudNet call sits behind {@link FallbackConnector}, so the handoff's logic runs in a test with no CloudNet type on
 * its class path. A failure of that call, from any cause, is reported as {@link HandoffUnavailableException} and is never
 * swallowed: the server then disconnects the racer with the translated message.
 */
@NotNullByDefault
public final class CloudNetPlayerHandoff implements PlayerHandoff {

    /** The one CloudNet call of the handoff: connect the player to the best matching fallback. */
    @FunctionalInterface
    public interface FallbackConnector {

        /**
         * @param playerId the racer to connect to the fallback
         * @throws RuntimeException if CloudNet refuses the connection; the handoff reports it as unavailable
         */
        void connectToFallback(UUID playerId);
    }

    private final FallbackConnector connector;

    /**
     * @param connector the call that connects a player to the fallback; CloudNet's in production, a fake in tests
     */
    public CloudNetPlayerHandoff(FallbackConnector connector) {
        this.connector = connector;
    }

    @Override
    public void toHub(UUID playerId) throws HandoffUnavailableException {
        try {
            connector.connectToFallback(playerId);
        } catch (RuntimeException failure) {
            throw HandoffUnavailableException.refused(playerId, failure);
        }
    }
}
