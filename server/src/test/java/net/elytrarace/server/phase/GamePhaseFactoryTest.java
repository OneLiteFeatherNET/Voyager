package net.elytrarace.server.phase;

import net.elytrarace.api.phase.EventRegistrar;
import net.elytrarace.api.phase.LinearPhaseSeries;
import net.elytrarace.api.phase.Phase;
import net.elytrarace.api.phase.PhaseScheduler;
import net.elytrarace.api.phase.PhaseTask;
import net.elytrarace.common.ecs.EntityManager;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link GamePhaseFactory}.
 * <p>
 * Uses stub implementations for {@link PhaseScheduler} and {@link EventRegistrar}
 * to avoid any platform dependency.
 */
class GamePhaseFactoryTest {

    private final PhaseScheduler stubScheduler = (task, intervalTicks, async) -> new PhaseTask() {
        @Override
        public void cancel() {
            // no-op
        }
    };

    private final EventRegistrar stubRegistrar = new EventRegistrar() {
        @Override
        public void registerListener(Object listener) {
            // no-op
        }

        @Override
        public void unregisterListener(Object listener) {
            // no-op
        }
    };

    private final EntityManager entityManager = new EntityManager();

    @Test
    void createGamePhasesReturnsSeriesWithThreePhases() {
        LinearPhaseSeries<Phase> series = GamePhaseFactory.createGamePhases(
                stubScheduler, stubRegistrar, entityManager);

        assertThat(series).hasSize(3);
    }

    @Test
    void firstPhaseIsMinestomLobbyPhase() {
        LinearPhaseSeries<Phase> series = GamePhaseFactory.createGamePhases(
                stubScheduler, stubRegistrar, entityManager);

        assertThat(series.get(0)).isInstanceOf(MinestomLobbyPhase.class);
    }

    @Test
    void secondPhaseIsMinestomGamePhase() {
        LinearPhaseSeries<Phase> series = GamePhaseFactory.createGamePhases(
                stubScheduler, stubRegistrar, entityManager);

        assertThat(series.get(1)).isInstanceOf(MinestomGamePhase.class);
    }

    @Test
    void lastPhaseIsMinestomEndPhase() {
        LinearPhaseSeries<Phase> series = GamePhaseFactory.createGamePhases(
                stubScheduler, stubRegistrar, entityManager);

        assertThat(series.get(2)).isInstanceOf(MinestomEndPhase.class);
    }

    @Test
    void phasesAreInCorrectOrder() {
        LinearPhaseSeries<Phase> series = GamePhaseFactory.createGamePhases(
                stubScheduler, stubRegistrar, entityManager);

        assertThat(series.get(0)).isInstanceOf(MinestomLobbyPhase.class);
        assertThat(series.get(1)).isInstanceOf(MinestomGamePhase.class);
        assertThat(series.get(2)).isInstanceOf(MinestomEndPhase.class);
    }
}
