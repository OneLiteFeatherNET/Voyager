package net.elytrarace.game.components;

import net.elytrarace.common.ecs.Component;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;

import java.util.List;

/**
 * Component that stores spline-related data.
 */
public record SplineComponent(List<Location> splinePoints, Particle particle, int particleCount, double particleOffset, double particleExtra) implements Component {
    
    /**
     * Creates a new SplineComponent with the given spline points and default particle settings.
     */
    public static SplineComponent create(List<Location> splinePoints) {
        return new SplineComponent(splinePoints, Particle.FLAME, 1, 0.0, 0.0);
    }
    
    /**
     * Creates a new SplineComponent with the given spline points and particle settings.
     */
    public static SplineComponent create(List<Location> splinePoints, Particle particle, int particleCount, double particleOffset, double particleExtra) {
        return new SplineComponent(splinePoints, particle, particleCount, particleOffset, particleExtra);
    }
    
    /**
     * Gets the spline points.
     */
    public List<Location> getSplinePoints() {
        return splinePoints;
    }
    
    /**
     * Gets the particle type.
     */
    public Particle getParticle() {
        return particle;
    }
    
    /**
     * Gets the particle count.
     */
    public int getParticleCount() {
        return particleCount;
    }
    
    /**
     * Gets the particle offset.
     */
    public double getParticleOffset() {
        return particleOffset;
    }
    
    /**
     * Gets the particle extra.
     */
    public double getParticleExtra() {
        return particleExtra;
    }
    
    /**
     * Creates a new SplineComponent with updated spline points.
     */
    public SplineComponent withSplinePoints(List<Location> splinePoints) {
        return new SplineComponent(splinePoints, particle, particleCount, particleOffset, particleExtra);
    }
    
    /**
     * Creates a new SplineComponent with updated particle settings.
     */
    public SplineComponent withParticleSettings(Particle particle, int particleCount, double particleOffset, double particleExtra) {
        return new SplineComponent(splinePoints, particle, particleCount, particleOffset, particleExtra);
    }
    
    /**
     * Spawns particles along the spline in the given world.
     */
    public void spawnParticles(World world) {
        if (world == null || splinePoints == null || splinePoints.isEmpty()) {
            return;
        }
        
        for (Location location : splinePoints) {
            world.spawnParticle(
                    particle,
                    location.getX(),
                    location.getY(),
                    location.getZ(),
                    particleCount,
                    particleOffset,
                    particleOffset,
                    particleOffset,
                    particleExtra
            );
        }
    }
}