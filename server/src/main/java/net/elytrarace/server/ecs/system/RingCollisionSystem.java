package net.elytrarace.server.ecs.system;

import net.elytrarace.common.ecs.Component;
import net.elytrarace.common.ecs.Entity;
import net.elytrarace.common.ecs.EntityManager;
import net.elytrarace.server.cup.MapDefinition;
import net.elytrarace.server.ecs.component.ActiveMapComponent;
import net.elytrarace.server.ecs.component.ElytraFlightComponent;
import net.elytrarace.server.ecs.component.PlayerRefComponent;
import net.elytrarace.server.ecs.component.RingEffectComponent;
import net.elytrarace.server.ecs.component.RingTrackerComponent;
import net.elytrarace.server.ecs.component.ScoreComponent;
import net.elytrarace.server.physics.Ring;
import net.elytrarace.server.physics.RingCollisionDetector;
import net.elytrarace.server.physics.RingType;
import net.elytrarace.server.ui.GameHudManager;
import net.minestom.server.coordinate.Vec;
import org.jetbrains.annotations.Nullable;

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
    private final @Nullable GameHudManager hudManager;

    public RingCollisionSystem(EntityManager entityManager) {
        this(entityManager, null);
    }

    public RingCollisionSystem(EntityManager entityManager, @Nullable GameHudManager hudManager) {
        this.entityManager = entityManager;
        this.hudManager = hudManager;
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
        var pos = playerRef.getPlayer().getPosition();
        Vec currentPos = new Vec(pos.x(), pos.y(), pos.z());
        Vec prevPos = currentPos.sub(flight.getVelocity());

        int nextIndex = tracker.passedCount();
        if (nextIndex >= rings.size()) {
            return;
        }

        Ring ring = rings.get(nextIndex);
        if (RingCollisionDetector.checkPassthrough(ring, prevPos, currentPos)) {
            tracker.markPassed(nextIndex);
            score.addRingPoints(ring.points());

            if (ring.type() == RingType.CHECKPOINT) {
                tracker.markCheckpointPassed(nextIndex);
            }

            if (entity.hasComponent(RingEffectComponent.class)) {
                var effects = entity.getComponent(RingEffectComponent.class);
                effects.addEffect(ring.type(), 1);
            }

            if (hudManager != null) {
                hudManager.showRingPassed(playerRef.getPlayerId(), ring.points());
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
