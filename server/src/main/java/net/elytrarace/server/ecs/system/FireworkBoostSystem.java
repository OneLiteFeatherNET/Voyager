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
 * Implements a two-phase firework boost each tick.
 *
 * <h2>Phase 1 — Attack (one tick at activation)</h2>
 * When the player uses a firework rocket and is flying without an active cooldown:
 * <ol>
 *   <li>An additive kick ({@link BoostConfig#kickBlocksPerTick()}) is added to the
 *       player's current velocity in their look direction. Current velocity is
 *       <em>preserved</em> — the kick does not overwrite it.</li>
 *   <li>The cooldown starts ({@link FireworkBoostComponent#startCooldown()}).</li>
 *   <li>A {@link SetCooldownPacket} is sent to grey out the hotbar slot.</li>
 *   <li>The burn counter starts ({@link FireworkBoostComponent#startBurn()}).</li>
 * </ol>
 *
 * <h2>Phase 2 — Sustain (burnDurationTicks after activation)</h2>
 * Each tick while the burn is active:
 * <ol>
 *   <li>The player's current look direction is re-read (players <em>can steer mid-boost</em>).</li>
 *   <li>Additive thrust is applied with a linear falloff: 100 % at start → 30 % at end.</li>
 *   <li>The total velocity magnitude is capped at {@link BoostConfig#maxSpeedBlocksPerTick()}.</li>
 *   <li>If the player is no longer flying, the burn is cancelled.</li>
 * </ol>
 *
 * <h2>Unit conventions</h2>
 * {@link ElytraFlightComponent} tracks velocity in <b>blocks/tick</b>.
 * {@code player.setVelocity()} in Minestom expects <b>blocks/second</b> — multiply by 20.
 *
 * <h2>Look-direction math</h2>
 * Minecraft convention: yaw=0 → south (+Z), negative pitch → looking up.
 * {@code lookX = -sin(yaw)·cos(pitch)}, {@code lookY = -sin(pitch)},
 * {@code lookZ = cos(yaw)·cos(pitch)}.
 */
public class FireworkBoostSystem implements net.elytrarace.common.ecs.System {

    private static final Logger LOGGER = LoggerFactory.getLogger(FireworkBoostSystem.class);
    private static final String FIREWORK_COOLDOWN_GROUP = Material.FIREWORK_ROCKET.key().asString();

    /** Linear falloff floor: thrust at end-of-burn is this fraction of {@code thrustBlocksPerTick}. */
    private static final double FALLOFF_MIN = 0.3;

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

        // ── Phase 2: apply sustained thrust every tick while burning ──────────
        if (boost.isBurning()) {
            if (!flight.isFlying()) {
                boost.cancelBurn();
            } else {
                Vec look = lookVec(player.getPosition().yaw(), player.getPosition().pitch());
                float falloff = (float) boost.getBurnTicksRemaining() / cfg.burnDurationTicks();
                double thrustFactor = FALLOFF_MIN + (1.0 - FALLOFF_MIN) * falloff;
                Vec thrust = look.mul(cfg.thrustBlocksPerTick() * thrustFactor);

                Vec newVel = clampMagnitude(flight.getVelocity().add(thrust), cfg.maxSpeedBlocksPerTick());
                flight.setVelocity(newVel);
                player.setVelocity(newVel.mul(20.0));

                boost.tickBurn();
            }
        }

        // ── Phase 1: handle a new boost request ───────────────────────────────
        if (!boost.claimBoostRequest()) {
            return;
        }
        if (boost.isOnCooldown() || !flight.isFlying()) {
            return;
        }

        // Additive kick — preserve current velocity, just add energy
        Vec look = lookVec(player.getPosition().yaw(), player.getPosition().pitch());
        Vec kick = look.mul(cfg.kickBlocksPerTick());
        Vec newVel = clampMagnitude(flight.getVelocity().add(kick), cfg.maxSpeedBlocksPerTick());
        flight.setVelocity(newVel);
        player.setVelocity(newVel.mul(20.0));

        boost.startCooldown();
        boost.startBurn();
        player.sendPacket(new SetCooldownPacket(FIREWORK_COOLDOWN_GROUP, boost.getCooldownRemainingTicks()));

        LOGGER.debug("Boost activated for {} — kick {} b/t, burn {} ticks",
                player.getUsername(), cfg.kickBlocksPerTick(), cfg.burnDurationTicks());
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
