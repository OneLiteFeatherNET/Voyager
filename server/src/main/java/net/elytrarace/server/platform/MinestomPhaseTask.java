package net.elytrarace.server.platform;

import net.elytrarace.api.phase.PhaseTask;
import net.minestom.server.timer.Task;

/**
 * Minestom implementation of {@link PhaseTask}.
 * Wraps a Minestom {@link Task} to provide cancellation support.
 *
 * @param task the underlying Minestom task
 */
public record MinestomPhaseTask(Task task) implements PhaseTask {

    @Override
    public void cancel() {
        task.cancel();
    }
}
