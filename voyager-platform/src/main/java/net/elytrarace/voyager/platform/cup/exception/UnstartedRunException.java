package net.elytrarace.voyager.platform.cup.exception;

import java.util.UUID;

/**
 * Thrown when a movement tick is played for a player who holds no run on the current map.
 *
 * <p>A run is started in exactly one place — by the transition that put the player at that map's
 * spawn — so "no run" means "this player was never moved here". The alternative, quietly starting a
 * run on the first tick that asks for one, is the defect this exception exists to prevent: it would
 * give a player who is standing somewhere else a course they are not flying, timed from a tick in
 * the middle of the race, and the scoreboard at the end would be the first thing to notice.
 *
 * <p>Nobody is expected to catch this. It reports a driver wired to tick a player the transition
 * never moved, which is fixed in the wiring rather than recovered from at runtime.
 */
public final class UnstartedRunException extends RuntimeException {

    public UnstartedRunException(UUID playerId) {
        super("player %s holds no run on the current map; a run is started only by moving the player to that map's spawn"
                .formatted(playerId));
    }
}
