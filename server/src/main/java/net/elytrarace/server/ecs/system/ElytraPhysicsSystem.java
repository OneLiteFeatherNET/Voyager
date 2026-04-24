package net.elytrarace.server.ecs.system;

import net.elytrarace.common.ecs.Component;
import net.elytrarace.common.ecs.Entity;
import net.elytrarace.server.ecs.component.ElytraFlightComponent;
import net.elytrarace.server.ecs.component.PlayerRefComponent;
import net.elytrarace.server.physics.ElytraPhysics;
import net.minestom.server.coordinate.Vec;

import java.util.Set;

/**
 * Tracks server-side elytra flight velocity each tick for use by other systems, but does
 * NOT push the velocity to the client.
 * <p>
 * Vanilla Minecraft is client-authoritative for normal elytra flight — the server only
 * sends velocity packets for external forces (firework boosts, ring boosts, knockback).
 * Pushing velocity to the client every tick causes constant client-side reconciliation,
 * which produces the "wrong feel". This system therefore computes the next velocity
 * server-side (so downstream systems like {@link FireworkBoostSystem} and
 * {@link RingEffectSystem} have accurate inputs for their boost formulas), but leaves
 * the client's own elytra physics simulation untouched.
 * <p>
 * Each tick:
 * <ol>
 *   <li>The current server-tracked velocity ({@link ElytraFlightComponent#getVelocity()}) is
 *       advanced by one tick of vanilla elytra physics via
 *       {@link ElytraPhysics#computeNextVelocity(Vec, double, double)}.</li>
 *   <li>The result is stored back into the component for later systems to read.</li>
 *   <li>The player's current position is stored as {@code previousPosition} so that
 *       {@link RingCollisionSystem} (which runs before this system) can compute the
 *       accurate flight segment used for ring intersection tests.</li>
 * </ol>
 * <p>
 * {@link FireworkBoostSystem} runs <em>after</em> this system and sends velocity to the
 * client only during boost ticks — matching vanilla's external-force pattern.
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

        flight.setPreviousPosition(pos);

        Vec nextVel = ElytraPhysics.computeNextVelocity(flight.getVelocity(), pos.pitch(), pos.yaw());
        flight.setVelocity(nextVel);
    }
}
