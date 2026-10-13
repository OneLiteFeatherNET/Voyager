package net.elytrarace.voyager.race.reset;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.race.progress.RingProgress;
import net.elytrarace.voyager.race.reset.exception.RunNotResettableException;
import net.elytrarace.voyager.race.run.RaceRun;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The rules of a course reset: when a racer has left the course or landed, and where and as what they
 * are sent back.
 *
 * <p>Every method is pure. Nothing here reads a clock, a world or a player, and nothing here knows
 * Minestom: the platform reads the dimension and the position and passes them in, then performs the
 * plan it gets back.
 *
 * <h2>The target is the last checkpoint passed</h2>
 *
 * <p>A reset returns the racer to the last {@code CHECKPOINT} ring they passed, facing along its flow, and
 * keeps the rings up to that checkpoint. The rings passed after it are removed, so the racer passes them
 * again in order. A racer who has passed no checkpoint goes back to the map's spawn and keeps no ring. The
 * clock keeps running throughout. The reset racer is placed gliding, so the next landing is a real one.
 */
@ApiStatus.Internal
public abstract class RunReset {

    /** Race ticks after a reset during which a landing does not count (design D4). */
    private static final int RESET_COOLDOWN_TICKS = 40;

    private RunReset() {
    }

    /**
     * Whether {@code position} is outside the vertical bounds of its world.
     *
     * <p>Strictly below the floor or strictly above the ceiling: a position exactly on either is inside.
     *
     * @param floorY the lowest height of the dimension, its minimum Y
     * @param ceilingY the top of the dimension, its minimum Y plus its height
     * @throws IllegalArgumentException if {@code ceilingY} is below {@code floorY}
     */
    @Contract(pure = true)
    public static boolean isOutOfBounds(Vec3 position, double floorY, double ceilingY) {
        if (ceilingY < floorY) {
            throw new IllegalArgumentException(
                    "a dimension's ceiling must not be below its floor, was %s below %s".formatted(ceilingY, floorY));
        }
        return position.y() < floorY || position.y() > ceilingY;
    }

    /**
     * Whether this tick is a landing: the racer was gliding on the tick that produced {@code before}, is on
     * the ground now, and is no longer gliding. A finished run cannot land, because nothing is reset after
     * the last ring.
     *
     * @param before the run as the previous tick left it
     * @param gliding whether the racer is gliding on this tick
     * @param onGround whether the racer is standing on the ground on this tick
     */
    @Contract(pure = true)
    public static boolean isLanded(RaceRun before, boolean gliding, boolean onGround) {
        return before.gliding() && !gliding && onGround && !before.finished();
    }

    /**
     * Plans the reset of a racer whose run is {@code run}, for {@code cause}.
     *
     * <p>With a checkpoint passed, the racer goes to the centre of the last checkpoint and faces one step
     * along its flow normal. The run keeps the rings up to that checkpoint and loses the rings passed after
     * it, so the racer cannot skip rings on the way back. With no checkpoint passed, the racer goes to the
     * spawn and faces the first ring, as a map start does, and the run keeps no ring. In both cases the run
     * is gliding with a reset cooldown and no airborne tick, and it loses the segment, and the ring passed,
     * of the tick that produced it.
     *
     * @throws RunNotResettableException if {@code run} has finished
     */
    public static ResetPlan plan(MapDefinition map, RaceRun run, ResetCause cause) {
        if (run.finished()) {
            throw RunNotResettableException.finishedRun();
        }
        int checkpoint = run.progress().lastCheckpointIndex();
        if (checkpoint < 0) {
            RaceRun reset = resetRun(RingProgress.atStart(), List.of());
            Ring first = map.rings().getFirst();
            return new ResetPlan(cause, reset, map.spawn(), first.center(), OptionalInt.empty());
        }
        int kept = checkpoint + 1;
        RaceRun reset = resetRun(new RingProgress(kept, checkpoint), run.passedOnGameTick().subList(0, kept));
        Ring target = map.rings().get(checkpoint);
        return new ResetPlan(cause, reset, target.center(), target.center().plus(target.normal()),
                OptionalInt.of(kept));
    }

    /** The run a reset leaves: gliding, with the reset cooldown running and no airborne tick. */
    private static RaceRun resetRun(RingProgress progress, List<Integer> passedOnGameTick) {
        return new RaceRun(progress, null, passedOnGameTick, Optional.empty(), null, true, 0,
                RESET_COOLDOWN_TICKS);
    }
}
