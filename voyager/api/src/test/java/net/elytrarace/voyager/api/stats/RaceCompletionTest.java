package net.elytrarace.voyager.api.stats;

import net.elytrarace.voyager.api.race.GameMode;
import net.elytrarace.voyager.api.race.MedalTier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RaceCompletionTest {

    private static final Instant STARTED = Instant.parse("2026-10-13T10:00:00Z");
    private static final Instant FINISHED = Instant.parse("2026-10-13T10:02:00Z");

    @Test
    @DisplayName("a racer's counts are never negative")
    void racersCountsAreNeverNegative() {
        assertThatThrownBy(() -> run(UUID.randomUUID(), "Alex", 1, 61_200, -1, 0, 0, 0))
                .as("a negative ring count is refused")
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> run(UUID.randomUUID(), "Alex", 1, 61_200, 0, -1, 0, 0))
                .as("a negative boost count is refused")
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> run(UUID.randomUUID(), "Alex", 1, 61_200, 0, 0, -1, 0))
                .as("a negative reset count is refused")
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> run(UUID.randomUUID(), "Alex", 1, 61_200, 0, 0, 0, -1))
                .as("a negative flight time is refused")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a finish time is present exactly when a finish position is")
    void finishTimeIsPresentExactlyWhenFinishPositionIs() {
        UUID playerId = UUID.randomUUID();

        assertThatThrownBy(() -> new RacerRun(playerId, "Alex", 1, null, 0, 0, 0, MedalTier.DNF,
                0, 0, 0, 0L, List.of()))
                .as("a position without a time is refused")
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RacerRun(playerId, "Alex", null, 61_200, 0, 0, 0, MedalTier.DNF,
                0, 0, 0, 0L, List.of()))
                .as("a time without a position is refused")
                .isInstanceOf(IllegalArgumentException.class);

        RacerRun finished = run(playerId, "Alex", 1, 61_200, 3, 0, 0, 0);
        RacerRun unfinished = new RacerRun(playerId, "Alex", null, null, 0, 0, 0, MedalTier.DNF,
                0, 0, 0, 0L, List.of());

        assertThat(finished.finishTimeMillis()).isEqualTo(61_200);
        assertThat(unfinished.finishTimeMillis()).isNull();
    }

    @Test
    @DisplayName("a completion lists humans only and keeps the bot count")
    void completionListsHumansOnlyAndKeepsTheBotCount() {
        RaceCompletion completion = new RaceCompletion("harbour", "0123456789abcdef", GameMode.RACE, "cup-1",
                STARTED, FINISHED, 3, List.of(
                        run(UUID.randomUUID(), "Alex", 1, 61_200, 0, 0, 0, 0),
                        run(UUID.randomUUID(), "Sam", 2, 63_000, 0, 0, 0, 0)));

        assertThat(completion.racers()).hasSize(2);
        assertThat(completion.humanCount()).isEqualTo(2);
        assertThat(completion.botCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("a cup result's medal counts are never negative")
    void cupResultMedalCountsAreNeverNegative() {
        assertThatThrownBy(() -> new CupRacerResult(UUID.randomUUID(), "Alex", 1, 120, 2, 61_200,
                -1, 0, 0, 0))
                .as("a negative diamond count is refused")
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CupRacerResult(UUID.randomUUID(), "Alex", 1, 120, 2, 61_200,
                0, 0, 0, -1))
                .as("a negative bronze count is refused")
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static RacerRun run(UUID playerId, String name, int position, int timeMillis,
            int rings, int boosts, int resets, long flightMillis) {
        return new RacerRun(playerId, name, position, timeMillis, 0, 0, 0, MedalTier.FINISH,
                rings, boosts, resets, flightMillis, List.of());
    }
}
