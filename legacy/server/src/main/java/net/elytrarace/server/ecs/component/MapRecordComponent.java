package net.elytrarace.server.ecs.component;

import net.elytrarace.common.ecs.Component;

/**
 * Holds the current best finish time for the active map, in milliseconds.
 * <p>
 * Set on the game entity by {@code GameOrchestrator.loadNextMap()} from the DB record
 * (if one exists) and updated in-session by {@code CompletionDetectionSystem} whenever
 * a player finishes faster than the current value. Absent when no record has ever been
 * set for this (cup, map) combination.
 */
public record MapRecordComponent(long recordTimeMs) implements Component {
}
