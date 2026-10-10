package net.elytrarace.server.ecs.component;

import net.elytrarace.common.ecs.Component;
import net.elytrarace.server.physics.RingType;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Stores pending ring effects that should be applied to a player entity.
 * Effects are queued when a ring collision is detected and consumed by
 * {@link net.elytrarace.server.ecs.system.RingEffectSystem}.
 */
public class RingEffectComponent implements Component {

    private final Queue<PendingEffect> pendingEffects = new LinkedList<>();

    /**
     * Represents a single pending effect to be applied.
     *
     * @param type           the ring type that determines the effect
     * @param ticksRemaining the number of ticks this effect should last
     */
    public record PendingEffect(RingType type, int ticksRemaining) {}

    /**
     * Queues a new effect to be processed.
     *
     * @param type     the ring type
     * @param duration the duration in ticks
     */
    public void addEffect(RingType type, int duration) {
        pendingEffects.add(new PendingEffect(type, duration));
    }

    /**
     * Retrieves and removes the next pending effect, or {@code null} if the queue is empty.
     */
    public PendingEffect pollEffect() {
        return pendingEffects.poll();
    }

    /**
     * Returns the number of pending effects in the queue.
     */
    public int pendingCount() {
        return pendingEffects.size();
    }

    /**
     * Returns the next pending effect without removing it, or {@code null} if empty.
     */
    public PendingEffect peekEffect() {
        return pendingEffects.peek();
    }
}
