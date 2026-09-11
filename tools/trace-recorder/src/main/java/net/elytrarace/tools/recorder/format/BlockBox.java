package net.elytrarace.tools.recorder.format;

import net.elytrarace.tools.recorder.format.exception.InvalidTraceException;

/** One solid collision box from the recorded world slice, in world coordinates. */
public record BlockBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {

    public BlockBox {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new InvalidTraceException(
                    "block box minimum exceeds its maximum: (%s, %s, %s) to (%s, %s, %s)"
                            .formatted(minX, minY, minZ, maxX, maxY, maxZ));
        }
    }
}
