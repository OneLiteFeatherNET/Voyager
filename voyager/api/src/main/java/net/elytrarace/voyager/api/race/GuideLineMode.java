package net.elytrarace.voyager.api.race;

import net.elytrarace.voyager.api.race.exception.InvalidGuideLineException;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** How much of the racing line a racer is shown. */
public enum GuideLineMode {
    /** The line from the ring the racer is heading for to the last ring. The flown part is not drawn. */
    FULL,
    /** The stretch from the ring the racer is heading for {@code lookAheadRings} rings on, and nothing more. */
    WINDOW;

    private static final List<GuideLineMode> VALUES = List.of(values());

    /**
     * Looks up a mode by name, matching case-insensitively. Reports absence rather than throwing, so a
     * caller can choose its own refusal.
     */
    public static Optional<GuideLineMode> byName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String normalized = name.trim().toUpperCase(Locale.ROOT);
        for (GuideLineMode mode : VALUES) {
            if (mode.name().equals(normalized)) {
                return Optional.of(mode);
            }
        }
        return Optional.empty();
    }

    /**
     * Looks up a mode by name and refuses anything else, naming the value that was given.
     *
     * @throws InvalidGuideLineException if the name is not {@code FULL} or {@code WINDOW}
     */
    public static GuideLineMode parse(String name) {
        return byName(name).orElseThrow(() -> InvalidGuideLineException.unknownMode(name));
    }
}
