package net.elytrarace.voyager.race.reset;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.race.reset.exception.RunNotResettableException;
import net.elytrarace.voyager.race.run.RaceRun;

import java.util.OptionalInt;

/**
 * Everything a reset decides, as values: where the racer goes, which way they face and launch, what
 * their run becomes, and which ring the feedback names.
 *
 * @param cause why the reset happens
 * @param run the run the racer holds after the reset; every ring already passed stays passed
 * @param position where the racer is placed: the centre of the last ring passed, or the map's spawn
 * @param toward a point the racer faces and is launched toward: one step along the last ring's flow
 *     normal from its centre, or the first ring's centre for a spawn
 * @param targetRing the one-based number of the last ring passed, or empty for a reset to the spawn
 */
public record ResetPlan(ResetCause cause, RaceRun run, Vec3 position, Vec3 toward, OptionalInt targetRing) {

    public ResetPlan {
        if (run.finished()) {
            throw RunNotResettableException.finishedRun();
        }
    }
}
