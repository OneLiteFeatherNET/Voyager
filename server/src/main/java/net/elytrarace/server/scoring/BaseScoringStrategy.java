package net.elytrarace.server.scoring;

import org.jetbrains.annotations.Unmodifiable;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Abstract base for {@link ScoringStrategy} implementations. Provides the shared
 * concurrent score storage plus the read paths ({@link #getScore(UUID)},
 * {@link #getRanking()}) and {@link #reset()} which are identical for every mode.
 *
 * <p>Subclasses implement the mode-specific write paths
 * ({@link #onRingPassed(UUID, net.elytrarace.server.physics.Ring)},
 * {@link #onMapCompleted(UUID, long, net.elytrarace.common.game.scoring.MedalBrackets, java.time.Duration)},
 * {@link #applyMapResults()}) and declare their {@link #mode()}.
 */
public abstract non-sealed class BaseScoringStrategy implements ScoringStrategy {

    /** Mutable score state keyed by player UUID. Concurrent for safe ring-callback use. */
    protected final ConcurrentHashMap<UUID, PlayerScore> scores = new ConcurrentHashMap<>();

    @Override
    public PlayerScore getScore(UUID playerId) {
        return scores.getOrDefault(playerId, PlayerScore.zero(playerId));
    }

    @Override
    public @Unmodifiable List<PlayerScore> getRanking() {
        return scores.values().stream()
                .sorted(Comparator.comparingInt(PlayerScore::totalPoints).reversed())
                .toList();
    }

    @Override
    public void reset() {
        scores.clear();
    }
}
