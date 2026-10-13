package net.elytrarace.voyager.platform.stats;

import net.elytrarace.voyager.api.race.GameMode;
import net.elytrarace.voyager.api.stats.RaceCompletion;
import net.elytrarace.voyager.api.stats.RacerRun;
import net.elytrarace.voyager.race.scoring.MapScore;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Maps one finished map's race values to the {@link RaceCompletion} the recorder stores. A pure function: it
 * reads the values it is given and touches nothing else.
 *
 * <p>Computer racers are left out of the racers and counted in the bot count. A finisher's place is their
 * rank by finish time on this map, the faster first; a racer who did not finish has no place and no finish
 * time. The medal, the points and the finish time are the map score's own, never recomputed here.
 */
@ApiStatus.Internal
public abstract class RaceCompletions {

    private RaceCompletions() {
    }

    /**
     * Builds the completion of one map.
     *
     * @param mapKey the map's name in its file
     * @param layoutVersion the layout identity of the map definition
     * @param mode the game mode the map was played in
     * @param cupKey the cup the map belonged to, or {@code null} for a map run alone
     * @param startedAt when the map's {@code GAME} phase began
     * @param finishedAt when the map ended
     * @param participants every racer who held a run on the map, humans and computer racers
     * @return the completion, with the human racers only
     */
    public static RaceCompletion of(String mapKey, String layoutVersion, GameMode mode, @Nullable String cupKey,
            Instant startedAt, Instant finishedAt, List<RaceParticipant> participants) {
        List<RaceParticipant> humans = participants.stream().filter(racer -> !racer.computer()).toList();
        int bots = participants.size() - humans.size();
        Map<UUID, Integer> places = placesOf(humans);
        List<RacerRun> racers = humans.stream()
                .map(racer -> racerRun(racer, places.get(racer.playerId())))
                .toList();
        return new RaceCompletion(mapKey, layoutVersion, mode, cupKey, startedAt, finishedAt, bots, racers);
    }

    /**
     * The place of every finisher among the humans: rank by finish time, first is 1. A racer who did not
     * finish has no place.
     */
    private static Map<UUID, Integer> placesOf(List<RaceParticipant> humans) {
        List<RaceParticipant> finishers = humans.stream()
                .filter(racer -> racer.score().completionTime().isPresent())
                .sorted(Comparator.comparing((RaceParticipant racer) -> racer.score().completionTime().orElseThrow()))
                .toList();
        Map<UUID, Integer> places = new HashMap<>();
        for (int index = 0; index < finishers.size(); index++) {
            places.put(finishers.get(index).playerId(), index + 1);
        }
        return places;
    }

    private static RacerRun racerRun(RaceParticipant racer, @Nullable Integer place) {
        MapScore score = racer.score();
        Integer finishTime = score.completionTime().map(RaceCompletions::millis).orElse(null);
        return new RacerRun(racer.playerId(), racer.name(), place, finishTime, score.ringPoints(),
                score.placementBonus(), score.medalPoints(), score.medal(), racer.run().progress().passedCount(),
                racer.boosts(), racer.resets(), racer.flightTime().toMillis(), racer.run().passedOnGameTick());
    }

    private static int millis(Duration time) {
        return Math.toIntExact(time.toMillis());
    }
}
