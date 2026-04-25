package net.elytrarace.server.scoring;

import net.elytrarace.common.game.mode.GameMode;
import net.elytrarace.common.game.scoring.MedalBrackets;
import net.elytrarace.server.physics.Ring;
import org.jetbrains.annotations.Unmodifiable;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Strategy for tracking and computing player scores within a single map.
 *
 * <p>The {@link GameMode} returned by {@link #mode()} determines which scoring rules
 * apply: {@code RACE} uses time brackets and finishing-position bonuses, {@code PRACTICE}
 * only classifies medals without competitive rewards.
 *
 * <p>Implementations are expected to be thread-safe — callers may invoke ring
 * notifications from gameplay systems while another thread reads the ranking.
 */
public sealed interface ScoringStrategy permits BaseScoringStrategy {

    /**
     * Records that the given player passed through a ring during the active map.
     *
     * @param playerId the player who passed through the ring
     * @param ring     the ring that was passed through
     */
    void onRingPassed(UUID playerId, Ring ring);

    /**
     * Records that the given player has completed the active map. The strategy
     * classifies the player's time against the supplied brackets and reference
     * duration and updates the player's score accordingly.
     *
     * @param playerId  the player who completed the map
     * @param elapsedMs the wall-clock time the player needed to finish, in milliseconds
     * @param brackets  the time-bracket multipliers for the map
     * @param reference the reference duration the brackets are evaluated against
     *                  (typically the map's target time)
     */
    void onMapCompleted(UUID playerId, long elapsedMs, MedalBrackets brackets, Duration reference);

    /**
     * Returns the current score for the given player. If the player has no
     * recorded score, a zero-valued score is returned.
     *
     * @param playerId the player to look up
     * @return the player's current score
     */
    PlayerScore getScore(UUID playerId);

    /**
     * Returns all player scores sorted by {@code totalPoints} in descending order.
     *
     * @return an unmodifiable ranked list of player scores
     */
    @Unmodifiable List<PlayerScore> getRanking();

    /**
     * Applies end-of-map result modifiers. In race mode this awards finishing-position
     * bonuses on top of the bracket points already credited in
     * {@link #onMapCompleted(UUID, long, MedalBrackets, Duration)}. In practice mode
     * this is a no-op because there is no competitive ranking.
     */
    void applyMapResults();

    /**
     * Resets all scores. Intended to be called between maps within a cup.
     */
    void reset();

    /**
     * Returns the {@link GameMode} this strategy implements.
     *
     * @return the game mode
     */
    GameMode mode();
}
