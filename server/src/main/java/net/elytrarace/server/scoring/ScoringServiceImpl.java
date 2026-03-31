package net.elytrarace.server.scoring;

import net.elytrarace.server.physics.Ring;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe implementation of {@link ScoringService} backed by a
 * {@link ConcurrentHashMap}.
 */
public final class ScoringServiceImpl implements ScoringService {

    private static final int[] POSITION_BONUSES = {50, 30, 20};
    private static final int DEFAULT_BONUS = 10;

    private final ConcurrentHashMap<UUID, PlayerScore> scores = new ConcurrentHashMap<>();

    @Override
    public void onRingPassed(UUID playerId, Ring ring) {
        scores.compute(playerId, (id, existing) -> {
            if (existing == null) {
                existing = new PlayerScore(id, 0, 0, 0);
            }
            return existing.addRingPoints(ring.points());
        });
    }

    @Override
    public PlayerScore getScore(UUID playerId) {
        return scores.getOrDefault(playerId, new PlayerScore(playerId, 0, 0, 0));
    }

    @Override
    public List<PlayerScore> getRanking() {
        return scores.values().stream()
                .sorted(Comparator.comparingInt(PlayerScore::totalPoints).reversed())
                .toList();
    }

    @Override
    public synchronized void applyPositionBonuses() {
        List<PlayerScore> ranked = getRanking();
        for (int i = 0; i < ranked.size(); i++) {
            int bonus = i < POSITION_BONUSES.length ? POSITION_BONUSES[i] : DEFAULT_BONUS;
            PlayerScore current = ranked.get(i);
            scores.compute(current.playerId(), (id, existing) ->
                    existing != null ? existing.withPositionBonus(bonus) : current.withPositionBonus(bonus));
        }
    }

    @Override
    public void reset() {
        scores.clear();
    }
}
