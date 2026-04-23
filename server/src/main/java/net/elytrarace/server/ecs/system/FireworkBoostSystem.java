package net.elytrarace.server.ecs.system;

import net.elytrarace.common.ecs.Component;
import net.elytrarace.common.ecs.Entity;
import net.elytrarace.server.cup.BoostConfig;
import net.elytrarace.server.ecs.component.ElytraFlightComponent;
import net.elytrarace.server.ecs.component.FireworkBoostComponent;
import net.elytrarace.server.ecs.component.PlayerRefComponent;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.server.play.SetCooldownPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Processes firework boost requests each tick.
 * <p>
 * On each tick this system:
 * <ol>
 *   <li>Decrements the per-player cooldown counter.</li>
 *   <li>Claims any pending boost request ({@link FireworkBoostComponent#claimBoostRequest()}).</li>
 *   <li>Ignores the request if the cooldown has not expired or the player is not flying.</li>
 *   <li>Applies a one-shot velocity impulse in the player's full look direction (yaw + pitch).</li>
 *   <li>Starts the cooldown and sends a {@link SetCooldownPacket} to grey the hotbar slot.</li>
 * </ol>
 * <p>
 * The boost direction uses standard Minecraft look-direction math:
 * yaw=0 points south (+Z), negative pitch looks upward.
 * The impulse is a single push — the client's own elytra physics handle drag and gravity.
 * <p>
 * {@code player.setVelocity()} in Minestom expects <b>blocks/second</b>;
 * internal velocity tracking in {@link ElytraFlightComponent} uses <b>blocks/tick</b>.
 */
public class FireworkBoostSystem implements net.elytrarace.common.ecs.System {

    private static final Logger LOGGER = LoggerFactory.getLogger(FireworkBoostSystem.class);

    private static final String FIREWORK_COOLDOWN_GROUP = Material.FIREWORK_ROCKET.key().asString();

    @Override
    public Set<Class<? extends Component>> getRequiredComponents() {
        return Set.of(FireworkBoostComponent.class, ElytraFlightComponent.class, PlayerRefComponent.class);
    }

    @Override
    public void process(Entity entity, float deltaTime) {
        var boost = entity.getComponent(FireworkBoostComponent.class);
        var flight = entity.getComponent(ElytraFlightComponent.class);
        var playerRef = entity.getComponent(PlayerRefComponent.class);

        boost.tickCooldown();

        if (!boost.claimBoostRequest()) {
            return;
        }

        if (boost.isOnCooldown() || !flight.isFlying()) {
            return;
        }

        var player = playerRef.getPlayer();
        BoostConfig cfg = boost.getBoostConfig();

        double yawRad   = Math.toRadians(player.getPosition().yaw());
        double pitchRad = Math.toRadians(player.getPosition().pitch());

        // Minecraft convention: yaw=0 → south (+Z), negative pitch → looking up
        double lookX = -Math.sin(yawRad) * Math.cos(pitchRad);
        double lookY = -Math.sin(pitchRad);
        double lookZ =  Math.cos(yawRad) * Math.cos(pitchRad);

        // ElytraFlightComponent tracks velocity in blocks/tick; Minestom needs blocks/second
        Vec boostPerTick   = new Vec(lookX, lookY, lookZ).mul(cfg.speedBlocksPerTick());
        Vec boostPerSecond = boostPerTick.mul(20.0);

        flight.setVelocity(boostPerTick);
        player.setVelocity(boostPerSecond);

        boost.startCooldown();
        player.sendPacket(new SetCooldownPacket(FIREWORK_COOLDOWN_GROUP, boost.getCooldownRemainingTicks()));

        LOGGER.debug("Boost applied to {} — {} b/t", player.getUsername(), cfg.speedBlocksPerTick());
    }
}
