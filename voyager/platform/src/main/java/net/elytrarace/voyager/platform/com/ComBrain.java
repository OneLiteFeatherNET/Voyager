package net.elytrarace.voyager.platform.com;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.race.com.ComPath;
import net.elytrarace.voyager.race.com.ComState;
import net.elytrarace.voyager.race.com.ComTuning;

/**
 * The movement the field asks for each tick: the pure brain of {@code race.com}, behind a seam so a test can put a fake
 * in its place. The production brain is {@link PilotBrain}, which is {@code ComPilot} and nothing else.
 */
interface ComBrain {

    /**
     * Advances one racer by one tick.
     *
     * @param state the racer before the tick
     * @param tuning the racer's tuning
     * @param path the course the racer runs on
     * @param tick the game tick
     * @return the racer after the tick
     */
    ComState step(ComState state, ComTuning tuning, ComPath path, long tick);

    /**
     * Where a racer stands in the world.
     *
     * @param state the racer
     * @param tuning the racer's tuning
     * @param path the course the racer runs on
     * @return the position in blocks
     */
    Vec3 positionOf(ComState state, ComTuning tuning, ComPath path);
}
