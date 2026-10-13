package net.elytrarace.voyager.api.stats;

import net.elytrarace.voyager.api.race.GameMode;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * One finished cup, handed to storage once.
 *
 * @param cupKey     the cup's name in its file, never blank
 * @param mode       the game mode the cup was played in
 * @param startedAt  when the cup started
 * @param finishedAt when the cup ended; never before {@code startedAt}
 * @param racers     one result per human racer
 */
public record CupCompletion(String cupKey, GameMode mode, Instant startedAt, Instant finishedAt,
        List<CupRacerResult> racers) {

    public CupCompletion {
        if (cupKey == null || cupKey.isBlank()) {
            throw new IllegalArgumentException("cup key must not be blank, was '%s'".formatted(cupKey));
        }
        Objects.requireNonNull(mode, "game mode must not be null");
        Objects.requireNonNull(startedAt, "start must not be null");
        Objects.requireNonNull(finishedAt, "finish must not be null");
        if (finishedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("a cup cannot finish (%s) before it starts (%s)"
                    .formatted(finishedAt, startedAt));
        }
        racers = List.copyOf(racers);
    }
}
