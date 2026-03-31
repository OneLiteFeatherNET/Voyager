package net.elytrarace.server.ecs.system;

import net.elytrarace.common.ecs.Component;
import net.elytrarace.common.ecs.Entity;
import net.elytrarace.server.ecs.component.ElytraFlightComponent;
import net.elytrarace.server.ecs.component.PlayerRefComponent;
import net.elytrarace.server.physics.ElytraPhysics;
import net.minestom.server.coordinate.Vec;

import java.util.Set;

/**
 * Applies elytra flight physics each tick.
 * <p>
 * Reads the player's current pitch and yaw, computes the next velocity via
 * {@link ElytraPhysics}, updates the {@link ElytraFlightComponent}, and
 * applies the resulting velocity to the Minestom player.
 * <p>
 * Only processes entities that are currently flying.
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

        // Track orientation so collision and scoring systems can read it
        var player = playerRef.getPlayer();
        flight.setPitch(player.getPosition().pitch());
        flight.setYaw(player.getPosition().yaw());

        // Estimate server-side velocity from position delta (used for ring collision,
        // NOT sent to the client — the client runs its own elytra physics).
        var prev = flight.getPreviousPosition();
        var curr = player.getPosition();
        if (prev != null) {
            flight.setVelocity(new Vec(
                curr.x() - prev.x(),
                curr.y() - prev.y(),
                curr.z() - prev.z()
            ));
        }
        flight.setPreviousPosition(curr);
    }
}
