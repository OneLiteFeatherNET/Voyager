package net.elytrarace.common.game.mode;

import org.jetbrains.annotations.Nullable;

public enum GameMode {
    RACE("race", 2, true),
    PRACTICE("practice", 1, false);

    private static final GameMode[] VALUES = values();
    private final String dslKey;
    private final int minimumPlayers;
    private final boolean leaderboardRanked;

    GameMode(String dslKey, int minimumPlayers, boolean leaderboardRanked) {
        this.dslKey = dslKey;
        this.minimumPlayers = minimumPlayers;
        this.leaderboardRanked = leaderboardRanked;
    }

    public String dslKey() {
        return dslKey;
    }

    public int minimumPlayers() {
        return minimumPlayers;
    }

    public boolean leaderboardRanked() {
        return leaderboardRanked;
    }

    public static @Nullable GameMode byName(String name) {
        GameMode match = null;
        for (int i = 0; i < VALUES.length && match == null; i++) {
            if (VALUES[i].dslKey.equalsIgnoreCase(name)) {
                match = VALUES[i];
            }
        }
        return match;
    }
}
