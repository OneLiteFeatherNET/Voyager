package net.elytrarace.tools.recorder.script;

import net.elytrarace.tools.recorder.format.exception.InvalidTraceException;

/** One tick's worth of scripted rotation and firework intent, fed into the recorder each tick. */
public record ScriptedInput(float yaw, float pitch, boolean igniteFirework) {

    public ScriptedInput {
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new InvalidTraceException(
                    "scripted input carries a non-finite rotation: yaw=%s pitch=%s".formatted(yaw, pitch));
        }
    }
}
