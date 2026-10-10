package net.elytrarace.voyager.race.cup;

import net.elytrarace.voyager.api.race.GameMode;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.race.run.RaceRun;
import net.elytrarace.voyager.race.scoring.MapScore;
import net.elytrarace.voyager.race.scoring.MapScorer;

import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * The use cases of one cup being played, with no platform in them: score a racer's run on a map, close the
 * map for placement, and read the board back.
 *
 * <p>The platform adapter holds one round per cup and calls it from the tick. A round does not read a clock,
 * log, send a message or touch a player; it is given the values it scores.
 */
public final class CupRound {

    private final CupStandings standings = new CupStandings();

    /**
     * Scores {@code run} on {@code map} and records it under {@code mapIndex}, replacing any earlier record for
     * that racer and map.
     *
     * @param raceLength the length the map's race phase was played with; a run that did not finish is scored on it
     */
    public void recordMap(int mapIndex, UUID playerId, RaceRun run, MapDefinition map, Duration raceLength) {
        MapScore score = MapScorer.score(run.progress(), map, run.timeOnCourse(raceLength));
        standings.record(playerId, mapIndex, score);
    }

    /** Awards the placement bonus of the cup's mode across everyone with a score on {@code mapIndex}. */
    public void closeMap(int mapIndex, GameMode mode) {
        standings.closeMap(mapIndex, mode);
    }

    /** {@code playerId}'s score on {@code mapIndex}, or {@code null} if they have none. */
    public @Nullable MapScore scoreOn(UUID playerId, int mapIndex) {
        return standings.scoreOn(playerId, mapIndex);
    }

    /** {@code playerId}'s scores, one per map they have a result on, in map order. */
    public List<MapScore> of(UUID playerId) {
        return standings.of(playerId);
    }

    /** The cup standings, best first. */
    public List<CupStanding> order() {
        return standings.cupOrder();
    }

    /** Drops everything: a fresh cup, not a continuation of the last one. */
    public void reset() {
        standings.clear();
    }
}
