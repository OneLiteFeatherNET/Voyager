package net.elytrarace.voyager.race.progress;

import net.elytrarace.voyager.api.race.Ring;

import org.jetbrains.annotations.Nullable;

/**
 * The result of one {@link ProgressTracker#advance} call: the progress after the tick, the ring that
 * was passed on it (or {@code null} if none was), and the ring that was missed on it (or {@code null}
 * if none was). A missed ring is one the racer skipped by passing the ring after it first.
 */
public record ProgressUpdate(RingProgress progress, @Nullable Ring passed, @Nullable Ring missed) {

    /** An update that passes no ring and misses none. */
    public ProgressUpdate(RingProgress progress, @Nullable Ring passed) {
        this(progress, passed, null);
    }
}
