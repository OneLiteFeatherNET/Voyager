package net.elytrarace.server.platform;

import net.elytrarace.api.phase.PhaseScheduler;
import net.elytrarace.api.phase.PhaseTask;
import net.minestom.server.MinecraftServer;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;

/**
 * Minestom implementation of {@link PhaseScheduler}.
 * Uses Minestom's scheduler to run repeating tasks at tick-based intervals.
 * <p>
 * Note: The {@code async} parameter in {@link #runRepeating(Runnable, long, boolean)} is
 * ignored because Minestom's tick scheduler is single-threaded by design and does not
 * support async task execution. All tasks run on the tick thread.
 */
public final class MinestomPhaseScheduler implements PhaseScheduler {

    @Override
    public PhaseTask runRepeating(Runnable task, long intervalTicks, boolean async) {
        Task scheduledTask = MinecraftServer.getSchedulerManager()
                .scheduleTask(task, TaskSchedule.immediate(), TaskSchedule.tick(Math.toIntExact(intervalTicks)));

        return new MinestomPhaseTask(scheduledTask);
    }
}
