package net.elytrarace.voyager.platform.stats;

import net.elytrarace.voyager.api.race.GameMode;
import net.elytrarace.voyager.api.race.MedalTier;
import net.elytrarace.voyager.api.stats.RaceCompletion;
import net.elytrarace.voyager.api.stats.RacerRun;
import net.elytrarace.voyager.race.flow.RaceClock;
import net.elytrarace.voyager.race.progress.RingProgress;
import net.elytrarace.voyager.race.run.RaceRun;
import net.elytrarace.voyager.race.scoring.MapScore;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The completion of one map, built from hand-made race values: no server, no player, no clock.
 *
 * <p>The fixtures are built so that a mapping which reads the wrong value cannot pass by accident. The
 * split ticks are distinct and not the ring indices; the ring points of the score are not what the run
 * would compute from its rings; and the two racers in the mixed completion differ in every field.
 */
class RaceCompletionsTest {

    private static final Duration STEP = Duration.ofMillis(50);
    private static final String MAP = "arena-run";
    private static final String LAYOUT = "0123456789abcdef";
    private static final String CUP = "tour";
    private static final Instant START = Instant.parse("2026-10-13T10:00:00Z");
    private static final Instant END = START.plusSeconds(60);

    private static final UUID FINISHER = UUID.fromString("00000000-0000-0000-0000-00000000f001");
    private static final UUID DROPOUT = UUID.fromString("00000000-0000-0000-0000-00000000d002");
    private static final UUID BOT_ID = UUID.fromString("00000000-0000-0000-0000-00000000b003");

    @Test
    void aFinishedRacersSplitsAreTheRingTicksInRingOrder() {
        RaceRun run = finished(5, 9, 14);
        MapScore score = new MapScore(30, 30, 0, Optional.of(Duration.ofMillis(700)), MedalTier.SILVER);

        RacerRun racer = onlyRacer(human(FINISHER, "Finisher", run, score));

        assertThat(racer.splitTicks()).as("split ticks of a finished racer").containsExactly(5, 9, 14);
    }

    @Test
    void aRacerWhoDidNotFinishHasNoFinishTimeAndTheDnfMedal() {
        RaceRun run = unfinished(4);
        MapScore score = new MapScore(10, 0, 0, Optional.empty(), MedalTier.DNF);

        RacerRun racer = onlyRacer(human(DROPOUT, "Dropout", run, score));

        assertThat(racer.finishTimeMillis()).as("finish time of a racer who did not finish").isNull();
        assertThat(racer.finishPosition()).as("finish position of a racer who did not finish").isNull();
        assertThat(racer.medalTier()).as("medal of a racer who did not finish").isEqualTo(MedalTier.DNF);
    }

    @Test
    void aComputerRacerIsNotInTheCompletion() {
        RaceRun humanRun = finished(5, 9, 14);
        RaceRun botRun = finished(6, 10, 15);
        MapScore score = new MapScore(30, 45, 0, Optional.of(Duration.ofMillis(700)), MedalTier.GOLD);

        RaceCompletion completion = RaceCompletions.of(MAP, LAYOUT, GameMode.RACE, CUP, START, END, List.of(
                human(FINISHER, "Finisher", humanRun, score),
                computer(BOT_ID, "Computer", botRun, score)));

        assertThat(completion.racers()).as("racers listed in the completion").extracting(RacerRun::playerId)
                .containsExactly(FINISHER);
        assertThat(completion.botCount()).as("computer racers counted as context").isEqualTo(1);
        assertThat(completion.humanCount()).as("human racers in the completion").isEqualTo(1);
    }

    @Test
    void theMedalAndThePointsComeFromTheMapScore() {
        RaceRun run = finished(5, 9);
        MapScore score = new MapScore(77, 45, 3, Optional.of(Duration.ofMillis(12_345)), MedalTier.GOLD);

        RacerRun racer = onlyRacer(human(FINISHER, "Finisher", run, score));

        assertThat(racer.medalTier()).as("medal tier").isEqualTo(MedalTier.GOLD);
        assertThat(racer.ringPoints()).as("ring points").isEqualTo(77);
        assertThat(racer.medalPoints()).as("medal points").isEqualTo(45);
        assertThat(racer.placementBonus()).as("placement bonus").isEqualTo(3);
        assertThat(racer.finishTimeMillis()).as("finish time in milliseconds").isEqualTo(12_345);
    }

    @Test
    void finishersArePlacedByFinishTimeAndNotByTheOrderTheyAreListed() {
        RaceRun slow = finished(5, 9);
        RaceRun fast = finished(4, 8);
        MapScore slowScore = new MapScore(20, 30, 0, Optional.of(Duration.ofMillis(800)), MedalTier.SILVER);
        MapScore fastScore = new MapScore(20, 30, 0, Optional.of(Duration.ofMillis(700)), MedalTier.SILVER);

        RaceCompletion completion = RaceCompletions.of(MAP, LAYOUT, GameMode.RACE, CUP, START, END, List.of(
                human(FINISHER, "Slow", slow, slowScore),
                human(BOT_ID, "Fast", fast, fastScore)));

        assertThat(racerWith(completion, BOT_ID).finishPosition()).as("place of the faster finisher").isEqualTo(1);
        assertThat(racerWith(completion, FINISHER).finishPosition()).as("place of the slower finisher").isEqualTo(2);
    }

    private static RacerRun onlyRacer(RaceParticipant participant) {
        RaceCompletion completion = RaceCompletions.of(MAP, LAYOUT, GameMode.RACE, CUP, START, END,
                List.of(participant));
        assertThat(completion.racers()).as("racers in the completion").hasSize(1);
        return completion.racers().get(0);
    }

    private static RacerRun racerWith(RaceCompletion completion, UUID playerId) {
        return completion.racers().stream()
                .filter(racer -> racer.playerId().equals(playerId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no racer %s in the completion".formatted(playerId)));
    }

    private static RaceParticipant human(UUID playerId, String name, RaceRun run, MapScore score) {
        return new RaceParticipant(playerId, name, false, run, score, 0, 0, Duration.ZERO);
    }

    private static RaceParticipant computer(UUID playerId, String name, RaceRun run, MapScore score) {
        return new RaceParticipant(playerId, name, true, run, score, 0, 0, Duration.ZERO);
    }

    /** A run that passed every ring it was given, on the ticks given, and finished on the last of them. */
    private static RaceRun finished(int... ticks) {
        List<Integer> passed = Arrays.stream(ticks).boxed().toList();
        return new RaceRun(new RingProgress(ticks.length, ticks.length - 1), null, passed,
                Optional.of(new RaceClock(ticks[ticks.length - 1], STEP)), null);
    }

    /** A run that passed the given number of rings on the ticks 1, 2, ... and did not finish. */
    private static RaceRun unfinished(int ringsPassed) {
        List<Integer> passed = IntStream.rangeClosed(1, ringsPassed).boxed().toList();
        return new RaceRun(new RingProgress(ringsPassed, ringsPassed - 1), null, passed, Optional.empty(), null);
    }
}
