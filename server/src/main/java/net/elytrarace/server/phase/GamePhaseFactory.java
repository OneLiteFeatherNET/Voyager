package net.elytrarace.server.phase;

import net.elytrarace.api.phase.EventRegistrar;
import net.elytrarace.api.phase.LinearPhaseSeries;
import net.elytrarace.api.phase.Phase;
import net.elytrarace.api.phase.PhaseScheduler;
import net.elytrarace.common.ecs.EntityManager;

import java.util.List;

/**
 * Factory that assembles the standard game phase series for the Minestom server.
 * <p>
 * The series follows the order: Lobby -> Game -> End.
 * The {@link LinearPhaseSeries} automatically advances through each phase
 * when the previous one finishes.
 */
public final class GamePhaseFactory {

    private GamePhaseFactory() {
        // utility class
    }

    /**
     * Creates a linear phase series containing lobby, game, and end phases
     * with default timing configurations.
     *
     * @param scheduler    the platform scheduler for repeating tasks
     * @param registrar    the platform event registrar
     * @param entityManager the ECS entity manager driving the game loop
     * @return a ready-to-start phase series
     */
    public static LinearPhaseSeries<Phase> createGamePhases(
            PhaseScheduler scheduler,
            EventRegistrar registrar,
            EntityManager entityManager
    ) {
        var lobby = new MinestomLobbyPhase(scheduler, registrar);
        var game = new MinestomGamePhase(scheduler, registrar, entityManager);
        var end = new MinestomEndPhase(scheduler, registrar);
        return new LinearPhaseSeries<>(List.of(lobby, game, end));
    }
}
