package net.elytrarace.game.system;

import net.elytrarace.common.ecs.Component;
import net.elytrarace.common.ecs.Entity;
import net.elytrarace.common.ecs.System;
import net.elytrarace.game.components.SimpleSplineComponent;
import net.elytrarace.game.components.SimpleWorldComponent;
import net.elytrarace.game.util.ElytraMarkers;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.Set;

/**
 * System that manages splines and spawns particles along them using the flattened component architecture.
 */
public class SimpleSplineSystem implements System {

    private static final ComponentLogger LOGGER = ComponentLogger.logger(SimpleSplineSystem.class);

    @Override
    public Set<Class<? extends Component>> getRequiredComponents() {
        return Set.of(SimpleSplineComponent.class);
    }

    @Override
    public void process(Entity entity, float deltaTime) {
        SimpleSplineComponent splineComponent = entity.getComponent(SimpleSplineComponent.class);
        
        // Get the world name from the spline component
        String worldName = splineComponent.worldName();
        if (worldName == null || worldName.isEmpty()) {
            return;
        }
        
        // Get the world from Bukkit
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            // Check if the entity has a SimpleWorldComponent with the same world name
            if (entity.hasComponent(SimpleWorldComponent.class)) {
                SimpleWorldComponent worldComponent = entity.getComponent(SimpleWorldComponent.class);
                if (worldName.equals(worldComponent.worldName()) && !worldComponent.isLoaded()) {
                    // World is not loaded, so we can't spawn particles
                    return;
                }
            }
            
            // Try to load the world
            LOGGER.warn(ElytraMarkers.MAP, "World {} not found, trying to load it", worldName);
            world = Bukkit.createWorld(org.bukkit.WorldCreator.name(worldName));
            
            if (world == null) {
                LOGGER.error(ElytraMarkers.MAP, "Failed to load world {}", worldName);
                return;
            }
            
            // Update the world component if it exists
            if (entity.hasComponent(SimpleWorldComponent.class)) {
                SimpleWorldComponent worldComponent = entity.getComponent(SimpleWorldComponent.class);
                if (worldName.equals(worldComponent.worldName())) {
                    entity.removeComponent(SimpleWorldComponent.class);
                    entity.addComponent(worldComponent.withLoaded(true));
                }
            }
        }
        
        // Spawn particles along the spline
        int pointCount = splineComponent.getPointCount();
        for (int i = 0; i < pointCount; i++) {
            double x = splineComponent.xCoordinates().get(i);
            double y = splineComponent.yCoordinates().get(i);
            double z = splineComponent.zCoordinates().get(i);
            
            world.spawnParticle(
                splineComponent.particle(),
                x, y, z,
                splineComponent.particleCount(),
                splineComponent.particleOffset(),
                splineComponent.particleOffset(),
                splineComponent.particleOffset(),
                splineComponent.particleExtra()
            );
        }
    }
}