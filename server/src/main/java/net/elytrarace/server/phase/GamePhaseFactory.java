package net.elytrarace.server.phase;

import net.elytrarace.common.ecs.EntityManager;
import net.theevilreaper.xerus.api.phase.LinearPhaseSeries;
import net.theevilreaper.xerus.api.phase.Phase;
import org.jetbrains.annotations.Nullable;

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
        return createGamePhases(entityManager, null, null);
    }

    /**
     * Creates a linear phase series containing lobby, game, and end phases.
     *
     * @param entityManager        the ECS entity manager driving the game loop
     * @param onMapSwitch          callback invoked when the lobby phase ends, before the game phase starts;
     *                             use this to trigger map loading and player teleportation
     * @param onGamePhaseFinished  callback invoked when the game phase finishes (race duration expired
     *                             or all rings passed); use this to advance to the next map or end phase
     * @return a ready-to-start phase series
     */
    public static LinearPhaseSeries<Phase> createGamePhases(EntityManager entityManager,
                                                            @Nullable Runnable onMapSwitch,
                                                            @Nullable Runnable onGamePhaseFinished) {
        var lobby = new MinestomLobbyPhase(120, onMapSwitch);
        var game = new MinestomGamePhase(entityManager,
                MinestomGamePhase.DEFAULT_RACE_DURATION_TICKS, onGamePhaseFinished);
        var end = new MinestomEndPhase(100, entityManager);
        return new LinearPhaseSeries<>("game-phases", List.of(lobby, game, end));
    }
}
