package net.elytrarace.voyager.api.handoff;

import net.elytrarace.voyager.api.handoff.exception.HandoffUnavailableException;

import java.util.UUID;

/**
 * Sends one racer to the network hub. The server asks this port when a cup has ended and its results have lingered; the
 * bridge answers it with CloudNet's fallback connection, and the server answers it with a disconnect when no bridge runs.
 *
 * <p>Deliberately an interface with one operation and no result: the hub decides where the racer lands, and a move that
 * has been asked for is either accepted or refused. A refusal is the only failure, and it is reported as
 * {@link HandoffUnavailableException}. Nothing here retries.
 */
@FunctionalInterface
public interface PlayerHandoff {

    /**
     * Asks for the racer to be moved to the hub.
     *
     * @param playerId the racer's unique id
     * @throws HandoffUnavailableException if the move is refused or cannot be asked for
     */
    void toHub(UUID playerId) throws HandoffUnavailableException;
}
