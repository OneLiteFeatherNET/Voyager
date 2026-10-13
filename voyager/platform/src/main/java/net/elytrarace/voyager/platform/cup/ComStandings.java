package net.elytrarace.voyager.platform.cup;

import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The computer racers' finishes, for the final standings: kept beside the cup round, never in it.
 *
 * <p>A computer racer is ranked and shown, and that is all. It takes no points, no placement bonus and no medal, and
 * nothing about it is stored: {@link CupRound} is for humans, so this holds only the number of each seat, how many maps
 * it finished and its fastest finish. It is cleared with the cup.
 *
 * <p>The number of a competitor is its place in the list the field returns on its first map. The field returns its
 * seats in seat order, and numbers them from one, so the place is the seat's number.
 */
final class ComStandings {

    private final Map<UUID, Standing> byId = new LinkedHashMap<>();

    /** Registers the competitors a map starts with. A competitor keeps the number it was first given. */
    void seat(List<UUID> prepared) {
        for (int index = 0; index < prepared.size(); index++) {
            byId.putIfAbsent(prepared.get(index), new Standing(index + 1, 0, null));
        }
    }

    /**
     * Records that {@code id} finished a map in {@code time}.
     *
     * @return the competitor's standing after the finish, or {@code null} if the competitor was never seated
     */
    @Nullable Standing finished(UUID id, Duration time) {
        Standing before = byId.get(id);
        if (before == null) {
            return null;
        }
        Standing after = before.finishedIn(time);
        byId.put(id, after);
        return after;
    }

    /** The standings of the competitors that finished at least one map, in seat order. */
    List<Standing> order() {
        return byId.values().stream().filter(standing -> standing.mapsFinished() > 0).toList();
    }

    /** Drops everything: a fresh cup, not a continuation of the last one. */
    void clear() {
        byId.clear();
    }

    /**
     * One computer racer's finished maps.
     *
     * @param number the racer's number, so it is shown as {@code COM number}
     * @param mapsFinished how many maps it has finished in this cup
     * @param bestTime its fastest finish, absent until it has finished a map
     */
    record Standing(int number, int mapsFinished, @Nullable Duration bestTime) {

        Standing finishedIn(Duration time) {
            Duration best = bestTime == null || time.compareTo(bestTime) < 0 ? time : bestTime;
            return new Standing(number, mapsFinished + 1, best);
        }
    }
}
