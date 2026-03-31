package net.elytrarace.server.game;

import net.elytrarace.server.cup.CupDefinition;
import net.elytrarace.server.cup.CupFlowService;
import net.elytrarace.server.scoring.ScoringService;
import net.minestom.server.coordinate.Vec;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the mutable state of a single running game session.
 * <p>
 * Each session is bound to one cup and tracks per-player velocity,
 * which rings have been passed, and which players are participating.
 */
public final class GameSession {

    private final UUID sessionId;
    private final CupDefinition cup;
    private final CupFlowService cupFlow;
    private final ScoringService scoring;
    private final Set<UUID> players = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Vec> playerVelocities = new ConcurrentHashMap<>();
    private final Set<String> passedRings = ConcurrentHashMap.newKeySet();

    public GameSession(UUID sessionId, CupDefinition cup, CupFlowService cupFlow, ScoringService scoring) {
        this.sessionId = sessionId;
        this.cup = cup;
        this.cupFlow = cupFlow;
        this.scoring = scoring;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public CupDefinition getCup() {
        return cup;
    }

    public CupFlowService getCupFlow() {
        return cupFlow;
    }

    public ScoringService getScoring() {
        return scoring;
    }

    public Set<UUID> getPlayers() {
        return Collections.unmodifiableSet(players);
    }

    public void addPlayer(UUID playerId) {
        players.add(playerId);
    }

    public void removePlayer(UUID playerId) {
        players.remove(playerId);
        playerVelocities.remove(playerId);
    }

    public Vec getVelocity(UUID playerId) {
        return playerVelocities.getOrDefault(playerId, Vec.ZERO);
    }

    public void setVelocity(UUID playerId, Vec velocity) {
        playerVelocities.put(playerId, velocity);
    }

    /**
     * Checks whether the given player has already passed through the ring at the given index.
     *
     * @param playerId  the player
     * @param ringIndex the zero-based ring index within the current map
     * @return {@code true} if the ring was already passed
     */
    public boolean hasPassedRing(UUID playerId, int ringIndex) {
        return passedRings.contains(ringKey(playerId, ringIndex));
    }

    /**
     * Marks the ring at the given index as passed for the given player.
     *
     * @param playerId  the player
     * @param ringIndex the zero-based ring index within the current map
     */
    public void markRingPassed(UUID playerId, int ringIndex) {
        passedRings.add(ringKey(playerId, ringIndex));
    }

    /**
     * Resets all passed-ring state. Call this when advancing to the next map.
     */
    public void resetPassedRings() {
        passedRings.clear();
    }

    private static String ringKey(UUID playerId, int ringIndex) {
        return playerId + ":" + ringIndex;
    }
}
