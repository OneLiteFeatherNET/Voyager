package net.elytrarace.tools.converter.legacy;

import net.elytrarace.tools.converter.exception.InvalidCourseException;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A UUID as the old format stored it — two signed longs rather than the canonical text form.
 *
 * <p>This is the only thing a cup knows about its maps in the old data, which is why cup conversion
 * needs every {@code map.json} in hand: the UUID has to be traded for a name before a
 * {@code CupDefinition} can exist at all.
 */
public record LegacyUuid(@Nullable Long mostSigBits, @Nullable Long leastSigBits) {

    /**
     * The identifier this pair of longs stands for.
     *
     * @throws InvalidCourseException if either half is absent — a half-written UUID would otherwise
     *                                unbox to zero and silently name a different map
     */
    public UUID toUuid() {
        if (mostSigBits == null || leastSigBits == null) {
            throw new InvalidCourseException(
                    "a uuid needs both mostSigBits and leastSigBits, got %s and %s"
                            .formatted(mostSigBits, leastSigBits));
        }
        return new UUID(mostSigBits, leastSigBits);
    }
}
