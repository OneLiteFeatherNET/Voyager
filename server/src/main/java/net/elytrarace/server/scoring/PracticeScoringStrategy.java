package net.elytrarace.server.scoring;

import net.elytrarace.common.game.mode.GameMode;
import net.elytrarace.common.game.scoring.MedalBrackets;
import net.elytrarace.common.game.scoring.MedalTier;
import net.elytrarace.server.physics.Ring;

import java.time.Duration;
import java.util.UUID;

/**
 * Practice-mode scoring strategy. Tracks ring points and classifies the player's
 * medal tier for personal feedback, but awards no bracket points and no
 * finishing-position bonuses — practice runs are not competitively ranked.
 */
public final class PracticeScoringStrategy extends BaseScoringStrategy {

    @Override
    public void onRingPassed(UUID playerId, Ring ring) {
        scores.compute(playerId, (id, existing) -> {
            PlayerScore current = existing != null ? existing : PlayerScore.zero(id);
            return current.addRingPoints(ring.points());
        });
    }

    @Override
    public void onMapCompleted(UUID playerId, long elapsedMs, MedalBrackets brackets, Duration reference) {
        MedalTier tier = classifyTier(elapsedMs, brackets, reference);
        scores.compute(playerId, (id, existing) -> {
            PlayerScore current = existing != null ? existing : PlayerScore.zero(id);
            return current.withCompletion(elapsedMs, tier);
        });
    }

    @Override
    public void applyMapResults() {
        // Intentionally a no-op — practice mode does not award position bonuses.
    }

    @Override
    public GameMode mode() {
        return GameMode.PRACTICE;
    }

    private static MedalTier classifyTier(long elapsedMs, MedalBrackets brackets, Duration reference) {
        if (elapsedMs < 0) {
            return MedalTier.DNF;
        }
        return brackets.classify(Duration.ofMillis(elapsedMs), reference);
    }
}
