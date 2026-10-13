package net.elytrarace.voyager.race.reset;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.race.reset.exception.RunNotResettableException;
import net.elytrarace.voyager.race.run.RaceRun;

import java.util.OptionalInt;

/**
 * Everything a reset decides, as values: where the racer goes, which way they face, what
 * their run becomes, and which ring the feedback names.
 *
 * @param cause why the reset happens
 * @param run the run the racer holds after the reset: the rings up to the target stay passed, and the
 *     rings passed after it are removed; a spawn reset keeps no ring
 * @param position where the racer is placed: the centre of the last checkpoint passed, or the map's spawn
 * @param toward a point the racer faces: one step along the checkpoint's flow
 *     normal from its centre, or the first ring's centre for a spawn
 * @param targetRing the one-based number of the checkpoint the racer is returned to, or empty for a reset
 *     to the spawn
 */
public record ResetPlan(ResetCause cause, RaceRun run, Vec3 position, Vec3 toward, OptionalInt targetRing) {

    public ResetPlan {
        if (run.finished()) {
            throw RunNotResettableException.finishedRun();
        }
    }
}
