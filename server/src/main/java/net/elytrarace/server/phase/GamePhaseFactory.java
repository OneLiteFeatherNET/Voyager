package net.elytrarace.server.phase;

import net.elytrarace.common.ecs.EntityManager;
import net.theevilreaper.xerus.api.phase.LinearPhaseSeries;
import net.theevilreaper.xerus.api.phase.Phase;

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
     * @param entityManager the ECS entity manager driving the game loop
     * @return a ready-to-start phase series
     */
    public static LinearPhaseSeries<Phase> createGamePhases(EntityManager entityManager) {
        var lobby = new MinestomLobbyPhase();
        var game = new MinestomGamePhase(entityManager);
        var end = new MinestomEndPhase();
        return new LinearPhaseSeries<>("game-phases", List.of(lobby, game, end));
    }
}
