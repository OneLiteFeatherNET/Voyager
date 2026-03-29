package net.elytrarace.setup.preview;

import net.elytrarace.common.map.model.PortalDTO;
import net.elytrarace.spline.SplineConfig;
import net.elytrarace.spline.SplineGenerator;
import org.apache.commons.geometry.euclidean.threed.Vector3D;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;

/**
 * Paper-side spline rendering. Uses {@link SplineGenerator} from shared/spline
 * for point generation, renders as Bukkit particles.
 */
public final class SplineRenderer {

    private static final float PARTICLE_SIZE = 0.5f;
    private static final Color SPLINE_COLOR = Color.fromRGB(255, 140, 0); // orange

    private SplineRenderer() {}

    /**
     * Generates spline points from portals with builder preview density.
     */
    public static List<Vector3D> generateSplinePoints(Collection<? extends PortalDTO> portals) {
        return SplineGenerator.generate(portals, SplineConfig.BUILDER);
    }

    /**
     * Renders pre-computed spline points as orange dust particles visible to one player.
     */
    public static void renderSpline(Player player, List<Vector3D> splinePoints) {
        if (splinePoints.isEmpty()) return;

        var dustOptions = new Particle.DustOptions(SPLINE_COLOR, PARTICLE_SIZE);
        for (var point : splinePoints) {
            player.spawnParticle(Particle.DUST,
                    point.getX(), point.getY(), point.getZ(),
                    1, 0, 0, 0, 0, dustOptions);
        }
    }
}
