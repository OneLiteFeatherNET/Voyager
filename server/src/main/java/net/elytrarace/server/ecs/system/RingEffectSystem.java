package net.elytrarace.server.ecs.system;

import net.elytrarace.common.ecs.Component;
import net.elytrarace.common.ecs.Entity;
import net.elytrarace.server.ecs.component.ElytraFlightComponent;
import net.elytrarace.server.ecs.component.PlayerRefComponent;
import net.elytrarace.server.ecs.component.RingEffectComponent;
import net.elytrarace.server.ecs.component.RingTrackerComponent;
import net.elytrarace.server.ecs.component.ScoreComponent;
import net.elytrarace.server.physics.RingType;
import net.minestom.server.coordinate.Vec;

import java.util.Set;

/**
 * Processes pending ring effects queued by {@link RingCollisionSystem}.
 * <p>
 * This system should be registered <em>after</em> {@link RingCollisionSystem} in the
 * {@link net.elytrarace.common.ecs.EntityManager} so that newly detected collisions
 * are processed in the same tick.
 * <p>
 * Effect behaviour per {@link RingType}:
 * <ul>
 *   <li>{@code STANDARD} — no additional effect (points already awarded)</li>
 *   <li>{@code BOOST} — multiplies velocity by 1.5</li>
 *   <li>{@code SLOW} — multiplies velocity by 0.5</li>
 *   <li>{@code CHECKPOINT} — marks the checkpoint as passed in {@link RingTrackerComponent}</li>
 *   <li>{@code BONUS} — no additional effect (extra points already awarded)</li>
 * </ul>
 * <p>
 * When a {@code BOOST} or {@code SLOW} effect changes the server-tracked velocity, the
 * updated velocity is sent to the client via {@code player.setVelocity()}. Ring boosts
 * are external forces (vanilla-equivalent to knockback or firework boosts), so the client
 * must be notified — unlike normal elytra flight, which is client-authoritative.
 */
public class RingEffectSystem implements net.elytrarace.common.ecs.System {

    /** Velocity multiplier applied for {@link RingType#BOOST} rings. */
    public static final double BOOST_MULTIPLIER = 1.5;

    /** Velocity multiplier applied for {@link RingType#SLOW} rings. */
    public static final double SLOW_MULTIPLIER = 0.5;

    @Override
    public Set<Class<? extends Component>> getRequiredComponents() {
        return Set.of(
                RingEffectComponent.class,
                ElytraFlightComponent.class,
                RingTrackerComponent.class,
                ScoreComponent.class
        );
    }

    @Override
    public void process(Entity entity, float deltaTime) {
        var effects = entity.getComponent(RingEffectComponent.class);
        var flight = entity.getComponent(ElytraFlightComponent.class);
        var tracker = entity.getComponent(RingTrackerComponent.class);

        Vec velocityBeforeEffects = flight.getVelocity();

        RingEffectComponent.PendingEffect effect;
        while ((effect = effects.pollEffect()) != null) {
            applyEffect(effect.type(), flight, tracker);
        }

        // Send modified velocity to client — ring effects are external forces.
        // Only send if velocity actually changed and player ref is available.
        if (!flight.getVelocity().equals(velocityBeforeEffects)
                && entity.hasComponent(PlayerRefComponent.class)) {
            entity.getComponent(PlayerRefComponent.class)
                    .getPlayer()
                    .setVelocity(flight.getVelocity().mul(20.0));
        }
    }

    private void applyEffect(RingType type, ElytraFlightComponent flight, RingTrackerComponent tracker) {
        switch (type) {
            case BOOST -> {
                Vec velocity = flight.getVelocity();
                flight.setVelocity(velocity.mul(BOOST_MULTIPLIER));
            }
            case SLOW -> {
                Vec velocity = flight.getVelocity();
                flight.setVelocity(velocity.mul(SLOW_MULTIPLIER));
            }
            case CHECKPOINT -> {
                // Checkpoint index is tracked via the ring index in RingTrackerComponent.
                // The collision system already marks the ring as passed; here we additionally
                // record it as a mandatory checkpoint passage.
                // Since we don't have the ring index here, we use a sentinel approach:
                // the collision system is responsible for calling markCheckpointPassed().
            }
            case STANDARD, BONUS -> {
                // No additional effect — points are handled by RingCollisionSystem.
            }
        }
    }
}
