package net.elytrarace.server.ecs.system;

import net.elytrarace.common.ecs.Component;
import net.elytrarace.common.ecs.Entity;
import net.elytrarace.common.ecs.EntityManager;
import net.elytrarace.common.game.scoring.MedalTier;
import net.elytrarace.server.ecs.component.ActiveMapComponent;
import net.elytrarace.server.ecs.component.BracketConfigComponent;
import net.elytrarace.server.ecs.component.ElapsedTimeComponent;
import net.elytrarace.server.ecs.component.ElytraFlightComponent;
import net.elytrarace.server.ecs.component.HudComponent;
import net.elytrarace.server.ecs.component.PlayerRefComponent;
import net.elytrarace.server.ecs.component.RingTrackerComponent;
import net.elytrarace.server.ecs.component.ScoreComponent;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Updates each player's actionbar HUD every 4 ticks (5 Hz).
 * <p>
 * <b>During the race</b>: shows speed, ring progress (X/N), elapsed time, and a
 * live bracket-pace indicator (projected finish bracket based on current pace).
 * <br>
 * <b>After finishing</b>: shows speed, the medal tier earned, and finish time.
 * <p>
 * Values are only re-sent when they change to prevent visual flickering. All
 * game-entity state (elapsed time, bracket config, total rings) is looked up via
 * the {@link EntityManager} on each display cycle.
 */
public class ScoreDisplaySystem implements net.elytrarace.common.ecs.System {

    private static final int TICK_INTERVAL = 4;

    private final EntityManager entityManager;
    private final Map<UUID, Integer> tickCounters = new HashMap<>();
    private final Map<UUID, Long> lastDisplayHash = new HashMap<>();

    public ScoreDisplaySystem(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Set<Class<? extends Component>> getRequiredComponents() {
        return Set.of(PlayerRefComponent.class, ScoreComponent.class,
                ElytraFlightComponent.class, HudComponent.class, RingTrackerComponent.class);
    }

    @Override
    public void process(Entity entity, float deltaTime) {
        var flight = entity.getComponent(ElytraFlightComponent.class);
        if (!flight.isFlying()) {
            // Clear cached state so the next takeoff renders immediately
            UUID cleared = entity.getId();
            tickCounters.remove(cleared);
            lastDisplayHash.remove(cleared);
            return;
        }

        UUID entityId = entity.getId();
        int ticks = tickCounters.getOrDefault(entityId, 0) + 1;
        if (ticks < TICK_INTERVAL) {
            tickCounters.put(entityId, ticks);
            return;
        }
        tickCounters.put(entityId, 0);

        var score   = entity.getComponent(ScoreComponent.class);
        var hud     = entity.getComponent(HudComponent.class);
        var tracker = entity.getComponent(RingTrackerComponent.class);

        double speedBps  = flight.getSpeedBlocksPerSecond();
        int passed       = tracker.passedCount();
        int total        = findTotalRings();
        long elapsedMs   = findElapsedMs();

        if (score.hasFinished()) {
            MedalTier medal   = score.getMedalTier() != null ? score.getMedalTier() : MedalTier.FINISH;
            long finishMs     = score.getCompletionTimeMs();

            long hash = ((long) medal.ordinal() << 48) | (finishMs & 0x0000FFFFFFFFFFFFL);
            if (!hash(entityId, hash)) return;

            hud.updateActionbarFinished(speedBps, medal, finishMs);
        } else {
            MedalTier pace = computePace(elapsedMs, passed, total);
            long speedKey  = Math.round(speedBps * 10);
            long hash = (speedKey << 40) | ((long) passed << 20) | (elapsedMs / 1000L);
            if (!hash(entityId, hash)) return;

            hud.updateActionbar(speedBps, passed, total, elapsedMs, pace);
        }
    }

    /**
     * Projects the finish time based on current pace and maps it to a bracket.
     * Returns DIAMOND when no data is available yet (early in the race).
     */
    private MedalTier computePace(long elapsedMs, int passed, int total) {
        if (passed <= 0 || total <= 0) {
            return MedalTier.DIAMOND;
        }
        long projectedMs = elapsedMs * total / passed;

        for (Entity e : entityManager.getEntities()) {
            if (e.hasComponent(BracketConfigComponent.class)) {
                var config = e.getComponent(BracketConfigComponent.class);
                return config.brackets().classify(
                        Duration.ofMillis(projectedMs), config.reference());
            }
        }
        return MedalTier.GOLD;
    }

    private long findElapsedMs() {
        for (Entity e : entityManager.getEntities()) {
            if (e.hasComponent(ElapsedTimeComponent.class)) {
                return e.getComponent(ElapsedTimeComponent.class).elapsedMs();
            }
        }
        return 0L;
    }

    private int findTotalRings() {
        for (Entity e : entityManager.getEntities()) {
            if (e.hasComponent(ActiveMapComponent.class)) {
                var map = e.getComponent(ActiveMapComponent.class).getCurrentMap();
                if (map != null) return map.rings().size();
            }
        }
        return 0;
    }

    /** Returns true when the hash changed (new display needed), false when unchanged. */
    private boolean hash(UUID id, long newHash) {
        Long prev = lastDisplayHash.get(id);
        if (prev != null && prev == newHash) return false;
        lastDisplayHash.put(id, newHash);
        return true;
    }
}
