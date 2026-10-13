package net.elytrarace.voyager.api.stats;

import java.time.Instant;
import java.util.UUID;

/**
 * One line of a map's leaderboard.
 *
 * @param rank           1-based position, fastest first
 * @param playerId       the player's UUID
 * @param name           the player's last known name
 * @param bestTimeMillis the player's fastest finish on the map, never negative
 * @param achievedAt     when that finish happened
 */
public record LeaderboardEntry(int rank, UUID playerId, String name, int bestTimeMillis, Instant achievedAt) {

    public LeaderboardEntry {
        if (rank < 1) {
            throw new IllegalArgumentException("rank starts at 1, was %s".formatted(rank));
        }
        if (playerId == null) {
            throw new IllegalArgumentException("player id must not be null");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank, was '%s'".formatted(name));
        }
        if (bestTimeMillis < 0) {
            throw new IllegalArgumentException("best time must not be negative, was %s".formatted(bestTimeMillis));
        }
    }
}
