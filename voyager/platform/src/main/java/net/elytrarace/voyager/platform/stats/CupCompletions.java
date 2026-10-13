package net.elytrarace.voyager.platform.stats;

import net.elytrarace.voyager.api.race.GameMode;
import net.elytrarace.voyager.api.race.MedalTier;
import net.elytrarace.voyager.api.stats.CupCompletion;
import net.elytrarace.voyager.api.stats.CupRacerResult;
import net.elytrarace.voyager.race.cup.CupStanding;
import net.elytrarace.voyager.race.scoring.MapScore;

import org.jetbrains.annotations.ApiStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Maps one finished cup to the {@link CupCompletion} the recorder stores. A pure function over the standing and
 * the map scores it is given: the place is the standing's order, and the medals are counted from each racer's own
 * map scores.
 */
@ApiStatus.Internal
public abstract class CupCompletions {

    private CupCompletions() {
    }

    /**
     * Builds the completion of one cup.
     *
     * @param cupKey the cup's name in its file
     * @param mode the game mode the cup was played in
     * @param startedAt when the cup started
     * @param finishedAt when the cup ended
     * @param standings the cup's standings, best first
     * @param mapScoresOf a racer's score on each map of the cup, in map order
     * @param nameOf a racer's name
     * @return the completion, one result per standing
     */
    public static CupCompletion of(String cupKey, GameMode mode, Instant startedAt, Instant finishedAt,
            List<CupStanding> standings, Function<UUID, List<MapScore>> mapScoresOf,
            Function<UUID, String> nameOf) {
        List<CupRacerResult> racers = new ArrayList<>();
        for (int index = 0; index < standings.size(); index++) {
            CupStanding standing = standings.get(index);
            UUID playerId = standing.playerId();
            List<MapScore> scores = mapScoresOf.apply(playerId);
            Integer bestTime = standing.score().bestTime().map(CupCompletions::millis).orElse(null);
            racers.add(new CupRacerResult(playerId, nameOf.apply(playerId), index + 1,
                    standing.score().totalPoints(), standing.score().mapsFinished(), bestTime,
                    countOf(scores, MedalTier.DIAMOND), countOf(scores, MedalTier.GOLD),
                    countOf(scores, MedalTier.SILVER), countOf(scores, MedalTier.BRONZE)));
        }
        return new CupCompletion(cupKey, mode, startedAt, finishedAt, racers);
    }

    private static int countOf(List<MapScore> scores, MedalTier tier) {
        return (int) scores.stream().filter(score -> score.medal() == tier).count();
    }

    private static int millis(Duration time) {
        return Math.toIntExact(time.toMillis());
    }
}
