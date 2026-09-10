package net.elytrarace.tools.recorder.script;

import net.elytrarace.tools.recorder.format.exception.InvalidTraceException;

import java.util.List;

/** A parsed flight script: the per-tick rotation and firework schedule that drives the recorder. */
public record FlightScript(List<ScriptedInput> inputs) {

    public FlightScript {
        inputs = List.copyOf(inputs);
    }

    /** Number of ticks this script drives the recorded entity for. */
    public int durationTicks() {
        return inputs.size();
    }

    /** The scripted input for the given tick, {@code 0}-based. */
    public ScriptedInput inputAt(int tick) {
        if (tick < 0 || tick >= inputs.size()) {
            throw new InvalidTraceException(
                    "tick %s is outside the script, which covers 0..%s".formatted(tick, inputs.size() - 1));
        }
        return inputs.get(tick);
    }
}
