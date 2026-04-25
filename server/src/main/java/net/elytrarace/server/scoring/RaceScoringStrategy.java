package net.elytrarace.server.scoring;

import net.elytrarace.common.game.mode.GameMode;
import net.elytrarace.common.game.scoring.MedalBrackets;
import net.elytrarace.common.game.scoring.MedalTier;
import net.elytrarace.server.physics.Ring;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Race-mode scoring strategy implementing the time-bracket model from ADR-0003.
 *
 * <p>Per-map rewards are layered:
 * <ol>
 *   <li>Ring points — accumulated as players fly through ring checkpoints.</li>
 *   <li>Bracket points — awarded on completion based on the medal tier earned
 *       (DIAMOND=60, GOLD=45, SILVER=30, BRONZE=15, FINISH=5, DNF=0).</li>
 *   <li>Position bonus — small finishing-position reward (1st=10, 2nd=6, 3rd=3,
 *       4th and below=1).</li>
 * </ol>
 * Both bracket points and position bonus are stored in {@link PlayerScore#positionBonus()}.
 */
public final class RaceScoringStrategy extends BaseScoringStrategy {

    /** Position bonus by 0-based finishing index. Index >= length falls back to {@link #DEFAULT_POSITION_BONUS}. */
    private static final int[] POSITION_BONUSES = {10, 6, 3};

    /** Position bonus awarded to all finishers from 4th place onward. */
    private static final int DEFAULT_POSITION_BONUS = 1;

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
        int bracketPoints = bracketPointsFor(tier);
        scores.compute(playerId, (id, existing) -> {
            PlayerScore current = existing != null ? existing : PlayerScore.zero(id);
            return current
                    .withCompletion(elapsedMs, tier)
                    .addBonusPoints(bracketPoints);
        });
    }

    @Override
    public synchronized void applyMapResults() {
        List<PlayerScore> ranked = getRanking();
        for (int i = 0; i < ranked.size(); i++) {
            int bonus = i < POSITION_BONUSES.length ? POSITION_BONUSES[i] : DEFAULT_POSITION_BONUS;
            PlayerScore current = ranked.get(i);
            scores.compute(current.playerId(), (id, existing) -> {
                PlayerScore base = existing != null ? existing : current;
                return base.addBonusPoints(bonus);
            });
        }
    }

    @Override
    public GameMode mode() {
        return GameMode.RACE;
    }

    /**
     * Classifies a player's elapsed time against the supplied brackets. Negative
     * elapsed times are treated as DNF.
     */
    private static MedalTier classifyTier(long elapsedMs, MedalBrackets brackets, Duration reference) {
        if (elapsedMs < 0) {
            return MedalTier.DNF;
        }
        return brackets.classify(Duration.ofMillis(elapsedMs), reference);
    }

    /**
     * Maps a {@link MedalTier} to the bracket point value defined in ADR-0003.
     */
    private static int bracketPointsFor(MedalTier tier) {
        return switch (tier) {
            case DIAMOND -> 60;
            case GOLD -> 45;
            case SILVER -> 30;
            case BRONZE -> 15;
            case FINISH -> 5;
            case DNF -> 0;
        };
    }
}
