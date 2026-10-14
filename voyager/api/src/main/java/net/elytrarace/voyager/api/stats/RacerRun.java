package net.elytrarace.voyager.api.stats;

import net.elytrarace.voyager.api.race.MedalTier;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * One human racer's run on one map, as stored. Computer racers never get one.
 *
 * @param playerId         the player's UUID
 * @param name             the player's name when the map finished
 * @param finishPosition   1-based place, or {@code null} when the racer did not finish
 * @param finishTimeMillis the finish time, present exactly when {@code finishPosition} is
 * @param ringPoints       points for the rings passed
 * @param placementBonus   points for the finish place
 * @param medalPoints      points for the medal
 * @param medalTier        the medal of the run, {@code DNF} when the racer did not finish
 * @param ringsPassed      rings passed, never negative
 * @param boosts           boosts accepted, never negative
 * @param resets           resets performed, never negative
 * @param flightMillis     time spent gliding, never negative
 * @param splitTicks       the game tick of each ring passed, in ring order
 */
public record RacerRun(UUID playerId, String name, @Nullable Integer finishPosition,
        @Nullable Integer finishTimeMillis, int ringPoints, int placementBonus, int medalPoints,
        MedalTier medalTier, int ringsPassed, int boosts, int resets, long flightMillis,
        List<Integer> splitTicks) {

    public RacerRun {
        Objects.requireNonNull(playerId, "player id must not be null");
        Objects.requireNonNull(medalTier, "medal tier must not be null");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("racer name must not be blank, was '%s'".formatted(name));
        }
        if ((finishPosition == null) != (finishTimeMillis == null)) {
            throw new IllegalArgumentException(
                    "a finish time is present exactly when a finish position is, was position %s and time %s"
                            .formatted(finishPosition, finishTimeMillis));
        }
        if (finishPosition != null && finishPosition < 1) {
            throw new IllegalArgumentException("finish position starts at 1, was %s".formatted(finishPosition));
        }
        nonNegative("finish time", finishTimeMillis);
        nonNegative("ring points", ringPoints);
        nonNegative("placement bonus", placementBonus);
        nonNegative("medal points", medalPoints);
        nonNegative("rings passed", ringsPassed);
        nonNegative("boosts", boosts);
        nonNegative("resets", resets);
        nonNegative("flight millis", flightMillis);
        splitTicks = List.copyOf(splitTicks);
        for (Integer tick : splitTicks) {
            nonNegative("split tick", tick);
        }
    }

    private static void nonNegative(String field, @Nullable Number value) {
        if (value != null && value.longValue() < 0) {
            throw new IllegalArgumentException("%s must not be negative, was %s".formatted(field, value));
        }
    }
}
