package net.elytrarace.game.system;

import net.elytrarace.common.cup.model.ResolvedCupDTO;
import net.elytrarace.common.ecs.Component;
import net.elytrarace.common.ecs.Entity;
import net.elytrarace.common.ecs.System;
import net.elytrarace.game.components.GameStateComponent;
import net.elytrarace.game.model.GameMapDTO;
import net.elytrarace.game.model.GamePortalDTO;
import net.elytrarace.game.model.GameSession;
import net.elytrarace.game.util.ElytraMarkers;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.bukkit.Bukkit;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * System that manages the game state.
 */
public class GameStateSystem implements System {

    private static final ComponentLogger LOGGER = ComponentLogger.logger(GameStateSystem.class);

    @Override
    public Set<Class<? extends Component>> getRequiredComponents() {
        return Set.of(GameStateComponent.class);
    }

    @Override
    public void process(Entity entity, float deltaTime) {
        // This system doesn't need to do anything on regular updates
    }

    /**
     * Sets the current cup for the game state entity.
     */
    public void setCurrentCup(Entity gameStateEntity, ResolvedCupDTO cup) {
        GameStateComponent component = gameStateEntity.getComponent(GameStateComponent.class);
        GameStateComponent updatedComponent = component.withCurrentCup(cup);
        gameStateEntity.removeComponent(GameStateComponent.class);
        gameStateEntity.addComponent(updatedComponent);

        LOGGER.info(ElytraMarkers.CUP, "The current cup has been set to: {}", updatedComponent.gameSession().currentCup().name());
    }

    /**
     * Switches to the next map in the current cup.
     */
    public CompletableFuture<GameSession> switchMap(Entity gameStateEntity) {
        GameStateComponent component = gameStateEntity.getComponent(GameStateComponent.class);
        return CompletableFuture.completedFuture(component.gameSession())
                .thenApplyAsync(this::switchMapInternal)
                .thenApply(gameSession -> {
                    GameStateComponent updatedComponent = component.withGameSession(gameSession);
                    gameStateEntity.removeComponent(GameStateComponent.class);
                    gameStateEntity.addComponent(updatedComponent);
                    return gameSession;
                });
    }

    /**
     * Internal method to switch to the next map in the current cup.
     */
    private GameSession switchMapInternal(GameSession gameSession) {
        if (gameSession.currentCup() == null) {
            LOGGER.error(ElytraMarkers.CUP, "No cup has been set, shutting down server...");
            Bukkit.shutdown();
            return null;
        }
        if (gameSession.currentMap() == null) {
            return GameSession.fromWithCurrentMap(gameSession, (GameMapDTO) gameSession.currentCup().maps().getFirst());
        }
        var currentMap = gameSession.currentMap();
        currentMap.portals().stream().filter(Objects::nonNull).forEach(GamePortalDTO::despawn);
        var index = gameSession.currentCup().maps().indexOf(currentMap);
        var nextIndex = index + 1;
        if (nextIndex >= gameSession.currentCup().maps().size()) {
            return GameSession.fromWithCurrentMap(gameSession, null);
        }
        return GameSession.fromWithCurrentMap(gameSession, (GameMapDTO) gameSession.currentCup().maps().get(nextIndex));
    }
}
