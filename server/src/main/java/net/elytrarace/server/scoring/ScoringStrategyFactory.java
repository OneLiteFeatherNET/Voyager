package net.elytrarace.server.scoring;

import net.elytrarace.common.game.mode.GameMode;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;

/**
 * Factory for creating {@link ScoringStrategy} instances by {@link GameMode}.
 *
 * <p>Follows the ManisGame factory convention: {@code abstract} class with a
 * private constructor and pure static creator methods. Marked
 * {@link ApiStatus.Internal} because consumers should obtain strategies through
 * the game session rather than constructing them directly.
 */
@ApiStatus.Internal
public abstract class ScoringStrategyFactory {

    private ScoringStrategyFactory() {
        // utility — no instances
    }

    /**
     * Creates a fresh {@link ScoringStrategy} for the given mode.
     *
     * @param mode the game mode the strategy should implement
     * @return a new, empty scoring strategy
     */
    @Contract(pure = true, value = "_ -> new")
    public static ScoringStrategy create(GameMode mode) {
        return switch (mode) {
            case RACE -> new RaceScoringStrategy();
            case PRACTICE -> new PracticeScoringStrategy();
        };
    }
}
