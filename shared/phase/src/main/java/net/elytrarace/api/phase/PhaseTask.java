package net.elytrarace.api.phase;

/**
 * A handle for a scheduled task that can be cancelled.
 */
public interface PhaseTask {

    /**
     * Cancels the scheduled task.
     */
    void cancel();
}
