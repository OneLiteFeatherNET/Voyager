package net.elytrarace.voyager.race.scoring;

import java.time.Duration;
import java.util.Optional;

/**
 * A player's standing across a whole cup: total points summed over every map, the best completion
 * time among the maps they actually finished, and how many maps that was.
 *
 * <p>{@code bestTime} is an {@link Optional} rather than the old tree's {@code -1} sentinel for "no
 * time set" — a player who did not finish a single map genuinely has no best time, and
 * {@link Optional#empty()} says that directly instead of asking every reader of this field to
 * remember what {@code -1} means.
 */
public record CupScore(int totalPoints, Optional<Duration> bestTime, int mapsFinished) {
}
