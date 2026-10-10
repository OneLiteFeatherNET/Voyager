package net.elytrarace.voyager.race.reset;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.race.reset.exception.RunNotResettableException;
import net.elytrarace.voyager.race.run.RaceRun;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;

import java.util.OptionalInt;

/**
 * The rules of a course reset: when a racer has left the course or landed, and where and as what they
 * are sent back.
 *
 * <p>Every method is pure. Nothing here reads a clock, a world or a player, and nothing here knows
 * Minestom: the platform reads the dimension and the position and passes them in, then performs the
 * plan it gets back.
 *
 * <h2>The target is the last ring passed</h2>
 *
 * <p>A reset returns the racer to the last ring they passed, of any type, and keeps every ring already
 * passed, so nothing is forfeited. A racer who has passed no ring goes back to the map's spawn with the
 * run unchanged. The clock keeps running throughout.
 */
@ApiStatus.Internal
public abstract class RunReset {

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
     * <p>With at least one ring passed, the racer goes to the centre of the last ring passed and faces one
     * step along its flow normal. With none, the racer goes to the spawn and faces the first ring, as a
     * map start does. In both cases the run keeps every ring it has passed, and loses only the fields that
     * describe the tick that produced it: the segment for the next tick, the ring passed on it, and the
     * glide, so that the relaunch is not a landing.
     *
     * @throws RunNotResettableException if {@code run} has finished
     */
    public static ResetPlan plan(MapDefinition map, RaceRun run, ResetCause cause) {
        if (run.finished()) {
            throw RunNotResettableException.finishedRun();
        }
        RaceRun reset = new RaceRun(run.progress(), null, run.passedOnGameTick(), run.finishedAt(), null, false);
        int passed = run.progress().passedCount();
        if (passed == 0) {
            Ring first = map.rings().getFirst();
            return new ResetPlan(cause, reset, map.spawn(), first.center(), OptionalInt.empty());
        }
        Ring last = map.rings().get(passed - 1);
        return new ResetPlan(cause, reset, last.center(), last.center().plus(last.normal()), OptionalInt.of(passed));
    }
}
