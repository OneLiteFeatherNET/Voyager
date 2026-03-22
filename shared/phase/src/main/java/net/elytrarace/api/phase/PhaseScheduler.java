package net.elytrarace.api.phase;

/**
 * Abstraction over platform-specific task schedulers (Bukkit, Minestom, etc.).
 * Implementations are provided by the platform-specific modules.
 */
public interface PhaseScheduler {

    /**
     * Schedules a repeating task.
     *
     * @param task          the task to execute
     * @param intervalTicks the interval in server ticks between executions
     * @param async         whether the task should run asynchronously
     * @return a handle to cancel the task
     */
    PhaseTask runRepeating(Runnable task, long intervalTicks, boolean async);
}
