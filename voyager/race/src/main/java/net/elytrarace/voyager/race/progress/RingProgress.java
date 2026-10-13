package net.elytrarace.voyager.race.progress;

/**
 * How far a player has advanced through a map's rings: how many have been passed, the index of the
 * last {@code CHECKPOINT} ring reached (or {@code -1} if none has been), and the index of the ring
 * last reported as missed (or {@code -1} if none has been).
 *
 * <p>The missed-ring index is the memory that makes a skipped ring reported once per run. It is the
 * index of the ring that was still expected when the racer flew through the one after it, so a
 * second crossing of the same pair does not report it again, and a reset keeps it.
 */
public record RingProgress(int passedCount, int lastCheckpointIndex, int lastMissedIndex) {

    public RingProgress {
        if (passedCount < 0) {
            throw new IllegalArgumentException(
                    "passedCount must not be negative, was %d".formatted(passedCount));
        }
        if (lastCheckpointIndex < -1) {
            throw new IllegalArgumentException(
                    "lastCheckpointIndex must not be below -1, was %d".formatted(lastCheckpointIndex));
        }
        if (lastMissedIndex < -1) {
            throw new IllegalArgumentException(
                    "lastMissedIndex must not be below -1, was %d".formatted(lastMissedIndex));
        }
    }

    /** A progress that has passed {@code passedCount} rings and has no missed ring on record. */
    public RingProgress(int passedCount, int lastCheckpointIndex) {
        this(passedCount, lastCheckpointIndex, -1);
    }

    /** The state of a player who has not yet passed any ring. */
    public static RingProgress atStart() {
        return new RingProgress(0, -1);
    }

    /** The same progress, with the missed-ring memory set to {@code index}. */
    public RingProgress withLastMissedIndex(int index) {
        return new RingProgress(passedCount, lastCheckpointIndex, index);
    }
}
