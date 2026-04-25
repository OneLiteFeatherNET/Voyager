package net.elytrarace.server.phase;

import net.elytrarace.common.ecs.Entity;
import net.elytrarace.common.ecs.EntityManager;
import net.elytrarace.server.ecs.component.ActiveMapComponent;
import net.elytrarace.server.ecs.component.CupProgressComponent;
import net.elytrarace.server.ecs.component.ElapsedTimeComponent;
import net.elytrarace.server.ecs.component.RingTrackerComponent;
import net.minestom.server.utils.time.TimeUnit;
import net.theevilreaper.xerus.api.phase.TickingPhase;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Active game phase for the Minestom server.
 * <p>
 * Runs every tick (interval = 1) and drives the ECS game loop by calling
 * {@link EntityManager#update(float)} each tick. Physics, collision detection,
 * and scoring are handled by the systems registered on the entity manager.
 * <p>
 * The phase finishes when either:
 * <ul>
 *   <li>The configurable race duration expires (default 6000 ticks = 5 minutes at 20 TPS)</li>
 *   <li>All players have passed all rings on the current map</li>
 * </ul>
 */
public final class MinestomGamePhase extends TickingPhase {

    private static final Logger LOGGER = LoggerFactory.getLogger(MinestomGamePhase.class);

    /**
     * Delta time for a single tick at 20 TPS (50 ms = 0.05 s).
     */
    private static final float TICK_DELTA = 1.0f / 20.0f;

    /**
     * Default race duration in ticks (5 minutes at 20 TPS).
     */
    public static final int DEFAULT_RACE_DURATION_TICKS = 6000;

    /** Milliseconds per tick at 20 TPS. */
    private static final long MS_PER_TICK = 50L;

    private final EntityManager entityManager;
    private final int raceDurationTicks;
    private Runnable onGamePhaseFinished;
    private int elapsedTicks;
    private boolean finishing = false;

    public MinestomGamePhase(EntityManager entityManager) {
        this(entityManager, DEFAULT_RACE_DURATION_TICKS, null);
    }

    public MinestomGamePhase(EntityManager entityManager, int raceDurationTicks,
                             Runnable onGamePhaseFinished) {
        super("game", TimeUnit.SERVER_TICK, 1);
        this.entityManager = entityManager;
        this.raceDurationTicks = raceDurationTicks;
        this.onGamePhaseFinished = onGamePhaseFinished;
    }

    @Override
    public void onStart() {
        finishing = false;
        elapsedTicks = 0;
        super.onStart();
        LOGGER.info("Game phase started — ECS loop running every tick, race duration {} ticks",
                raceDurationTicks);
    }

    @Override
    public void onUpdate() {
        updateElapsedTime();          // set timer BEFORE systems process it
        entityManager.update(TICK_DELTA);
        elapsedTicks++;

        if (elapsedTicks >= raceDurationTicks) {
            LOGGER.info("Race duration expired after {} ticks", elapsedTicks);
            finish();
            return;
        }
        if (allPlayersFinished()) {
            LOGGER.info("All players have passed all rings after {} ticks", elapsedTicks);
            finish();
        }
    }

    /**
     * Publishes the current race time to the game entity so any system processing
     * player entities this tick sees a consistent elapsed-time value. Must run
     * BEFORE {@link EntityManager#update(float)} on the same tick.
     */
    private void updateElapsedTime() {
        long elapsedMs = (long) elapsedTicks * MS_PER_TICK;
        for (Entity entity : entityManager.getEntities()) {
            if (entity.hasComponent(CupProgressComponent.class)) {
                entity.addComponent(new ElapsedTimeComponent(elapsedMs));
                return;
            }
        }
    }

    @Override
    public void finish() {
        if (finishing) {
            return;
        }
        finishing = true;
        LOGGER.info("Game phase finished");
        if (onGamePhaseFinished != null) {
            onGamePhaseFinished.run();
        }
        super.finish();
    }

    /**
     * Checks whether all players with a {@link RingTrackerComponent} have passed
     * every ring on the current map.
     */
    private boolean allPlayersFinished() {
        int totalRings = getTotalRingCount();
        if (totalRings <= 0) {
            return false;
        }

        var playerEntities = entityManager.getEntitiesWithComponent(RingTrackerComponent.class);
        if (playerEntities.isEmpty()) {
            return false;
        }

        for (Entity entity : playerEntities) {
            var tracker = entity.getComponent(RingTrackerComponent.class);
            if (tracker.passedCount() < totalRings) {
                return false;
            }
        }
        return true;
    }

    private int getTotalRingCount() {
        for (Entity entity : entityManager.getEntities()) {
            if (entity.hasComponent(ActiveMapComponent.class)) {
                var activeMap = entity.getComponent(ActiveMapComponent.class);
                if (activeMap.getCurrentMap() != null) {
                    return activeMap.getCurrentMap().rings().size();
                }
            }
        }
        return 0;
    }

    /**
     * Returns the entity manager driving this game phase.
     *
     * @return the ECS entity manager
     */
    public EntityManager getEntityManager() {
        return entityManager;
    }

    /**
     * Returns the number of ticks elapsed since the game phase started.
     */
    public int getElapsedTicks() {
        return elapsedTicks;
    }

    /**
     * Sets the callback invoked when the game phase finishes.
     * <p>
     * Pass {@code null} to clear the callback (e.g., during a game restart
     * to prevent stale callbacks from triggering unwanted phase transitions).
     *
     * @param callback the callback to invoke on finish, or {@code null} to clear
     */
    public void setOnGamePhaseFinished(@Nullable Runnable callback) {
        this.onGamePhaseFinished = callback;
    }
}
