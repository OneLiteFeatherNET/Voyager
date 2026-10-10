package net.elytrarace.server.phase;

import net.elytrarace.common.ecs.EntityManager;
import net.elytrarace.common.game.mode.GameMode;
import net.elytrarace.server.persistence.GameResultPersistenceService;
import net.theevilreaper.xerus.api.phase.LinearPhaseSeries;
import net.theevilreaper.xerus.api.phase.Phase;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Factory that assembles the standard game phase series for the Minestom server.
 * <p>
 * The series follows the order: Lobby -> Game -> End.
 * The {@link LinearPhaseSeries} automatically advances through each phase
 * when the previous one finishes.
 * <p>
 * The {@link GameMode} parameter is currently accepted for forward compatibility
 * (mode-specific phase composition will be introduced in a later epic). For now,
 * both {@code RACE} and {@code PRACTICE} produce the same phase series.
 */
public final class GamePhaseFactory {

    private GamePhaseFactory() {
        // utility class
    }

    /**
     * Creates a linear phase series with default timing configurations and the default
     * {@link GameMode#RACE} mode.
     */
    public static LinearPhaseSeries<Phase> createGamePhases(EntityManager entityManager) {
        return createGamePhases(entityManager, GameMode.RACE, null, null, null);
    }

    /**
     * Creates a linear phase series without persistence hooks. Defaults to
     * {@link GameMode#RACE}.
     */
    public static LinearPhaseSeries<Phase> createGamePhases(EntityManager entityManager,
                                                            @Nullable Runnable onMapSwitch,
                                                            @Nullable Runnable onGamePhaseFinished) {
        return createGamePhases(entityManager, GameMode.RACE, onMapSwitch, onGamePhaseFinished, null);
    }

    /**
     * Creates a linear phase series with the given persistence service. Defaults to
     * {@link GameMode#RACE}.
     */
    public static LinearPhaseSeries<Phase> createGamePhases(EntityManager entityManager,
                                                            @Nullable Runnable onMapSwitch,
                                                            @Nullable Runnable onGamePhaseFinished,
                                                            @Nullable GameResultPersistenceService gameResultPersistence) {
        return createGamePhases(entityManager, GameMode.RACE, onMapSwitch, onGamePhaseFinished, gameResultPersistence);
    }

    /**
     * Creates a linear phase series for the given {@link GameMode} without persistence hooks.
     */
    public static LinearPhaseSeries<Phase> createGamePhases(EntityManager entityManager,
                                                            GameMode mode,
                                                            @Nullable Runnable onMapSwitch,
                                                            @Nullable Runnable onGamePhaseFinished) {
        return createGamePhases(entityManager, mode, onMapSwitch, onGamePhaseFinished, null);
    }

    /**
     * Creates a linear phase series containing lobby, game, and end phases for the
     * given {@link GameMode}.
     *
     * @param entityManager           the ECS entity manager driving the game loop
     * @param mode                    the game mode this series runs under; currently informational
     *                                (mode-specific composition arrives in a later epic)
     * @param onMapSwitch             callback invoked when the lobby phase ends, before the game phase starts;
     *                                use this to trigger map loading and player teleportation
     * @param onGamePhaseFinished     callback invoked when the game phase finishes (race duration expired
     *                                or all rings passed); use this to advance to the next map or end phase
     * @param gameResultPersistence   persistence service used by the end phase to write final standings;
     *                                may be {@code null} to disable persistence
     * @return a ready-to-start phase series
     */
    public static LinearPhaseSeries<Phase> createGamePhases(EntityManager entityManager,
                                                            GameMode mode,
                                                            @Nullable Runnable onMapSwitch,
                                                            @Nullable Runnable onGamePhaseFinished,
                                                            @Nullable GameResultPersistenceService gameResultPersistence) {
        Objects.requireNonNull(entityManager, "entityManager must not be null");
        Objects.requireNonNull(mode, "mode must not be null");
        var lobby = new MinestomLobbyPhase(120, onMapSwitch);
        var game = new MinestomGamePhase(entityManager,
                MinestomGamePhase.DEFAULT_RACE_DURATION_TICKS, onGamePhaseFinished);
        var end = new MinestomEndPhase(100, entityManager, gameResultPersistence);
        return new LinearPhaseSeries<>("game-phases", List.of(lobby, game, end));
    }
}
