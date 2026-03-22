package net.elytrarace.game.system;

import net.elytrarace.common.ecs.Component;
import net.elytrarace.common.ecs.Entity;
import net.elytrarace.common.ecs.System;
import net.elytrarace.game.components.CurrentCupComponent;
import net.elytrarace.game.components.CurrentMapComponent;
import net.elytrarace.game.components.SimpleCupComponent;
import net.elytrarace.game.components.SimpleWorldComponent;
import net.elytrarace.game.util.ElytraMarkers;
import net.elytrarace.game.util.PluginInstanceHolder;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * System that manages cups and maps using the flattened component architecture.
 */
public class SimpleCupSystem implements System {

    private static final ComponentLogger LOGGER = ComponentLogger.logger(SimpleCupSystem.class);

    @Override
    public Set<Class<? extends Component>> getRequiredComponents() {
        return Set.of(SimpleCupComponent.class);
    }

    @Override
    public void process(Entity entity, float deltaTime) {
        // This system doesn't need to do anything on regular updates
    }

    /**
     * Loads the worlds for all maps in a cup.
     * 
     * @param entity The entity with the SimpleCupComponent
     * @return A CompletableFuture that completes when all worlds are loaded
     */
    public CompletableFuture<Void> loadWorlds(Entity entity) {
        if (!entity.hasComponent(SimpleCupComponent.class)) {
            return CompletableFuture.completedFuture(null);
        }

        SimpleCupComponent cupComponent = entity.getComponent(SimpleCupComponent.class);
        List<Key> mapIds = cupComponent.getMapIds();
        
        var allBukkitMapsLoaded = mapIds.stream().map(mapId -> {
            String worldName = "world_" + mapId.value();
            LOGGER.info("Loading world: {}", worldName);
            
            return CompletableFuture.completedFuture(worldName)
                    .thenApplyAsync(name -> WorldCreator
                            .name(name)
                            .generator("ElytraRace")
                            .environment(World.Environment.NORMAL)
                            .type(WorldType.FLAT)
                            .createWorld(), Bukkit.getScheduler().getMainThreadExecutor(PluginInstanceHolder.getPluginInstance()))
                    .thenApply(world -> {
                        LOGGER.info(ElytraMarkers.MAP, "Loaded world: {}", world.getName());
                        
                        // Update or add the world component
                        SimpleWorldComponent worldComponent = SimpleWorldComponent.create(world.getName(), true);
                        if (entity.hasComponent(SimpleWorldComponent.class)) {
                            SimpleWorldComponent existingComponent = entity.getComponent(SimpleWorldComponent.class);
                            if (existingComponent.worldName().equals(world.getName())) {
                                entity.removeComponent(SimpleWorldComponent.class);
                                entity.addComponent(worldComponent);
                            }
                        }
                        
                        return world;
                    })
                    .exceptionally(throwable -> {
                        LOGGER.error(ElytraMarkers.EXCEPTION, "An error occurred while loading the world", throwable);
                        return null;
                    });
        }).toArray(CompletableFuture[]::new);
        
        return CompletableFuture.allOf(allBukkitMapsLoaded);
    }

    /**
     * Sets the current cup for the game state entity.
     * 
     * @param gameStateEntity The game state entity
     * @param cupEntity The entity with the SimpleCupComponent
     */
    public void setCurrentCup(Entity gameStateEntity, Entity cupEntity) {
        if (!cupEntity.hasComponent(SimpleCupComponent.class)) {
            LOGGER.error(ElytraMarkers.CUP, "Cup entity does not have a SimpleCupComponent");
            return;
        }
        
        SimpleCupComponent cupComponent = cupEntity.getComponent(SimpleCupComponent.class);
        
        // Find the SimpleGameStateSystem
        SimpleGameStateSystem gameStateSystem = findGameStateSystem();
        if (gameStateSystem == null) {
            LOGGER.error(ElytraMarkers.CUP, "Could not find SimpleGameStateSystem");
            return;
        }
        
        // Set the current cup
        gameStateSystem.setCurrentCup(gameStateEntity, cupComponent.getCupId(), cupComponent.getDisplayName(), cupComponent.getMapIds());
    }
    
    /**
     * Finds the SimpleGameStateSystem in the entity manager.
     */
    private SimpleGameStateSystem findGameStateSystem() {
        var entityManager = PluginInstanceHolder.getEntityManager();
        if (entityManager == null) {
            return null;
        }
        
        for (var system : entityManager.getSystems()) {
            if (system instanceof SimpleGameStateSystem) {
                return (SimpleGameStateSystem) system;
            }
        }
        
        return null;
    }
}