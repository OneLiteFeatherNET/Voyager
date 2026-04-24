package net.elytrarace.server.ecs.system;

import net.elytrarace.common.ecs.Component;
import net.elytrarace.common.ecs.Entity;
import net.elytrarace.common.map.model.GuidePointDTO;
import net.elytrarace.common.utils.SplineAPI;
import net.elytrarace.server.cup.MapDefinition;
import net.elytrarace.server.ecs.component.ActiveMapComponent;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Player;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.particle.Particle;
import org.apache.commons.geometry.euclidean.threed.Vector3D;

import java.util.*;

/**
 * Renders the ideal racing line as a spline curve using particles.
 * <p>
 * The spline is built from ring centers (using orderIndex = ringIndex * 100) merged
 * with guide points, then interpolated via Catmull-Rom. Particles are broadcast to
 * all online players every 20 ticks (1 second at 20 TPS).
 * <p>
 * This system operates on the game entity (which has {@link ActiveMapComponent}),
 * similar to {@link RingVisualizationSystem}.
 */
public class SplineVisualizationSystem implements net.elytrarace.common.ecs.System {

    private static final int TICK_INTERVAL = 5;
    private static final int POINTS_PER_SEGMENT = 24;
    private static final Particle SPLINE_PARTICLE = Particle.END_ROD;

    private int tickCounter;
    private List<Vec> cachedPath = List.of();
    private String cachedMapName = null;

    @Override
    public Set<Class<? extends Component>> getRequiredComponents() {
        return Set.of(ActiveMapComponent.class);
    }

    @Override
    public void process(Entity entity, float deltaTime) {
        tickCounter++;
        if (tickCounter < TICK_INTERVAL) return;
        tickCounter = 0;

        var activeMap = entity.getComponent(ActiveMapComponent.class);
        MapDefinition map = activeMap.getCurrentMap();
        if (map == null) return;

        // Recompute spline only when map changes
        if (!map.name().equals(cachedMapName)) {
            cachedMapName = map.name();
            cachedPath = computeSpline(map);
        }

        if (cachedPath.isEmpty()) return;

        Collection<Player> players = MinecraftServer.getConnectionManager().getOnlinePlayers();
        if (players.isEmpty()) return;

        for (Vec point : cachedPath) {
            var packet = new ParticlePacket(SPLINE_PARTICLE,
                    point.x(), point.y(), point.z(),
                    0.1f, 0.1f, 0.1f, 0f, 3);
            for (Player player : players) {
                player.sendPacket(packet);
            }
        }
    }

    private List<Vec> computeSpline(MapDefinition map) {
        // Merge ring centers (orderIndex = ringIndex*100) and guide points, sorted
        record CP(int order, Vec vec) {}
        var cps = new ArrayList<CP>();
        var rings = map.rings();
        for (int i = 0; i < rings.size(); i++) {
            cps.add(new CP(i * 100, rings.get(i).center()));
        }
        for (GuidePointDTO gp : map.guidePoints()) {
            cps.add(new CP(gp.orderIndex(), new Vec(gp.x(), gp.y(), gp.z())));
        }
        cps.sort(Comparator.comparingInt(CP::order));

        if (cps.size() < 2) {
            return cps.stream().map(CP::vec).toList();
        }

        // Build Vector3D list with ghost endpoints for Catmull-Rom
        var v3d = new ArrayList<Vector3D>();
        v3d.add(toV3D(cps.getFirst().vec()));           // ghost start
        for (CP cp : cps) v3d.add(toV3D(cp.vec()));
        v3d.add(toV3D(cps.getLast().vec()));             // ghost end

        if (v3d.size() < 4) {
            return cps.stream().map(CP::vec).toList();
        }

        var result = new ArrayList<Vec>();
        for (int i = 0; i <= v3d.size() - 4; i++) {
            for (Vector3D p : SplineAPI.interpolate(v3d, i, POINTS_PER_SEGMENT)) {
                result.add(new Vec(p.getX(), p.getY(), p.getZ()));
            }
        }
        return result;
    }

    private static Vector3D toV3D(Vec v) {
        return Vector3D.of(v.x(), v.y(), v.z());
    }
}
