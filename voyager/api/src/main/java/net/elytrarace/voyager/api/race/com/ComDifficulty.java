package net.elytrarace.voyager.api.race.com;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * How hard a computer racer plays, from the easiest to the reference racer. The default is {@link #MEDIUM}.
 */
public enum ComDifficulty {
    EASY,
    MEDIUM,
    HARD;

    private static final List<ComDifficulty> VALUES = List.of(values());

    /**
     * Looks up a difficulty by name, matching case-insensitively. The setting is written by hand, so this
     * never throws on unrecognised or blank input; it reports absence instead.
     */
    public static Optional<ComDifficulty> byName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String normalized = name.trim().toUpperCase(Locale.ROOT);
        for (ComDifficulty difficulty : VALUES) {
            if (difficulty.name().equals(normalized)) {
                return Optional.of(difficulty);
            }
        }
        return Optional.empty();
    }
}
