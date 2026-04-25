package net.elytrarace.server.scoring;

import net.elytrarace.common.game.scoring.MedalTier;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Immutable record representing a player's score within a single map.
 *
 * <p>Field semantics:
 * <ul>
 *   <li>{@code ringPoints} — points accumulated by passing through rings during the map.</li>
 *   <li>{@code positionBonus} — non-ring rewards: time-bracket points (DIAMOND/GOLD/...) and
 *       finishing-position bonuses (1st/2nd/3rd/...). Both are folded into this single bucket
 *       so that {@code totalPoints} stays a simple sum of {@code ringPoints + positionBonus}.</li>
 *   <li>{@code totalPoints} — always equal to {@code ringPoints + positionBonus}.</li>
 *   <li>{@code completionTimeMs} — wall-clock time the player needed to finish the map;
 *       {@link #DNF_TIME} (-1) when the player did not finish.</li>
 *   <li>{@code medalTier} — the medal classification for this map; {@code null} when the
 *       player has not yet been classified, {@link MedalTier#DNF} when they did not finish.</li>
 * </ul>
 *
 * @param playerId         the unique identifier of the player
 * @param ringPoints       points accumulated from passing through rings
 * @param positionBonus    bonus points from time brackets and/or finishing position
 * @param totalPoints      ringPoints + positionBonus
 * @param completionTimeMs elapsed map time in milliseconds; {@link #DNF_TIME} for DNF
 * @param medalTier        the earned medal tier; may be {@code null} until classified
 */
public record PlayerScore(
        UUID playerId,
        int ringPoints,
        int positionBonus,
        int totalPoints,
        long completionTimeMs,
        @Nullable MedalTier medalTier
) {

    /** Sentinel value for {@link #completionTimeMs} indicating the player did not finish. */
    public static final long DNF_TIME = -1L;

    /**
     * Creates a fresh zero-valued score for a player. Completion time is {@link #DNF_TIME}
     * and the medal tier is {@link MedalTier#DNF} until the player completes the map.
     *
     * @param playerId the player to create a baseline score for
     * @return a zero score in DNF state
     */
    public static PlayerScore zero(UUID playerId) {
        return new PlayerScore(playerId, 0, 0, 0, DNF_TIME, MedalTier.DNF);
    }

    /**
     * Creates a "did not finish" score for the given player. All point fields are zero
     * and the medal tier is {@link MedalTier#DNF}.
     *
     * @param playerId the player who did not finish the map
     * @return a score marked as DNF
     */
    public static PlayerScore dnf(UUID playerId) {
        return new PlayerScore(playerId, 0, 0, 0, DNF_TIME, MedalTier.DNF);
    }

    /**
     * Returns a new {@code PlayerScore} with the given ring points added.
     *
     * @param points the ring points to add (may be negative for SLOW penalties)
     * @return a new score with updated ringPoints and totalPoints
     */
    public PlayerScore addRingPoints(int points) {
        int newRingPoints = ringPoints + points;
        int newTotal = newRingPoints + positionBonus;
        return new PlayerScore(playerId, newRingPoints, positionBonus, newTotal, completionTimeMs, medalTier);
    }

    /**
     * Returns a new {@code PlayerScore} with the given position bonus applied,
     * <strong>replacing</strong> any previous bonus. Use {@link #addBonusPoints(int)}
     * when bonuses should accumulate (e.g. bracket points + finishing position).
     *
     * @param bonus the position bonus to set
     * @return a new score with updated positionBonus and totalPoints
     */
    public PlayerScore withPositionBonus(int bonus) {
        int newTotal = ringPoints + bonus;
        return new PlayerScore(playerId, ringPoints, bonus, newTotal, completionTimeMs, medalTier);
    }

    /**
     * Returns a new {@code PlayerScore} with additional bonus points added on top of
     * the existing {@code positionBonus}. Used to layer time-bracket rewards and
     * finishing-position bonuses without overwriting one another.
     *
     * @param bonus the bonus points to add
     * @return a new score with the bonus accumulated
     */
    public PlayerScore addBonusPoints(int bonus) {
        int newPositionBonus = positionBonus + bonus;
        int newTotal = ringPoints + newPositionBonus;
        return new PlayerScore(playerId, ringPoints, newPositionBonus, newTotal, completionTimeMs, medalTier);
    }

    /**
     * Returns a new {@code PlayerScore} carrying the player's completion data.
     * Point fields are not modified — callers add bracket or finishing points
     * separately via {@link #addBonusPoints(int)}.
     *
     * @param timeMs the completion time in milliseconds; must be non-negative
     * @param tier   the medal tier earned by the player
     * @return a new score with completion time and medal tier set
     */
    public PlayerScore withCompletion(long timeMs, MedalTier tier) {
        return new PlayerScore(playerId, ringPoints, positionBonus, totalPoints, timeMs, tier);
    }
}
