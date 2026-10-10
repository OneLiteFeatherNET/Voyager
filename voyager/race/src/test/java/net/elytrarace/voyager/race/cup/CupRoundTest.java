package net.elytrarace.voyager.race.cup;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.GameMode;
import net.elytrarace.voyager.api.race.GuideLine;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.MedalTier;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;
import net.elytrarace.voyager.race.progress.RingProgress;
import net.elytrarace.voyager.race.run.RaceRun;
import net.elytrarace.voyager.race.scoring.MapScore;
import net.elytrarace.voyager.race.scoring.MapScorer;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The cup's use-case facade: scoring a racer's run on a map, closing the map for placement, and reading the
 * board back. Pure: no Minestom, no clock, no shared state, so every test builds its own round.
 */
class CupRoundTest {

    private static final UUID ADA = UUID.nameUUIDFromBytes("Ada".getBytes(StandardCharsets.UTF_8));
    private static final UUID BEN = UUID.nameUUIDFromBytes("Ben".getBytes(StandardCharsets.UTF_8));

    private static final Duration RACE_LENGTH = Duration.ofSeconds(20);

    private static final MapDefinition MAP = new MapDefinition("two-ring", "world_a", Vec3.ZERO,
            List.of(ring(0, 9), ring(1, 25)), Duration.ofSeconds(60), new BoostConfig(12, 25),
            new GuideLine(List.of(), 2, 1.0));

    @Test
    void recordMapStoresTheScoreMapScorerGivesTheRun() {
        CupRound round = new CupRound();
        RaceRun run = passed(1);

        round.recordMap(0, ADA, run, MAP, RACE_LENGTH);

        assertThat(round.scoreOn(ADA, 0))
                .isEqualTo(MapScorer.score(run.progress(), MAP, run.timeOnCourse(RACE_LENGTH)));
    }

    @Test
    void recordMapScoresAnUnfinishedRunOnTheFullRaceLength() {
        CupRound round = new CupRound();

        round.recordMap(0, ADA, passed(1), MAP, RACE_LENGTH);

        MapScore score = round.scoreOn(ADA, 0);
        assertThat(score.medal()).isEqualTo(MedalTier.DNF);
        assertThat(score.completionTime()).isEmpty();
    }

    @Test
    void scoreOnIsNullForARacerWithNoRecordedMap() {
        CupRound round = new CupRound();

        assertThat(round.scoreOn(ADA, 0)).isNull();
    }

    @Test
    void closeMapAwardsTheFirstPlaceBonusToTheHigherScoringRacerInARankedCup() {
        CupRound round = new CupRound();
        round.recordMap(0, ADA, passed(2), MAP, RACE_LENGTH);
        round.recordMap(0, BEN, passed(1), MAP, RACE_LENGTH);

        round.closeMap(0, GameMode.RACE);

        assertThat(round.scoreOn(ADA, 0).placementBonus())
                .describedAs("the racer who passed more rings is first on the map")
                .isEqualTo(10);
        assertThat(round.scoreOn(BEN, 0).placementBonus()).isEqualTo(6);
    }

    @Test
    void closeMapAwardsNoPlacementBonusInAPracticeCup() {
        CupRound round = new CupRound();
        round.recordMap(0, ADA, passed(2), MAP, RACE_LENGTH);

        round.closeMap(0, GameMode.PRACTICE);

        assertThat(round.scoreOn(ADA, 0).placementBonus()).isZero();
    }

    @Test
    void resetDropsEveryRecordedScoreAndTheOrder() {
        CupRound round = new CupRound();
        round.recordMap(0, ADA, passed(2), MAP, RACE_LENGTH);

        round.reset();

        assertThat(round.scoreOn(ADA, 0)).isNull();
        assertThat(round.order()).isEmpty();
    }

    @Test
    void orderRanksTheRacerWithTheHigherCupTotalFirst() {
        CupRound round = new CupRound();
        round.recordMap(0, ADA, passed(1), MAP, RACE_LENGTH);
        round.recordMap(0, BEN, passed(2), MAP, RACE_LENGTH);

        assertThat(round.order()).extracting(CupStanding::playerId).containsExactly(BEN, ADA);
    }

    /** A run that has passed {@code rings} rings and not finished, with one game tick per ring. */
    private static RaceRun passed(int rings) {
        List<Integer> ticks = IntStream.rangeClosed(1, rings).map(tick -> tick * 4).boxed().toList();
        return new RaceRun(new RingProgress(rings, rings - 1), null, ticks, Optional.empty(), null);
    }

    private static Ring ring(int index, int points) {
        return new Ring(index, new Vec3(0, 64, 10 * (index + 1)), new Vec3(0, 0, 1), 6.0, points, RingType.STANDARD);
    }
}
