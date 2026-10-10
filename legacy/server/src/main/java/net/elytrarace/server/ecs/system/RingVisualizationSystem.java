package net.elytrarace.server.ecs.system;

import net.elytrarace.common.ecs.Component;
import net.elytrarace.common.ecs.Entity;
import net.elytrarace.common.ecs.EntityManager;
import net.elytrarace.server.cup.MapDefinition;
import net.elytrarace.server.ecs.component.ActiveMapComponent;
import net.elytrarace.server.physics.Ring;
import net.elytrarace.server.physics.RingType;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Player;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.particle.Particle;

import java.util.List;
import java.util.Set;

/**
 * Spawns particles at ring positions each tick so players can see where to fly.
 * <p>
 * Each ring is rendered as a circle of particles around its center, oriented along
 * the ring's normal vector. Different ring types use different particle types for
 * visual distinction:
 * <ul>
 *   <li>{@link RingType#STANDARD} — {@code END_ROD} (white/subtle)</li>
 *   <li>{@link RingType#BOOST} — {@code FLAME} (orange/fiery)</li>
 *   <li>{@link RingType#CHECKPOINT} — {@code COMPOSTER} (green)</li>
 *   <li>{@link RingType#SLOW} — {@code SNOWFLAKE} (blue/cold)</li>
 *   <li>{@link RingType#BONUS} — {@code ENCHANT} (purple/magical)</li>
 * </ul>
 * <p>
 * This system operates on the game entity (which has {@link ActiveMapComponent})
 * rather than on player entities. It broadcasts particle packets to all online
 * players once per second (every 20 ticks).
 */
public class RingVisualizationSystem implements net.elytrarace.common.ecs.System {

    /** Number of particles per ring circle. */
    public static final int PARTICLES_PER_RING = 16;

    /** Only spawn particles every N ticks to reduce bandwidth (1 second at 20 TPS). */
    private static final int TICK_INTERVAL = 20;

    private final EntityManager entityManager;
    private int tickCounter;

    public RingVisualizationSystem(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Set<Class<? extends Component>> getRequiredComponents() {
        return Set.of(ActiveMapComponent.class);
    }

    @Override
    public void process(Entity entity, float deltaTime) {
        tickCounter++;
        if (tickCounter < TICK_INTERVAL) {
            return;
        }
        tickCounter = 0;

        var activeMap = entity.getComponent(ActiveMapComponent.class);
        MapDefinition map = activeMap.getCurrentMap();
        if (map == null) {
            return;
        }

        var players = MinecraftServer.getConnectionManager().getOnlinePlayers();
        if (players.isEmpty()) {
            return;
        }

        List<Ring> rings = map.rings();
        for (Ring ring : rings) {
            spawnRingParticles(ring, players);
        }
    }

    private void spawnRingParticles(Ring ring, java.util.Collection<Player> players) {
        Vec center = ring.center();
        Vec normal = ring.normal().normalize();
        double radius = ring.radius();

        // Build two orthogonal vectors in the ring plane
        Vec arbitrary = Math.abs(normal.y()) < 0.9 ? new Vec(0, 1, 0) : new Vec(1, 0, 0);
        Vec u = normal.cross(arbitrary).normalize();
        Vec v = normal.cross(u).normalize();

        Particle particle = particleForType(ring.type());

        for (int i = 0; i < PARTICLES_PER_RING; i++) {
            double angle = 2.0 * Math.PI * i / PARTICLES_PER_RING;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);

            double x = center.x() + radius * (cos * u.x() + sin * v.x());
            double y = center.y() + radius * (cos * u.y() + sin * v.y());
            double z = center.z() + radius * (cos * u.z() + sin * v.z());

            var packet = new ParticlePacket(
                    particle,
                    x, y, z,
                    0f, 0f, 0f,  // offset
                    0f,           // speed
                    1             // count
            );

            for (Player player : players) {
                player.sendPacket(packet);
            }
        }
    }

    private static Particle particleForType(RingType type) {
        return switch (type) {
            case STANDARD -> Particle.END_ROD;
            case BOOST -> Particle.FLAME;
            case CHECKPOINT -> Particle.COMPOSTER;
            case SLOW -> Particle.SNOWFLAKE;
            case BONUS -> Particle.ENCHANT;
        };
    }
}
