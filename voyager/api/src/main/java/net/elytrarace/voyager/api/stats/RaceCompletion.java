package net.elytrarace.voyager.api.stats;

import net.elytrarace.voyager.api.race.GameMode;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * One finished map, handed to storage once. It lists the human racers who held a run; computer racers
 * are counted in {@link #botCount()} and nowhere else.
 *
 * @param mapKey      the map's name in its file, never blank
 * @param layoutVersion the layout identity of the map definition, never blank
 * @param mode        the game mode the map was played in
 * @param cupKey     the cup the map belonged to, or {@code null} for a map run alone
 * @param startedAt   when the map started
 * @param finishedAt  when the map ended; never before {@code startedAt}
 * @param botCount    the number of computer racers that ran, never negative
 * @param racers      one run per human racer who held one
 */
public record RaceCompletion(String mapKey, String layoutVersion, GameMode mode, @Nullable String cupKey,
        Instant startedAt, Instant finishedAt, int botCount, List<RacerRun> racers) {

    public RaceCompletion {
        if (mapKey == null || mapKey.isBlank()) {
            throw new IllegalArgumentException("map key must not be blank, was '%s'".formatted(mapKey));
        }
        if (layoutVersion == null || layoutVersion.isBlank()) {
            throw new IllegalArgumentException("layout version must not be blank");
        }
        Objects.requireNonNull(mode, "game mode must not be null");
        Objects.requireNonNull(startedAt, "start must not be null");
        Objects.requireNonNull(finishedAt, "finish must not be null");
        if (finishedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("a map cannot finish (%s) before it starts (%s)"
                    .formatted(finishedAt, startedAt));
        }
        if (botCount < 0) {
            throw new IllegalArgumentException("bot count must not be negative, was %s".formatted(botCount));
        }
        racers = List.copyOf(racers);
    }

    /**
     * The number of human racers in this completion.
     *
     * @return the size of {@link #racers()}
     */
    public int humanCount() {
        return racers.size();
    }
}
