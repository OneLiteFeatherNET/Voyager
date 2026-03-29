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

        // Read current orientation from the player
        flight.setPitch(playerRef.getPlayer().getPosition().pitch());
        flight.setYaw(playerRef.getPlayer().getPosition().yaw());

        // Compute new velocity
        Vec newVelocity = ElytraPhysics.computeNextVelocity(
                flight.getVelocity(), flight.getPitch(), flight.getYaw());
        flight.setVelocity(newVelocity);

        // Apply velocity to the Minestom player
        playerRef.getPlayer().setVelocity(newVelocity);
    }
}
