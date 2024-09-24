package net.elytrarace.api.phase;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.MustBeInvokedByOverriders;

/**
 * @author Patrick Zdarsky / Rxcki
 * @version 1.0
 * @since 03/01/2020 21:53
 */

public abstract class TickingPhase extends TickedPhase {

    private final long interval;
    private final boolean async;

    private BukkitTask scheduledTask;

    public TickingPhase(String name, JavaPlugin game, long interval, boolean async) {
        super(name, game);
        this.interval = interval;
        this.async = async;
    }

    public TickingPhase(String name, JavaPlugin game) {
        this(name, game, 20, false);
    }

    /**
     * Remember to call this method using super.onStart() !
     */
    @Override
    @MustBeInvokedByOverriders
    public void onStart() {
        if (async) {
            scheduledTask = Bukkit.getScheduler().runTaskTimerAsynchronously(getPlugin(), this::onUpdate, 0L, interval);
        } else {
            scheduledTask = Bukkit.getScheduler().runTaskTimer(getPlugin(), this::onUpdate, 0L, interval);
        }
    }

    @Override
    @MustBeInvokedByOverriders
    public void finish() {
        super.finish();
        if (getScheduledTask() != null)
            scheduledTask.cancel();
    }

    /**
     * Sets the scheduled task to the phase.
     * @param scheduledTask The {@link BukkitTask} to set
     */

    public void setScheduledTask(BukkitTask scheduledTask) {
        this.scheduledTask = scheduledTask;
    }

    /**
     * Returns the tick interval from the {@link TickingPhase}.
     * @return the interval
     */

    public long getInterval() {
        return interval;
    }

    /**
     * Returns if the task is an async task.
     * @return True when the task is async otherwise false
     */

    public boolean isAsync() {
        return async;
    }

    /**
     * Returns the {@link BukkitTask} from the phase.
     * @return the underlying task
     */

    public BukkitTask getScheduledTask() {
        return scheduledTask;
    }
}