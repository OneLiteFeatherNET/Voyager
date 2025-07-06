package net.elytrarace.game.components;

import net.elytrarace.common.ecs.Component;
import org.bukkit.Particle;

import java.util.List;

/**
 * Component that stores spline data directly.
 * This is part of the flattened architecture, replacing the SplineComponent that contains Bukkit Location objects and behavior.
 */
public record SimpleSplineComponent(
    List<Double> xCoordinates,
    List<Double> yCoordinates,
    List<Double> zCoordinates,
    String worldName,
    Particle particle,
    int particleCount,
    double particleOffset,
    double particleExtra
) implements Component {
    
    /**
     * Creates a new SimpleSplineComponent with the given coordinates and particle settings.
     */
    public static SimpleSplineComponent create(
        List<Double> xCoordinates,
        List<Double> yCoordinates,
        List<Double> zCoordinates,
        String worldName,
        Particle particle,
        int particleCount,
        double particleOffset,
        double particleExtra
    ) {
        return new SimpleSplineComponent(
            xCoordinates,
            yCoordinates,
            zCoordinates,
            worldName,
            particle,
            particleCount,
            particleOffset,
            particleExtra
        );
    }
    
    /**
     * Creates a new SimpleSplineComponent with the given coordinates and default particle settings.
     */
    public static SimpleSplineComponent create(
        List<Double> xCoordinates,
        List<Double> yCoordinates,
        List<Double> zCoordinates,
        String worldName
    ) {
        return new SimpleSplineComponent(
            xCoordinates,
            yCoordinates,
            zCoordinates,
            worldName,
            Particle.FLAME,
            1,
            0.0,
            0.0
        );
    }
    
    /**
     * Gets the number of points in the spline.
     */
    public int getPointCount() {
        return Math.min(Math.min(xCoordinates.size(), yCoordinates.size()), zCoordinates.size());
    }
    
    /**
     * Creates a new SimpleSplineComponent with updated particle settings.
     */
    public SimpleSplineComponent withParticleSettings(Particle particle, int particleCount, double particleOffset, double particleExtra) {
        return new SimpleSplineComponent(
            xCoordinates,
            yCoordinates,
            zCoordinates,
            worldName,
            particle,
            particleCount,
            particleOffset,
            particleExtra
        );
    }
}