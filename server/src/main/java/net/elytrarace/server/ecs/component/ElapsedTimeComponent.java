package net.elytrarace.server.ecs.component;

import net.elytrarace.common.ecs.Component;

/**
 * Tracks how long the current race has been running, in milliseconds.
 * <p>
 * Attached to the game entity only. Updated every tick by
 * {@code MinestomGamePhase} before {@code entityManager.update()} runs, so any
 * system processing player entities sees a consistent elapsed time for the
 * current tick.
 */
public record ElapsedTimeComponent(long elapsedMs) implements Component {
}
