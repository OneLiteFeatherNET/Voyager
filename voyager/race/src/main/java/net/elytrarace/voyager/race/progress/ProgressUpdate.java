package net.elytrarace.voyager.race.progress;

import net.elytrarace.voyager.api.race.Ring;

import org.jetbrains.annotations.Nullable;

/**
 * The result of one {@link ProgressTracker#advance} call: the progress after the tick, and the ring
 * that was passed on it, or {@code null} if none was.
 */
public record ProgressUpdate(RingProgress progress, @Nullable Ring passed) {
}
