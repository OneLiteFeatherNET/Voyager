package net.elytrarace.voyager.api.race;

import net.elytrarace.voyager.api.race.exception.InvalidGuideLineException;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The particle the racing line is drawn with. A closed list on purpose: each entry is known to the
 * server, so an unknown name is refused in the map file rather than failing on a racer's screen.
 */
public enum GuideParticle {
    /** The end rod sparkle. It has no colour of its own. */
    END_ROD,
    /** A dust particle, drawn in the map's {@code dustColor}. */
    DUST;

    private static final List<GuideParticle> VALUES = List.of(values());

    /**
     * Looks up a particle by name, matching case-insensitively. Reports absence rather than throwing,
     * so a caller can choose its own refusal.
     */
    public static Optional<GuideParticle> byName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String normalized = name.trim().toUpperCase(Locale.ROOT);
        for (GuideParticle particle : VALUES) {
            if (particle.name().equals(normalized)) {
                return Optional.of(particle);
            }
        }
        return Optional.empty();
    }

    /**
     * Looks up a particle by name and refuses anything else, naming the value that was given.
     *
     * @throws InvalidGuideLineException if the name is not {@code END_ROD} or {@code DUST}
     */
    public static GuideParticle parse(String name) {
        return byName(name).orElseThrow(() -> InvalidGuideLineException.unknownParticle(name));
    }
}
