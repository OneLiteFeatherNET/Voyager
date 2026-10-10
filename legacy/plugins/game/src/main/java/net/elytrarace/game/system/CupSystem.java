package net.elytrarace.game.system;

import net.elytrarace.common.cup.model.CupDTO;
import net.elytrarace.common.cup.model.ResolvedCupDTO;
import net.elytrarace.common.ecs.Component;
import net.elytrarace.common.ecs.Entity;
import net.elytrarace.common.ecs.System;
import net.elytrarace.game.components.CupComponent;
import net.elytrarace.game.components.GameStateComponent;
import net.elytrarace.game.model.GameMapDTO;
import net.elytrarace.game.model.GamePortalDTO;
import net.elytrarace.game.model.GameSession;
import net.elytrarace.game.util.ElytraMarkers;
import net.elytrarace.game.util.PluginInstanceHolder;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.kyori.adventure.util.TriState;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * System that manages cups and maps.
 */
public class CupSystem implements System {

    private static final ComponentLogger LOGGER = ComponentLogger.logger(CupSystem.class);

    @Override
    public Set<Class<? extends Component>> getRequiredComponents() {
        return Set.of(CupComponent.class);
    }

    @Override
    public void process(Entity entity, float deltaTime) {
        // This system doesn't need to do anything on regular updates
    }

    /**
     * Loads the worlds for all maps in a cup.
     * 
     * @param entity The entity with the CupComponent
     * @return A CompletableFuture that completes when all worlds are loaded
     */
    public CompletableFuture<Void> loadWorlds(Entity entity) {
        if (!entity.hasComponent(CupComponent.class)) {
            return CompletableFuture.completedFuture(null);
        }

        CupComponent cupComponent = entity.getComponent(CupComponent.class);
        var maps = cupComponent.getMaps();
        
        var allBukkitMapsLoaded = maps.stream().map(map -> {
            LOGGER.info("Loading map: {}", map.name());
            return CompletableFuture.completedFuture(map)
                    .thenApplyAsync(mapDTO -> WorldCreator
                            .name(mapDTO.world())
                            .generator("ElytraRace")
                            .environment(World.Environment.NORMAL)
                            .type(WorldType.FLAT)
                            .keepSpawnLoaded(TriState.FALSE)
                            .createWorld(), Bukkit.getScheduler().getMainThreadExecutor(PluginInstanceHolder.getPluginInstance()))
                    .thenAccept(world -> LOGGER.info(ElytraMarkers.MAP, "Loaded world: {}", world.getName()))
                    .exceptionally(throwable -> {
                        LOGGER.error(ElytraMarkers.EXCEPTION, "An error occurred while loading the world", throwable);
                        return null;
                    });
        }).toArray(CompletableFuture[]::new);
        
        return CompletableFuture.allOf(allBukkitMapsLoaded);
    }

    /**
     * Switches to the next map in the cup.
     * 
     * @param entity The entity with the CupComponent and GameStateComponent
     * @return A CompletableFuture that completes with the updated GameSession
     */
    public CompletableFuture<GameSession> switchMap(Entity entity) {
        if (!entity.hasComponent(CupComponent.class) || !entity.hasComponent(GameStateComponent.class)) {
            return CompletableFuture.completedFuture(null);
        }

        CupComponent cupComponent = entity.getComponent(CupComponent.class);
        GameStateComponent gameStateComponent = entity.getComponent(GameStateComponent.class);
        GameSession gameSession = gameStateComponent.gameSession();
        
        return CompletableFuture.completedFuture(gameSession)
                .thenApply(this::switchMapInternal)
                .thenApply(updatedSession -> {
                    // Update the game state component
                    GameStateComponent updatedComponent = gameStateComponent.withGameSession(updatedSession);
                    entity.removeComponent(GameStateComponent.class);
                    entity.addComponent(updatedComponent);
                    return updatedSession;
                });
    }

    /**
     * Internal method to switch to the next map in the cup.
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