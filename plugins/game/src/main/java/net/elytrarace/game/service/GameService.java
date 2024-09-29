package net.elytrarace.game.service;

import net.elytrarace.api.database.service.DatabaseService;
import net.elytrarace.api.phase.LinearPhaseSeries;
import net.elytrarace.api.phase.Phase;
import net.elytrarace.common.cup.model.CupDTO;
import net.elytrarace.game.ElytraRace;
import net.elytrarace.game.model.GameMapDTO;
import net.elytrarace.game.model.GameSession;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Service for managing the game.
 */
public interface GameService {

    /**
     * Initialize the game service.
     * Selects random cup.
     *
     * @return A future that completes when the service is initialized.
     */
    CompletableFuture<Void> init();

    CompletableFuture<GameSession> switchMap();

    Optional<CupDTO> getCurrentCup();

    Optional<GameSession> getGameSession();

    Optional<GameMapDTO> getCurrentMap();

    ElytraRace getPlugin();

    LinearPhaseSeries<Phase> getElytraPhase();

    DatabaseService getDatabaseService();

    /**
     * Create a new instance of the service.
     *
     * @return The service.
     */
    @Contract(value = "_ -> new", pure = true)
    static @NotNull GameService create(@NotNull ElytraRace plugin) {
        return new GameServiceImpl(plugin);
    }

}
