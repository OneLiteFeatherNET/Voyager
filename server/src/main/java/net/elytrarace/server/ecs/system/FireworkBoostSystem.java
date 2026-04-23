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
 * Applies the vanilla firework boost formula each tick while a boost burn is active.
 *
 * <h2>Vanilla formula (see docs/elytra-physics-reference.md §3.2)</h2>
 * <pre>
 *   newVel = 0.5 × currentVel + 0.85 × lookDirection
 * </pre>
 * Applied every tick for {@link BoostConfig#burnDurationTicks()} ticks. The look direction is
 * re-read each tick, so players can steer mid-boost exactly as in vanilla.
 *
 * <h2>Behaviour per tick</h2>
 * <ol>
 *   <li><b>Activation</b> — if the player used a firework while flying and not on cooldown:
 *       the burn counter is set and the client item cooldown packet is sent.</li>
 *   <li><b>Formula</b> — if the burn counter &gt; 0: the formula is applied, velocity is capped
 *       at {@link BoostConfig#maxSpeedBlocksPerTick()}, and the counter is decremented.</li>
 * </ol>
 * Activation and formula both happen in the same tick, so the player feels the impulse
 * immediately on the frame they press the boost button.
 *
 * <h2>Unit conventions</h2>
 * {@link ElytraFlightComponent} tracks velocity in <b>blocks/tick</b>.
 * {@code player.setVelocity()} in Minestom expects <b>blocks/second</b> — multiply by 20.
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
        var boost     = entity.getComponent(FireworkBoostComponent.class);
        var flight    = entity.getComponent(ElytraFlightComponent.class);
        var playerRef = entity.getComponent(PlayerRefComponent.class);
        var player    = playerRef.getPlayer();
        var cfg       = boost.getBoostConfig();

        // ── Phase 1: activate on boost request ────────────────────────────────
        if (boost.claimBoostRequest() && !boost.isOnCooldown() && flight.isFlying()) {
            boost.startCooldown();
            boost.startBurn();
            player.sendPacket(new SetCooldownPacket(FIREWORK_COOLDOWN_GROUP, boost.getCooldownRemainingTicks()));
            LOGGER.debug("Boost activated for {} — {} burn ticks", player.getUsername(), cfg.burnDurationTicks());
        }

        // ── Phase 2: apply vanilla formula each burn tick ──────────────────────
        if (!boost.isBurning()) {
            return;
        }
        if (!flight.isFlying()) {
            boost.cancelBurn();
            return;
        }

        Vec look = lookVec(player.getPosition().yaw(), player.getPosition().pitch());
        // Vanilla: newVel = 0.5 * currentVel + 0.85 * look
        Vec newVel = clampMagnitude(
                flight.getVelocity().mul(0.5).add(look.mul(0.85)),
                cfg.maxSpeedBlocksPerTick()
        );
        flight.setVelocity(newVel);
        player.setVelocity(newVel.mul(20.0));
        boost.tickBurn();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Vec lookVec(float yaw, float pitch) {
        double yawRad   = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        return new Vec(
                -Math.sin(yawRad) * Math.cos(pitchRad),
                -Math.sin(pitchRad),
                 Math.cos(yawRad) * Math.cos(pitchRad));
    }

    private static Vec clampMagnitude(Vec v, double max) {
        double mag = v.length();
        return mag > max ? v.mul(max / mag) : v;
    }
}
