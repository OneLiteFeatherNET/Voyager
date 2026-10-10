package net.elytrarace.voyager.api.race;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** How a cup is played: the minimum player count it needs, and whether it counts toward rankings. */
public enum GameMode {
    RACE(2, true),
    PRACTICE(1, false);

    private static final List<GameMode> VALUES = List.of(values());

    private final int minimumPlayers;
    private final boolean ranked;

    GameMode(int minimumPlayers, boolean ranked) {
        this.minimumPlayers = minimumPlayers;
        this.ranked = ranked;
    }

    public int minimumPlayers() {
        return minimumPlayers;
    }

    public boolean ranked() {
        return ranked;
    }

    /**
     * Looks up a game mode by name, matching case-insensitively. Cup JSON is written by hand, so this
     * never throws on unrecognised or blank input — it reports absence instead.
     */
    public static Optional<GameMode> byName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String normalized = name.trim().toUpperCase(Locale.ROOT);
        for (GameMode mode : VALUES) {
            if (mode.name().equals(normalized)) {
                return Optional.of(mode);
            }
        }
        return Optional.empty();
    }
}
