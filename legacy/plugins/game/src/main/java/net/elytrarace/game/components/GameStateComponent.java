package net.elytrarace.game.components;

import net.elytrarace.common.cup.model.CupDTO;
import net.elytrarace.common.cup.model.ResolvedCupDTO;
import net.elytrarace.common.ecs.Component;
import net.elytrarace.game.model.GameMapDTO;
import net.elytrarace.game.model.GameSession;

import java.util.Optional;
import java.util.UUID;

/**
 * Component that stores the current game state.
 */
public record GameStateComponent(GameSession gameSession) implements Component {
    
    /**
     * Creates a new GameStateComponent with a new game session.
     */
    public static GameStateComponent create() {
        return new GameStateComponent(new GameSession(UUID.randomUUID(), null, null));
    }
    
    /**
     * Creates a new GameStateComponent with an updated current cup.
     */
    public GameStateComponent withCurrentCup(ResolvedCupDTO cup) {
        return new GameStateComponent(GameSession.fromWithCurrentCup(gameSession, cup));
    }
    
    /**
     * Creates a new GameStateComponent with an updated game session.
     */
    public GameStateComponent withGameSession(GameSession gameSession) {
        return new GameStateComponent(gameSession);
    }
    
    /**
     * Gets the current cup, if any.
     */
    public Optional<CupDTO> getCurrentCup() {
        return Optional.ofNullable(gameSession.currentCup());
    }
    
    /**
     * Gets the current map, if any.
     */
    public Optional<GameMapDTO> getCurrentMap() {
        return Optional.ofNullable(gameSession.currentMap());
    }
}