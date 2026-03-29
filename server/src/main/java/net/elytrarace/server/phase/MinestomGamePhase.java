package net.elytrarace.server.phase;

import net.elytrarace.api.phase.EventRegistrar;
import net.elytrarace.api.phase.PhaseScheduler;
import net.elytrarace.api.phase.TickingPhase;
import net.elytrarace.common.ecs.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Active game phase for the Minestom server.
 * <p>
 * Runs every tick (interval = 1) and drives the ECS game loop by calling
 * {@link EntityManager#update(float)} each tick. Physics, collision detection,
 * and scoring are handled by the systems registered on the entity manager.
 */
public final class MinestomGamePhase extends TickingPhase {

    private static final Logger LOGGER = LoggerFactory.getLogger(MinestomGamePhase.class);

    /**
     * Delta time for a single tick at 20 TPS (50 ms = 0.05 s).
     */
    private static final float TICK_DELTA = 1.0f / 20.0f;

    private final EntityManager entityManager;

    public MinestomGamePhase(PhaseScheduler scheduler, EventRegistrar eventRegistrar, EntityManager entityManager) {
        super("Game", scheduler, eventRegistrar, 1, false);
        this.entityManager = entityManager;
    }

    @Override
    public void onStart() {
        super.onStart();
        LOGGER.info("Game phase started — ECS loop running every tick");
    }

    @Override
    public void onUpdate() {
        entityManager.update(TICK_DELTA);
    }

    @Override
    public void finish() {
        LOGGER.info("Game phase finished");
        super.finish();
    }

    /**
     * Returns the entity manager driving this game phase.
     *
     * @return the ECS entity manager
     */
    public EntityManager getEntityManager() {
        return entityManager;
    }
}
