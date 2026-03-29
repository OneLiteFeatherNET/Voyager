package net.elytrarace.setup.preview;

import net.elytrarace.common.map.model.LocationDTO;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility for rendering portal outlines as particles.
 */
public final class ParticleRenderer {

    private static final int POINTS_PER_EDGE = 8;
    private static final float PARTICLE_SIZE = 0.8f;
    private static final float CENTER_PARTICLE_SIZE = 1.2f;

    /** Colors cycling by portal index for visual distinction. */
    private static final Color[] EDGE_COLORS = {
            Color.fromRGB(0, 200, 255),   // cyan
            Color.fromRGB(0, 255, 100),   // green
            Color.fromRGB(255, 200, 0),   // yellow
            Color.fromRGB(255, 100, 0),   // orange
            Color.fromRGB(200, 0, 255),   // purple
            Color.fromRGB(255, 50, 50),   // red
            Color.fromRGB(50, 150, 255),  // blue
            Color.fromRGB(255, 255, 100), // light yellow
    };

    private static final Color CENTER_COLOR = Color.fromRGB(255, 255, 255); // white

    private ParticleRenderer() {}

    /**
     * Renders a single portal's outline as particles visible only to the given player.
     *
     * @param player      the player to show particles to
     * @param locations   the portal's LocationDTOs (vertices + center)
     * @param portalIndex the portal index (used for color cycling)
     */
    public static void renderPortal(Player player, List<LocationDTO> locations, int portalIndex) {
        var vertices = locations.stream().filter(loc -> !loc.center()).toList();
        var center = locations.stream().filter(LocationDTO::center).findFirst().orElse(null);

        if (vertices.size() < 2) return;

        var color = EDGE_COLORS[portalIndex % EDGE_COLORS.length];
        var dustOptions = new Particle.DustOptions(color, PARTICLE_SIZE);

        // Draw edges between consecutive vertices (closed polygon)
        for (int i = 0; i < vertices.size(); i++) {
            var from = vertices.get(i);
            var to = vertices.get((i + 1) % vertices.size());
            renderEdge(player, from, to, dustOptions);
        }

        // Draw center point with distinct color
        if (center != null) {
            var centerDust = new Particle.DustOptions(CENTER_COLOR, CENTER_PARTICLE_SIZE);
            player.spawnParticle(
                    Particle.DUST,
                    center.x() + 0.5, center.y() + 0.5, center.z() + 0.5,
                    3, 0.1, 0.1, 0.1, 0, centerDust
            );
        }
    }

    /**
     * Renders a line of particles between two points.
     */
    static void renderEdge(Player player, LocationDTO from, LocationDTO to, Particle.DustOptions dustOptions) {
        var points = interpolate(from, to, POINTS_PER_EDGE);
        for (var point : points) {
            player.spawnParticle(
                    Particle.DUST,
                    point[0], point[1], point[2],
                    1, 0, 0, 0, 0, dustOptions
            );
        }
    }

    /**
     * Interpolates N evenly spaced points between two LocationDTOs.
     * Returns a list of [x, y, z] arrays (doubles, block-centered at +0.5).
     */
    static List<double[]> interpolate(LocationDTO from, LocationDTO to, int steps) {
        if (steps <= 1) {
            return List.of(new double[]{from.x() + 0.5, from.y() + 0.5, from.z() + 0.5});
        }
        var points = new ArrayList<double[]>(steps);
        for (int i = 0; i < steps; i++) {
            double t = (double) i / (steps - 1);
            points.add(new double[]{
                    from.x() + 0.5 + (to.x() - from.x()) * t,
                    from.y() + 0.5 + (to.y() - from.y()) * t,
                    from.z() + 0.5 + (to.z() - from.z()) * t
            });
        }
        return points;
    }
}
