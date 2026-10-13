package net.elytrarace.voyager.api.stats;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * One human racer's result over a whole cup, as stored.
 *
 * @param playerId      the player's UUID
 * @param name          the player's name when the cup finished
 * @param finalPosition 1-based place in the cup
 * @param totalPoints   points summed over the cup's maps, never negative
 * @param mapsFinished  maps the racer finished, never negative
 * @param bestTimeMillis the fastest finish on any map of the cup, or {@code null} when none was finished
 * @param diamonds      diamond medals, never negative
 * @param golds         gold medals, never negative
 * @param silvers       silver medals, never negative
 * @param bronzes       bronze medals, never negative
 */
public record CupRacerResult(UUID playerId, String name, int finalPosition, int totalPoints, int mapsFinished,
        @Nullable Integer bestTimeMillis, int diamonds, int golds, int silvers, int bronzes) {

    public CupRacerResult {
        if (playerId == null) {
            throw new IllegalArgumentException("player id must not be null");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("racer name must not be blank, was '%s'".formatted(name));
        }
        if (finalPosition < 1) {
            throw new IllegalArgumentException("final position starts at 1, was %s".formatted(finalPosition));
        }
        nonNegative("total points", totalPoints);
        nonNegative("maps finished", mapsFinished);
        nonNegative("best time", bestTimeMillis);
        nonNegative("diamond medals", diamonds);
        nonNegative("gold medals", golds);
        nonNegative("silver medals", silvers);
        nonNegative("bronze medals", bronzes);
    }

    private static void nonNegative(String field, @Nullable Number value) {
        if (value != null && value.longValue() < 0) {
            throw new IllegalArgumentException("%s must not be negative, was %s".formatted(field, value));
        }
    }
}
