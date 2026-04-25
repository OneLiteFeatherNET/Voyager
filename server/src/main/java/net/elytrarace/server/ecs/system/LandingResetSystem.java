package net.elytrarace.server.ecs.system;

import net.elytrarace.common.ecs.Component;
import net.elytrarace.common.ecs.Entity;
import net.elytrarace.server.ecs.component.ElytraFlightComponent;
import net.elytrarace.server.ecs.component.HudComponent;
import net.elytrarace.server.ecs.component.RingTrackerComponent;
import net.elytrarace.server.ecs.component.ScoreComponent;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Resets a player's ring progress and score when they land mid-race.
 * <p>
 * Landing is defined as the flying flag transitioning from {@code true} to
 * {@code false}. {@link ElytraPhysicsSystem} syncs this flag from
 * {@code player.isOnGround()} every tick, so it reflects the server-confirmed ground state.
 * <p>
 * Reset is skipped for players who have already finished the map
 * ({@link ScoreComponent#hasFinished()}), so a legitimate finish does not wipe
 * the earned medal tier or completion time.
 * <p>
 * Must run <em>after</em> {@link ElytraPhysicsSystem} (flying state synced)
 * and <em>before</em> {@link ScoreDisplaySystem} (actionbar cleared before next render).
 */
public class LandingResetSystem implements net.elytrarace.common.ecs.System {

    private final Map<UUID, Boolean> prevFlying = new HashMap<>();

    @Override
    public Set<Class<? extends Component>> getRequiredComponents() {
        return Set.of(ElytraFlightComponent.class, RingTrackerComponent.class,
                ScoreComponent.class, HudComponent.class);
    }

    @Override
    public void process(Entity entity, float deltaTime) {
        var flight  = entity.getComponent(ElytraFlightComponent.class);
        UUID id     = entity.getId();
        boolean was = prevFlying.getOrDefault(id, false);
        boolean is  = flight.isFlying();
        prevFlying.put(id, is);

        if (!was || is) {
            return; // not a landing transition
        }

        var score = entity.getComponent(ScoreComponent.class);
        if (score.hasFinished()) {
            return; // legitimate finish — preserve medal + time
        }

        entity.getComponent(RingTrackerComponent.class).reset();
        score.reset();
        entity.getComponent(HudComponent.class).clearActionbar();
    }
}
