package net.elytrarace.voyager.race.progress;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;
import net.elytrarace.voyager.race.collision.RingPass;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Advances a player's {@link RingProgress} by one tick.
 *
 * <p>Only {@code rings.get(current.passedCount())} — the next ring in order — is ever tested for a pass. That
 * single line is what makes strict ordering and "at most one ring per tick" fall out together, instead of
 * needing two separate guards that could disagree: a ring already passed is never tested again, and a ring
 * further ahead is never reachable before the ones in between.
 *
 * <p>A missed ring is detected beside that rule, never instead of it. When the racer crosses the ring after
 * the next one while the next one is still open, the next one is the ring missed. It is reported once per
 * run, by the memory in {@link RingProgress#lastMissedIndex()}.
 */
@ApiStatus.Internal
public abstract class ProgressTracker {

    private ProgressTracker() {
    }

    /**
     * Returns the progress after this tick's movement from {@code previous} to {@code position}.
     *
     * <p>Nothing changes — the same {@code current} progress, {@code null} passed ring and {@code null} missed ring —
     * when the player is not gliding, when {@code previous} is {@code null} (the first tick after a teleport has no
     * segment to test), or when every ring has already been passed.
     */
    public static ProgressUpdate advance(RingProgress current, List<Ring> rings, @Nullable Vec3 previous,
            Vec3 position, boolean gliding) {
        if (!gliding || previous == null || current.passedCount() >= rings.size()) {
            return new ProgressUpdate(current, null, null);
        }

        Ring next = rings.get(current.passedCount());
        if (!RingPass.crosses(previous, position, next)) {
            Ring missed = missedRing(current, rings, previous, position);
            if (missed == null) {
                return new ProgressUpdate(current, null, null);
            }
            return new ProgressUpdate(current.withLastMissedIndex(current.passedCount()), null, missed);
        }

        int lastCheckpointIndex = next.type() == RingType.CHECKPOINT
                ? next.index()
                : current.lastCheckpointIndex();
        RingProgress advanced = new RingProgress(current.passedCount() + 1, lastCheckpointIndex,
                current.lastMissedIndex());
        return new ProgressUpdate(advanced, next, null);
    }

    /**
     * The ring the racer skipped on this tick, or {@code null} if the tick skips none.
     *
     * <p>A skip is the crossing of the ring after the next one while the next one is still open. It is reported
     * only once for each next ring, so the memory of the last missed ring decides it.
     */
    @Nullable
    private static Ring missedRing(RingProgress current, List<Ring> rings, Vec3 previous, Vec3 position) {
        int afterNext = current.passedCount() + 1;
        if (afterNext >= rings.size() || current.lastMissedIndex() == current.passedCount()) {
            return null;
        }
        if (!RingPass.crosses(previous, position, rings.get(afterNext))) {
            return null;
        }
        return rings.get(current.passedCount());
    }
}
