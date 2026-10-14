package net.elytrarace.voyager.platform.stats;

import net.elytrarace.voyager.race.run.RaceRun;
import net.elytrarace.voyager.race.scoring.MapScore;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/**
 * One racer's values on one finished map, as the platform hands them to {@link RaceCompletions}.
 *
 * <p>A computer racer is described with {@code computer} set, so that the mapping can leave it out of the
 * completion and count it instead. The cup builds one for each computer racer that ran a map, and the human
 * participants for the humans, so the bot count is the number of computer participants.
 *
 * @param playerId the player's UUID
 * @param name the player's name when the map finished
 * @param computer whether the racer is a computer racer
 * @param run the run the racer held on the map
 * @param score the racer's score on the map, after the placement bonus was awarded
 * @param boosts boosts the racer had accepted on the map
 * @param resets resets the racer had on the map
 * @param flightTime the time the racer spent gliding on the map
 */
public record RaceParticipant(UUID playerId, String name, boolean computer, RaceRun run, MapScore score,
        int boosts, int resets, Duration flightTime) {

    public RaceParticipant {
        Objects.requireNonNull(playerId, "player id must not be null");
        Objects.requireNonNull(run, "run must not be null");
        Objects.requireNonNull(score, "score must not be null");
        Objects.requireNonNull(flightTime, "flight time must not be null");
    }
}
