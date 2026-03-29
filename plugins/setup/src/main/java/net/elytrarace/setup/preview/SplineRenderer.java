package net.elytrarace.setup.preview;

import net.elytrarace.common.map.model.GuidePointDTO;
import net.elytrarace.common.map.model.PortalDTO;
import net.elytrarace.spline.PathPoint;
import net.elytrarace.spline.SplineConfig;
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

    private static final float GUIDE_PARTICLE_SIZE = 1.0f;
    private static final Color GUIDE_COLOR = Color.fromRGB(0, 255, 200);  // teal

    private SplineRenderer() {}

    /**
     * Builds a merged PathPoint list from portals and guide points, then generates the spline.
     */
    public static List<Vector3D> generateSplinePoints(Collection<? extends PortalDTO> portals,
                                                      List<GuidePointDTO> guidePoints,
                                                      SplineConfig config) {
        var pathPoints = buildPathPoints(portals, guidePoints);
        if (pathPoints.size() < 2) return List.of();
        var segments = SplineGenerator.compile(pathPoints);
        return SplineGenerator.sampleUniform(segments, config);
    }

    /**
     * Overload with default builder config.
     */
    public static List<Vector3D> generateSplinePoints(Collection<? extends PortalDTO> portals,
                                                      List<GuidePointDTO> guidePoints) {
        return generateSplinePoints(portals, guidePoints, SplineConfig.BUILDER);
    }

    /**
     * Renders the spline path using the config's color and size.
     */
    public static void renderSpline(Player player, List<Vector3D> splinePoints, SplineConfig config) {
        if (splinePoints.isEmpty()) return;
        var dust = new Particle.DustOptions(
                Color.fromRGB(config.colorR(), config.colorG(), config.colorB()),
                config.particleSize());
        for (var point : splinePoints) {
            player.spawnParticle(Particle.DUST,
                    point.getX(), point.getY(), point.getZ(),
                    1, 0, 0, 0, 0, dust);
        }
    }

    /**
     * Renders guide point markers as teal particles.
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
        points.addAll(SplineGenerator.portalsToPathPoints(portals));
        for (var guide : guidePoints) {
            points.add(new PathPoint.GuidePoint(
                    Vector3D.of(guide.x(), guide.y(), guide.z()),
                    guide.orderIndex()));
        }
        points.sort(Comparator.comparingInt(PathPoint::orderIndex));
        return points;
    }
}
