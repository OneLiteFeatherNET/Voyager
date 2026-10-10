package net.elytrarace.voyager.platform.cup;

/**
 * One step of a cup's per-tick order: a name for the reader and the action it runs.
 *
 * @param name the step's name, as the tick order documents it
 * @param action what the step does on each tick
 */
public record TickStep(String name, Runnable action) {
}
