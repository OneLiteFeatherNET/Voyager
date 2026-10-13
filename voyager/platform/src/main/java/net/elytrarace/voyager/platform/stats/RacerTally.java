package net.elytrarace.voyager.platform.stats;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What each racer did on the map being raced and that the race values do not already hold: boosts accepted,
 * resets performed and gliding ticks. One tally belongs to one cup, and {@link #clear()} is called when the next
 * map starts, so a count never carries from one map into the next.
 *
 * <p>It is written from the tick thread only and is not thread-safe, like the flight tracker it observes beside.
 * It counts what it is told; the caller decides what counts as a boost or a reset.
 */
public final class RacerTally {

    private final Duration step;
    private final Map<UUID, Counts> byRacer = new HashMap<>();

    /**
     * @param step the wall-clock duration one gliding tick stands for; 50 ms on a 20 TPS server
     */
    public RacerTally(Duration step) {
        this.step = step;
    }

    /** Counts one boost the racer was given. Called once per accepted boost. */
    public void recordBoost(UUID playerId) {
        counts(playerId).boosts++;
    }

    /** Counts one reset the racer was sent back by. */
    public void recordReset(UUID playerId) {
        counts(playerId).resets++;
    }

    /** Counts one tick on which the racer was gliding. */
    public void recordGlidingTick(UUID playerId) {
        counts(playerId).glidingTicks++;
    }

    /** The boosts the racer had accepted on this map. */
    public int boosts(UUID playerId) {
        return read(playerId).boosts;
    }

    /** The resets the racer had on this map. */
    public int resets(UUID playerId) {
        return read(playerId).resets;
    }

    /** The ticks on which the racer was gliding on this map. */
    public int glidingTicks(UUID playerId) {
        return read(playerId).glidingTicks;
    }

    /** The time the racer spent gliding on this map: the gliding ticks times one step. */
    public Duration flightTime(UUID playerId) {
        return step.multipliedBy(glidingTicks(playerId));
    }

    /** Drops every count, for every racer: the next map starts from nothing. */
    public void clear() {
        byRacer.clear();
    }

    private Counts counts(UUID playerId) {
        return byRacer.computeIfAbsent(playerId, id -> new Counts());
    }

    /** The counters of a racer, all zero for a racer nothing was counted for. Never creates an entry. */
    private Counts read(UUID playerId) {
        Counts held = byRacer.get(playerId);
        return held == null ? new Counts() : held;
    }

    /** The three counters of one racer. */
    private static final class Counts {
        private int boosts;
        private int resets;
        private int glidingTicks;
    }
}
