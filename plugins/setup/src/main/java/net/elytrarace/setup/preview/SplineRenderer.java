package net.elytrarace.setup.preview;

import net.elytrarace.common.map.model.GuidePointDTO;
import net.elytrarace.common.map.model.PortalDTO;
import net.elytrarace.spline.PathPoint;
import net.elytrarace.spline.SplineGenerator;
import org.apache.commons.geometry.euclidean.threed.Vector3D;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Paper-side spline rendering. Builds PathPoints from portals + guide points,
 * delegates generation to shared/spline, renders as Bukkit particles.
 */
public final class SplineRenderer {

    private static final float SPLINE_PARTICLE_SIZE = 0.5f;
    private static final float GUIDE_PARTICLE_SIZE = 0.8f;
    private static final Color SPLINE_COLOR = Color.fromRGB(255, 140, 0); // orange
    private static final Color GUIDE_COLOR = Color.fromRGB(0, 255, 200);  // teal

    private SplineRenderer() {}

    /**
     * Builds a merged PathPoint list from portals and guide points, then generates the spline.
     */
    public static List<Vector3D> generateSplinePoints(Collection<? extends PortalDTO> portals,
                                                      List<GuidePointDTO> guidePoints) {
        var pathPoints = buildPathPoints(portals, guidePoints);
        if (pathPoints.size() < 2) return List.of();
        return SplineGenerator.generate(pathPoints);
    }

    /**
     * Overload for portal-only (no guide points).
     */
    public static List<Vector3D> generateSplinePoints(Collection<? extends PortalDTO> portals) {
        return generateSplinePoints(portals, List.of());
    }

    /**
     * Renders the spline path as orange particles.
     */
    public static void renderSpline(Player player, List<Vector3D> splinePoints) {
        if (splinePoints.isEmpty()) return;
        var dust = new Particle.DustOptions(SPLINE_COLOR, SPLINE_PARTICLE_SIZE);
        for (var point : splinePoints) {
            player.spawnParticle(Particle.DUST,
                    point.getX(), point.getY(), point.getZ(),
                    1, 0, 0, 0, 0, dust);
        }
    }

    /**
     * Renders guide point markers as teal particles (distinct from the spline line).
     */
    public static void renderGuidePoints(Player player, List<GuidePointDTO> guidePoints) {
        if (guidePoints.isEmpty()) return;
        var dust = new Particle.DustOptions(GUIDE_COLOR, GUIDE_PARTICLE_SIZE);
        for (var guide : guidePoints) {
            player.spawnParticle(Particle.DUST,
                    guide.x(), guide.y(), guide.z(),
                    5, 0.15, 0.15, 0.15, 0, dust);
        }
    }

    /**
     * Merges portals and guide points into a single sorted PathPoint list.
     */
    static List<PathPoint> buildPathPoints(Collection<? extends PortalDTO> portals,
                                           List<GuidePointDTO> guidePoints) {
        var points = new ArrayList<PathPoint>();

        // Add portal centers
        points.addAll(SplineGenerator.portalsToPathPoints(portals));

        // Add guide points
        for (var guide : guidePoints) {
            points.add(new PathPoint.GuidePoint(
                    Vector3D.of(guide.x(), guide.y(), guide.z()),
                    guide.orderIndex()));
        }

        points.sort(Comparator.comparingInt(PathPoint::orderIndex));
        return points;
    }
}
