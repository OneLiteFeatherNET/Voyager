package net.elytrarace.server.phase;

import net.elytrarace.common.ecs.EntityManager;
import net.theevilreaper.xerus.api.phase.LinearPhaseSeries;
import net.theevilreaper.xerus.api.phase.Phase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link GamePhaseFactory}.
 */
class GamePhaseFactoryTest {

    private final EntityManager entityManager = new EntityManager();

    @Test
    void createGamePhasesReturnsSeriesWithThreePhases() {
        LinearPhaseSeries<Phase> series = GamePhaseFactory.createGamePhases(entityManager);

        assertThat(series).hasSize(3);
    }

    @Test
    void firstPhaseIsMinestomLobbyPhase() {
        LinearPhaseSeries<Phase> series = GamePhaseFactory.createGamePhases(entityManager);

        assertThat(series.get(0)).isInstanceOf(MinestomLobbyPhase.class);
    }

    @Test
    void secondPhaseIsMinestomGamePhase() {
        LinearPhaseSeries<Phase> series = GamePhaseFactory.createGamePhases(entityManager);

        assertThat(series.get(1)).isInstanceOf(MinestomGamePhase.class);
    }

    @Test
    void lastPhaseIsMinestomEndPhase() {
        LinearPhaseSeries<Phase> series = GamePhaseFactory.createGamePhases(entityManager);

        assertThat(series.get(2)).isInstanceOf(MinestomEndPhase.class);
    }

    @Test
    void phasesAreInCorrectOrder() {
        LinearPhaseSeries<Phase> series = GamePhaseFactory.createGamePhases(entityManager);

        assertThat(series.get(0)).isInstanceOf(MinestomLobbyPhase.class);
        assertThat(series.get(1)).isInstanceOf(MinestomGamePhase.class);
        assertThat(series.get(2)).isInstanceOf(MinestomEndPhase.class);
    }
}
