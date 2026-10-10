package net.elytrarace.game.system;

import net.elytrarace.common.ecs.Component;
import net.elytrarace.common.ecs.Entity;
import net.elytrarace.common.ecs.System;
import net.elytrarace.game.components.SplineComponent;
import net.elytrarace.game.components.WorldComponent;
import net.elytrarace.game.util.ElytraMarkers;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.bukkit.World;

import java.util.Set;

/**
 * System that manages splines and spawns particles along them.
 */
public class SplineSystem implements System {

    private static final ComponentLogger LOGGER = ComponentLogger.logger(SplineSystem.class);

    @Override
    public Set<Class<? extends Component>> getRequiredComponents() {
        return Set.of(SplineComponent.class);
    }

    @Override
    public void process(Entity entity, float deltaTime) {
        SplineComponent splineComponent = entity.getComponent(SplineComponent.class);
        
        // If the entity has a WorldComponent, use that world
        if (entity.hasComponent(WorldComponent.class)) {
            WorldComponent worldComponent = entity.getComponent(WorldComponent.class);
            worldComponent.getWorld().ifPresent(splineComponent::spawnParticles);
        } else {
            // Otherwise, try to get the world from the first spline point
            var splinePoints = splineComponent.getSplinePoints();
            if (splinePoints != null && !splinePoints.isEmpty()) {
                var firstPoint = splinePoints.get(0);
                if (firstPoint != null) {
                    World world = firstPoint.getWorld();
                    if (world != null) {
                        splineComponent.spawnParticles(world);
                    }
                }
            }
        }
    }
}