package net.elytrarace.voyager.api.race;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** What passing through a ring does. */
public enum RingType {
    STANDARD,
    BOOST,
    SLOW,
    CHECKPOINT;

    private static final List<RingType> VALUES = List.of(values());

    /**
     * Looks up a ring type by name, matching case-insensitively. Map JSON is written by hand, so this
     * never throws on unrecognised or blank input — it reports absence instead.
     */
    public static Optional<RingType> byName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String normalized = name.trim().toUpperCase(Locale.ROOT);
        for (RingType type : VALUES) {
            if (type.name().equals(normalized)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
