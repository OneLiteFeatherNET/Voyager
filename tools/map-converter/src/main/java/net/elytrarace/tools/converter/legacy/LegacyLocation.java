package net.elytrarace.tools.converter.legacy;

import org.jetbrains.annotations.Nullable;

/**
 * One point of a ring as the setup wizard wrote it: integer block coordinates, and a flag saying
 * whether this is the ring's centre or a point on its rim.
 *
 * <p>Boxed {@code Integer} and {@code Boolean} on purpose. Gson leaves an absent field at its
 * default, and for primitives that default is a valid-looking {@code 0}/{@code false} — a portal
 * missing its {@code y} would convert into a ring at {@code y = 0} with nothing to say so. Boxed,
 * the absence survives as {@code null} and {@link LegacyPortal} can reject it by name.
 */
public record LegacyLocation(@Nullable Integer x, @Nullable Integer y, @Nullable Integer z,
                             @Nullable Boolean center) {

    /** Whether this point is the ring's centre. An absent flag means a rim point, as it did before. */
    public boolean isCenter() {
        return center != null && center;
    }
}
