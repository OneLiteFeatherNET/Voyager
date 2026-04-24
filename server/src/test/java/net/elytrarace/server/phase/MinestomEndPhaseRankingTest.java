package net.elytrarace.server.phase;

import net.elytrarace.common.ecs.Entity;
import net.elytrarace.common.ecs.EntityManager;
import net.elytrarace.server.ecs.component.PlayerRefComponent;
import net.elytrarace.server.ecs.component.ScoreComponent;
import net.minestom.server.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the ranking / bonus rules used by {@link MinestomEndPhase} before
 * results are persisted. Keeps the scoring logic testable without standing up
 * the Minestom server or the phase lifecycle.
 */
class MinestomEndPhaseRankingTest {

    @Test
    void ranksDescendingByTotalAndAppliesStandardBonuses() {
        EntityManager em = new EntityManager();
        Entity e1 = player(em, "A", 40);
        Entity e2 = player(em, "B", 100);
        Entity e3 = player(em, "C", 70);

        List<Entity> ranked = MinestomEndPhase.rankAndApplyBonuses(em);

        assertThat(ranked).extracting(e -> e.getComponent(PlayerRefComponent.class).getPlayer().getUsername())
                .containsExactly("B", "C", "A");
        assertThat(ranked.get(0).getComponent(ScoreComponent.class).getPositionBonus()).isEqualTo(50);
        assertThat(ranked.get(1).getComponent(ScoreComponent.class).getPositionBonus()).isEqualTo(30);
        assertThat(ranked.get(2).getComponent(ScoreComponent.class).getPositionBonus()).isEqualTo(20);
    }

    @Test
    void fourthAndBelowReceiveTheFallbackBonus() {
        EntityManager em = new EntityManager();
        for (int i = 0; i < 5; i++) {
            player(em, "P" + i, 100 - i * 10); // distinct totals so ordering is deterministic
        }

        List<Entity> ranked = MinestomEndPhase.rankAndApplyBonuses(em);

        assertThat(ranked).hasSize(5);
        assertThat(ranked.get(3).getComponent(ScoreComponent.class).getPositionBonus()).isEqualTo(10);
        assertThat(ranked.get(4).getComponent(ScoreComponent.class).getPositionBonus()).isEqualTo(10);
    }

    @Test
    void emptyEntityManagerProducesEmptyRanking() {
        assertThat(MinestomEndPhase.rankAndApplyBonuses(new EntityManager())).isEmpty();
    }

    private static Entity player(EntityManager em, String username, int ringPoints) {
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUuid()).thenReturn(id);
        when(player.getUsername()).thenReturn(username);

        Entity entity = new Entity();
        entity.addComponent(new PlayerRefComponent(id, player));
        ScoreComponent score = new ScoreComponent();
        score.addRingPoints(ringPoints);
        entity.addComponent(score);
        em.addEntity(entity);
        return entity;
    }
}
