package net.elytrarace.tools.recorder.format;

import net.elytrarace.tools.recorder.format.exception.InvalidTraceException;

import java.util.List;

/**
 * What a replay needs to know about a recording beyond the samples themselves.
 *
 * <p>{@code gravity} is the entity's effective gravity attribute at record time. Vanilla's lift term
 * is {@code gravity * (-1.0 + cos²(pitch) * 0.75)}, so a replay that assumes the default silently
 * diverges whenever the recording used a different value.
 */
public record TraceMetadata(
        String minecraftVersion,
        String profile,
        double gravity,
        int formatVersion,
        List<BlockBox> worldSlice) {

    public TraceMetadata {
        if (minecraftVersion.isBlank()) {
            throw new InvalidTraceException("minecraftVersion must not be blank");
        }
        if (profile.isBlank()) {
            throw new InvalidTraceException("profile must not be blank");
        }
        if (!Double.isFinite(gravity) || gravity <= 0.0) {
            throw new InvalidTraceException("gravity must be finite and > 0, was %s".formatted(gravity));
        }
        if (formatVersion < 1) {
            throw new InvalidTraceException("formatVersion must be >= 1, was %s".formatted(formatVersion));
        }
        worldSlice = List.copyOf(worldSlice);
    }
}
