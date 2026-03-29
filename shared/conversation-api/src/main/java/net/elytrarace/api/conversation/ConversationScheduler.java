package net.elytrarace.api.conversation;

import org.jetbrains.annotations.NotNull;

/**
 * Platform-agnostic scheduler abstraction for conversation timeouts.
 * Replaces direct usage of Bukkit's scheduler API.
 */
public interface ConversationScheduler {

    /**
     * Schedules a task to run after a delay.
     *
     * @param task The task to execute.
     * @param delayTicks The delay in server ticks (20 ticks = 1 second).
     * @return A task ID that can be used to cancel the task.
     */
    int scheduleDelayed(@NotNull Runnable task, long delayTicks);

    /**
     * Cancels a previously scheduled task.
     *
     * @param taskId The task ID returned by {@link #scheduleDelayed}.
     */
    void cancelTask(int taskId);
}
