package net.elytrarace.server.ecs.system;

import net.elytrarace.common.ecs.Component;
import net.elytrarace.common.ecs.Entity;
import net.elytrarace.server.ecs.component.ElytraFlightComponent;
import net.elytrarace.server.ecs.component.PlayerRefComponent;
import net.elytrarace.server.physics.ElytraPhysics;
import net.minestom.server.coordinate.Vec;

import java.util.Set;

/**
 * Advances server-side elytra flight physics each tick and pushes the result to the client.
 * <p>
 * The server is authoritative over elytra velocity. Each tick:
 * <ol>
 *   <li>The current server-tracked velocity ({@link ElytraFlightComponent#getVelocity()}) is
 *       advanced by one tick of vanilla elytra physics via
 *       {@link ElytraPhysics#computeNextVelocity(Vec, double, double)}.</li>
 *   <li>The result is stored back and sent to the client via {@code player.setVelocity()}
 *       so the client's simulation stays in sync.</li>
 *   <li>The player's current position is stored as {@code previousPosition} so that
 *       {@link RingCollisionSystem} (which runs before this system) can compute the
 *       accurate flight segment used for ring intersection tests.</li>
 * </ol>
 * <p>
 * {@link FireworkBoostSystem} runs <em>after</em> this system and may override the sent
 * velocity for the current tick when a boost burn is active — the boost formula is applied
 * on top of the physics-computed velocity, matching the vanilla execution order.
 */
public class ElytraPhysicsSystem implements net.elytrarace.common.ecs.System {

    @Override
    public Set<Class<? extends Component>> getRequiredComponents() {
        return Set.of(ElytraFlightComponent.class, PlayerRefComponent.class);
    }

    @Override
    public void process(Entity entity, float deltaTime) {
        var flight = entity.getComponent(ElytraFlightComponent.class);
        var playerRef = entity.getComponent(PlayerRefComponent.class);

        if (!flight.isFlying()) {
            return;
        }

        var player = playerRef.getPlayer();
        var pos = player.getPosition();
        flight.setPitch(pos.pitch());
        flight.setYaw(pos.yaw());

        // Store current position for ring collision detection (read by RingCollisionSystem
        // in the PREVIOUS tick's check, before this update overwrites it).
        flight.setPreviousPosition(pos);

        // Advance server-tracked velocity by one vanilla elytra physics tick.
        Vec nextVel = ElytraPhysics.computeNextVelocity(flight.getVelocity(), pos.pitch(), pos.yaw());
        flight.setVelocity(nextVel);

        // Push to client — server is authoritative for elytra physics.
        player.setVelocity(nextVel.mul(20.0));
    }
}
