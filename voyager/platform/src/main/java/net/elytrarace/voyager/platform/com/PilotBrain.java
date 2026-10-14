package net.elytrarace.voyager.platform.com;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.race.com.ComPath;
import net.elytrarace.voyager.race.com.ComPilot;
import net.elytrarace.voyager.race.com.ComState;
import net.elytrarace.voyager.race.com.ComTuning;

/** The production {@link ComBrain}: the pure {@link ComPilot}, with no state of its own. */
final class PilotBrain implements ComBrain {

    @Override
    public ComState step(ComState state, ComTuning tuning, ComPath path, long tick) {
        return ComPilot.step(state, tuning, path, tick);
    }

    @Override
    public Vec3 positionOf(ComState state, ComTuning tuning, ComPath path) {
        return ComPilot.positionOf(state, tuning, path);
    }
}
