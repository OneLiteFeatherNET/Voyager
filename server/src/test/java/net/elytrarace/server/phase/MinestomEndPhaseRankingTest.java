package net.elytrarace.server.phase;

import net.elytrarace.common.ecs.Entity;
import net.elytrarace.common.ecs.EntityManager;
import net.elytrarace.server.ecs.component.PlayerRefComponent;
import net.elytrarace.server.ecs.component.ScoreComponent;
import net.minestom.server.coordinate.Pos;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the ranking / bonus rules used by {@link MinestomEndPhase} before
 * results are persisted. Keeps the scoring logic testable without standing up
 * the full phase lifecycle.
 */
@EnvTest
class MinestomEndPhaseRankingTest {

    @Test
    void ranksDescendingByTotalAndAppliesStandardBonuses(Env env) {
        EntityManager em = new EntityManager();
        Entity eA = player(env, em, 40);
        Entity eB = player(env, em, 100);
        Entity eC = player(env, em, 70);

        List<Entity> ranked = MinestomEndPhase.rankAndApplyBonuses(em);

        // eB=100 > eC=70 > eA=40 before bonuses — verify ring-point ordering
        assertThat(ranked).extracting(e -> e.getComponent(ScoreComponent.class).getRingPoints())
                .containsExactly(100, 70, 40);
        assertThat(ranked.get(0).getComponent(ScoreComponent.class).getPositionBonus()).isEqualTo(10);
        assertThat(ranked.get(1).getComponent(ScoreComponent.class).getPositionBonus()).isEqualTo(6);
        assertThat(ranked.get(2).getComponent(ScoreComponent.class).getPositionBonus()).isEqualTo(3);
    }

    @Test
    void fourthAndBelowReceiveTheFallbackBonus(Env env) {
        EntityManager em = new EntityManager();
        for (int i = 0; i < 5; i++) {
            player(env, em, 100 - i * 10); // distinct totals so ordering is deterministic
        }

        List<Entity> ranked = MinestomEndPhase.rankAndApplyBonuses(em);

        assertThat(ranked).hasSize(5);
        assertThat(ranked.get(3).getComponent(ScoreComponent.class).getPositionBonus()).isEqualTo(1);
        assertThat(ranked.get(4).getComponent(ScoreComponent.class).getPositionBonus()).isEqualTo(1);
    }

    @Test
    void emptyEntityManagerProducesEmptyRanking(Env env) {
        assertThat(MinestomEndPhase.rankAndApplyBonuses(new EntityManager())).isEmpty();
    }

    private static Entity player(Env env, EntityManager em, int ringPoints) {
        var instance = env.createFlatInstance();
        var player = env.createPlayer(instance, new Pos(0, 64, 0));
        Entity entity = new Entity();
        entity.addComponent(new PlayerRefComponent(player.getUuid(), player));
        ScoreComponent score = new ScoreComponent();
        score.addRingPoints(ringPoints);
        entity.addComponent(score);
        em.addEntity(entity);
        return entity;
    }
}
