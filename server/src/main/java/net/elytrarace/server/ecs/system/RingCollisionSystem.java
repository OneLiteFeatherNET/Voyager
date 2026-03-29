package net.elytrarace.server.ecs.system;

import net.elytrarace.common.ecs.Component;
import net.elytrarace.common.ecs.Entity;
import net.elytrarace.common.ecs.EntityManager;
import net.elytrarace.server.cup.MapDefinition;
import net.elytrarace.server.ecs.component.ActiveMapComponent;
import net.elytrarace.server.ecs.component.ElytraFlightComponent;
import net.elytrarace.server.ecs.component.PlayerRefComponent;
import net.elytrarace.server.ecs.component.RingTrackerComponent;
import net.elytrarace.server.ecs.component.ScoreComponent;
import net.elytrarace.server.physics.Ring;
import net.elytrarace.server.physics.RingCollisionDetector;
import net.minestom.server.coordinate.Vec;

import java.util.List;
import java.util.Set;

/**
 * Checks whether a player has flown through any ring checkpoint this tick.
 * <p>
 * For each ring on the active map that the player has not yet passed, this system
 * computes the predicted position (prevPos + velocity) and checks for a passthrough
 * using {@link RingCollisionDetector}. On a hit, the ring is marked as passed and
 * the corresponding points are added to the player's {@link ScoreComponent}.
 * <p>
 * Requires a game entity with an {@link ActiveMapComponent} to be present in the
 * {@link EntityManager} in order to know which rings to check against.
 */
public class RingCollisionSystem implements net.elytrarace.common.ecs.System {

    private final EntityManager entityManager;

    public RingCollisionSystem(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Set<Class<? extends Component>> getRequiredComponents() {
        return Set.of(PlayerRefComponent.class, RingTrackerComponent.class,
                ScoreComponent.class, ElytraFlightComponent.class);
    }

    @Override
    public void process(Entity entity, float deltaTime) {
        var playerRef = entity.getComponent(PlayerRefComponent.class);
        var tracker = entity.getComponent(RingTrackerComponent.class);
        var score = entity.getComponent(ScoreComponent.class);
        var flight = entity.getComponent(ElytraFlightComponent.class);

        if (!flight.isFlying()) {
            return;
        }

        MapDefinition map = findActiveMap();
        if (map == null) {
            return;
        }

        List<Ring> rings = map.rings();
        Vec currentPos = Vec.fromPoint(playerRef.getPlayer().getPosition());
        Vec prevPos = currentPos.sub(flight.getVelocity());

        for (int i = 0; i < rings.size(); i++) {
            if (tracker.hasPassed(i)) {
                continue;
            }

            Ring ring = rings.get(i);
            if (RingCollisionDetector.checkPassthrough(ring, prevPos, currentPos)) {
                tracker.markPassed(i);
                score.addRingPoints(ring.points());
            }
        }
    }

    private MapDefinition findActiveMap() {
        for (Entity entity : entityManager.getEntities()) {
            if (entity.hasComponent(ActiveMapComponent.class)) {
                ActiveMapComponent activeMap = entity.getComponent(ActiveMapComponent.class);
                return activeMap.getCurrentMap();
            }
        }
        return null;
    }
}
