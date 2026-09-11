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
 * <p>Only {@code rings.get(current.passedCount())} — the next ring in order — is ever tested. That
 * single line is what makes strict ordering and "at most one ring per tick" fall out together,
 * instead of needing two separate guards that could disagree: a ring already passed is never tested
 * again, and a ring further ahead is never reachable before the ones in between.
 */
@ApiStatus.Internal
public abstract class ProgressTracker {

    private ProgressTracker() {
    }

    /**
     * Returns the progress after this tick's movement from {@code previous} to {@code position}.
     *
     * <p>Nothing changes — the same {@code current} progress, {@code null} passed ring — when the
     * player is not gliding, when {@code previous} is {@code null} (the first tick after a teleport
     * has no segment to test), or when every ring has already been passed.
     */
    public static ProgressUpdate advance(RingProgress current, List<Ring> rings, @Nullable Vec3 previous,
            Vec3 position, boolean gliding) {
        if (!gliding || previous == null || current.passedCount() >= rings.size()) {
            return new ProgressUpdate(current, null);
        }

        Ring next = rings.get(current.passedCount());
        if (!RingPass.crosses(previous, position, next)) {
            return new ProgressUpdate(current, null);
        }

        int lastCheckpointIndex = next.type() == RingType.CHECKPOINT
                ? next.index()
                : current.lastCheckpointIndex();
        RingProgress advanced = new RingProgress(current.passedCount() + 1, lastCheckpointIndex);
        return new ProgressUpdate(advanced, next);
    }
}
