package net.elytrarace.server.scoring;

import net.elytrarace.server.physics.Ring;

import java.util.List;
import java.util.UUID;

/**
 * Service for tracking and computing player scores within a single map.
 */
public interface ScoringService {

    /**
     * Records that a player has passed through a ring, adding the ring's
     * point value to the player's score.
     *
     * @param playerId the player who passed through the ring
     * @param ring     the ring that was passed through
     */
    void onRingPassed(UUID playerId, Ring ring);

    /**
     * Returns the current score for the given player. If the player has no
     * recorded score, a zero-valued score is returned.
     *
     * @param playerId the player to look up
     * @return the player's current score
     */
    PlayerScore getScore(UUID playerId);

    /**
     * Returns all player scores sorted by totalPoints in descending order.
     *
     * @return an unmodifiable ranked list of player scores
     */
    List<PlayerScore> getRanking();

    /**
     * Applies position bonuses to all tracked players based on their current
     * ranking. 1st place receives 50 points, 2nd 30, 3rd 20, and all
     * remaining players receive 10 points each.
     */
    void applyPositionBonuses();

    /**
     * Resets all scores. Intended to be called between maps within a cup.
     */
    void reset();
}
