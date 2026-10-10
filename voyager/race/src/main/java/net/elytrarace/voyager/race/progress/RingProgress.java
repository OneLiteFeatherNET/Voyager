package net.elytrarace.voyager.race.progress;

/**
 * How far a player has advanced through a map's rings: how many have been passed, and the index of
 * the last {@code CHECKPOINT} ring reached, or {@code -1} if none has been.
 */
public record RingProgress(int passedCount, int lastCheckpointIndex) {

    public RingProgress {
        if (passedCount < 0) {
            throw new IllegalArgumentException(
                    "passedCount must not be negative, was %d".formatted(passedCount));
        }
        if (lastCheckpointIndex < -1) {
            throw new IllegalArgumentException(
                    "lastCheckpointIndex must not be below -1, was %d".formatted(lastCheckpointIndex));
        }
    }

    /** The state of a player who has not yet passed any ring. */
    public static RingProgress atStart() {
        return new RingProgress(0, -1);
    }
}
