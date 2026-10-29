package net.elytrarace.voyager.race.standing;

import net.elytrarace.voyager.race.flow.RaceClock;

import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Every racer who started one map, in the order they stand: finishers by finish time, then the racers still racing, then
 * the racers who did not finish.
 *
 * <p>Computer racers are entries like any other, so they count in a place's total and in a gap. A finisher's place is the
 * place in this order, and a tie is broken by the earlier finish tick, which is what the clock records.
 *
 * @param entries the racers, in the order above
 */
public record MapStanding(List<Entry> entries) {

    /** Where a racer stands on the map: finished, still racing, or did not finish. */
    public enum Outcome {
        FINISHED,
        RACING,
        DID_NOT_FINISH
    }

    /**
     * One racer's result on the map.
     *
     * @param racer the racer's id
     * @param computer whether the racer is a computer racer
     * @param outcome how the racer stands
     * @param finishedAt the clock of the tick the racer finished on, present exactly when {@code outcome} is
     *     {@link Outcome#FINISHED}
     */
    public record Entry(UUID racer, boolean computer, Outcome outcome, @Nullable RaceClock finishedAt) {

        public Entry {
            if ((outcome == Outcome.FINISHED) != (finishedAt != null)) {
                throw new IllegalArgumentException(
                        "only a finished racer has a finish time, was %s with %s".formatted(outcome, finishedAt));
            }
        }
    }

    /**
     * A finisher's place on the map.
     *
     * @param place the place, counted from one
     * @param of how many racers started the map, every racer who did not finish included
     * @param rival the racer this place is measured against: the one directly ahead, or for first place the one directly
     *     behind; empty when there is none to measure against
     * @param gap the time between this racer and {@code rival}; empty exactly when {@code rival} is
     * @param rivalIsAhead whether {@code rival} is ahead of this racer
     */
    public record Place(int place, int of, Optional<UUID> rival, Optional<Duration> gap, boolean rivalIsAhead) {
    }

    /**
     * @param entries the racers, in the order {@link #of} gives them
     */
    public MapStanding {
        entries = List.copyOf(entries);
    }

    private static final Comparator<Entry> ORDER = Comparator.comparing(Entry::outcome)
            .thenComparing(entry -> entry.finishedAt() == null ? Duration.ZERO : entry.finishedAt().elapsed())
            .thenComparingInt(entry -> entry.finishedAt() == null ? 0 : entry.finishedAt().gameTick());

    /**
     * The standing of the given racers, sorted into the order a map's results are read in.
     *
     * @param racers the racers who started the map, in any order
     * @return the standing
     */
    public static MapStanding of(List<Entry> racers) {
        List<Entry> sorted = new ArrayList<>(racers);
        sorted.sort(ORDER);
        return new MapStanding(sorted);
    }

    /** How many racers started the map. */
    public int size() {
        return entries.size();
    }

    /**
     * The place of a finisher, with the racer it is measured against.
     *
     * @param racer the racer's id
     * @return the place, or empty when the racer did not finish or started no map
     */
    public Optional<Place> placeOf(UUID racer) {
        int index = indexOf(racer);
        if (index < 0 || entries.get(index).outcome() != Outcome.FINISHED) {
            return Optional.empty();
        }
        Entry entry = entries.get(index);
        int place = index + 1;
        if (place > 1) {
            Entry ahead = entries.get(index - 1);
            return Optional.of(new Place(place, size(), Optional.of(ahead.racer()),
                    Optional.of(difference(entry, ahead)), true));
        }
        if (entries.size() > 1 && entries.get(1).outcome() == Outcome.FINISHED) {
            Entry behind = entries.get(1);
            return Optional.of(new Place(place, size(), Optional.of(behind.racer()),
                    Optional.of(difference(entry, behind)), false));
        }
        return Optional.of(new Place(place, size(), Optional.empty(), Optional.empty(), false));
    }

    /**
     * How far a finisher is behind the leader.
     *
     * @param racer the racer's id
     * @return the gap, zero for the leader; empty for a racer who did not finish or is still racing
     */
    public Optional<Duration> gapToLeader(UUID racer) {
        int index = indexOf(racer);
        if (index < 0 || entries.get(index).outcome() != Outcome.FINISHED) {
            return Optional.empty();
        }
        return Optional.of(difference(entries.get(index), entries.getFirst()));
    }

    private int indexOf(UUID racer) {
        for (int index = 0; index < entries.size(); index++) {
            if (entries.get(index).racer().equals(racer)) {
                return index;
            }
        }
        return -1;
    }

    /** The time between two finishers, never negative. */
    private static Duration difference(Entry first, Entry second) {
        return first.finishedAt().elapsed().minus(second.finishedAt().elapsed()).abs();
    }
}
