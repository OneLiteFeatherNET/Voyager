package net.elytrarace.voyager.platform.stats;

import net.elytrarace.voyager.api.race.GameMode;
import net.elytrarace.voyager.api.race.MedalTier;
import net.elytrarace.voyager.api.stats.CupCompletion;
import net.elytrarace.voyager.api.stats.CupRacerResult;
import net.elytrarace.voyager.race.cup.CupStanding;
import net.elytrarace.voyager.race.scoring.CupScore;
import net.elytrarace.voyager.race.scoring.MapScore;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The cup's completion, built from a standing and each racer's map scores. No round, no server: the scores are
 * given directly, so each medal tier is counted from its own value and no tier is a copy of another.
 */
class CupCompletionsTest {

    private static final Instant START = Instant.parse("2026-10-13T10:00:00Z");
    private static final Instant END = START.plusSeconds(600);
    private static final UUID LEADER = UUID.fromString("00000000-0000-0000-0000-00000000c001");
    private static final UUID TRAILER = UUID.fromString("00000000-0000-0000-0000-00000000c002");

    @Test
    void aCupResultCountsTheMedalsOfEachTier() {
        List<MapScore> scores = List.of(
                score(MedalTier.DIAMOND, 1_000),
                score(MedalTier.GOLD, 2_000),
                score(MedalTier.GOLD, 2_500),
                score(MedalTier.BRONZE, 3_000),
                score(MedalTier.DNF, null));
        CupCompletion completion = completionOf(List.of(new CupStanding(LEADER, new CupScore(200, Optional.of(
                Duration.ofMillis(1_000)), 4, 0))), scores);

        CupRacerResult result = completion.racers().get(0);

        assertThat(result.diamonds()).as("diamond medals").isEqualTo(1);
        assertThat(result.golds()).as("gold medals").isEqualTo(2);
        assertThat(result.silvers()).as("silver medals").isZero();
        assertThat(result.bronzes()).as("bronze medals").isEqualTo(1);
    }

    @Test
    void aCupResultHasNoBestTimeWhenNoMapWasFinished() {
        CupCompletion completion = completionOf(List.of(new CupStanding(TRAILER, new CupScore(10, Optional.empty(),
                0, 0))), List.of(score(MedalTier.DNF, null)));

        assertThat(completion.racers().get(0).bestTimeMillis()).as("best time without a finish").isNull();
    }

    @Test
    void aCupResultIsPlacedInTheOrderOfTheStanding() {
        CupCompletion completion = completionOf(List.of(
                new CupStanding(LEADER, new CupScore(300, Optional.of(Duration.ofMillis(900)), 2, 0)),
                new CupStanding(TRAILER, new CupScore(120, Optional.empty(), 1, 0))),
                List.of(score(MedalTier.SILVER, 900)));

        assertThat(completion.racers()).as("racers in standing order").extracting(CupRacerResult::playerId)
                .containsExactly(LEADER, TRAILER);
        assertThat(completion.racers()).as("final positions").extracting(CupRacerResult::finalPosition)
                .containsExactly(1, 2);
    }

    /** A completion of one cup with the given standings, where every racer has the given map scores. */
    private static CupCompletion completionOf(List<CupStanding> standings, List<MapScore> scores) {
        return CupCompletions.of("tour", GameMode.RACE, START, END, standings, id -> scores,
                id -> "racer-" + id.getLeastSignificantBits());
    }

    private static MapScore score(MedalTier tier, Integer timeMillis) {
        return new MapScore(0, tier.medalPoints(), 0,
                timeMillis == null ? Optional.empty() : Optional.of(Duration.ofMillis(timeMillis)), tier);
    }
}
