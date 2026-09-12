package net.elytrarace.tools.converter.legacy;

import org.jetbrains.annotations.Nullable;

/**
 * One entry of the old {@code guides.json}: a point on the shared order axis, written by the setup
 * wizard in the tree being replaced.
 *
 * <p>Every field is boxed, like every other legacy record here, so that "the file did not say" and
 * "the file said zero" stay different — a guide at {@code y = 0} is a perfectly ordinary point on a
 * course that climbs through it.
 */
public record LegacyGuide(@Nullable Integer orderIndex, @Nullable Double x, @Nullable Double y,
        @Nullable Double z) {
}
