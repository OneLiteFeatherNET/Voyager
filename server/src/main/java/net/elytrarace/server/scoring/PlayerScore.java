package net.elytrarace.server.scoring;

import java.util.UUID;

/**
 * Immutable record representing a player's score within a single map.
 *
 * @param playerId      the unique identifier of the player
 * @param ringPoints    points accumulated from passing through rings
 * @param positionBonus bonus points awarded based on finishing position
 * @param totalPoints   the sum of ringPoints and positionBonus
 */
public record PlayerScore(UUID playerId, int ringPoints, int positionBonus, int totalPoints) {

    /**
     * Returns a new {@code PlayerScore} with the given ring points added.
     *
     * @param points the ring points to add
     * @return a new score with updated ringPoints and totalPoints
     */
    public PlayerScore addRingPoints(int points) {
        return new PlayerScore(playerId, ringPoints + points, positionBonus, ringPoints + points + positionBonus);
    }

    /**
     * Returns a new {@code PlayerScore} with the given position bonus applied,
     * replacing any previous bonus.
     *
     * @param bonus the position bonus to set
     * @return a new score with updated positionBonus and totalPoints
     */
    public PlayerScore withPositionBonus(int bonus) {
        return new PlayerScore(playerId, ringPoints, bonus, ringPoints + bonus);
    }
}
