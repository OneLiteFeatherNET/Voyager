package net.elytrarace.api.phase;

import org.jetbrains.annotations.MustBeInvokedByOverriders;

/**
 * @author Patrick Zdarsky / Rxcki
 * @version 1.0
 * @since 03/01/2020 21:53
 */

public abstract class TickingPhase extends TickedPhase {

    private final PhaseScheduler scheduler;
    private final long interval;
    private final boolean async;

    private PhaseTask scheduledTask;

    public TickingPhase(String name, PhaseScheduler scheduler, EventRegistrar eventRegistrar, long interval, boolean async) {
        super(name, eventRegistrar);
        this.scheduler = scheduler;
        this.interval = interval;
        this.async = async;
    }

    public TickingPhase(String name, PhaseScheduler scheduler, EventRegistrar eventRegistrar) {
        this(name, scheduler, eventRegistrar, 20, false);
    }

    /**
     * Remember to call this method using super.onStart() !
     */
    @Override
    @MustBeInvokedByOverriders
    public void onStart() {
        scheduledTask = scheduler.runRepeating(this::onUpdate, interval, async);
    }

    @Override
    @MustBeInvokedByOverriders
    public void finish() {
        super.finish();
        if (scheduledTask != null)
            scheduledTask.cancel();
    }

    /**
     * Sets the scheduled task for the phase.
     * @param scheduledTask The task to set
     */
    public void setScheduledTask(PhaseTask scheduledTask) {
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
     * Returns the scheduled task from the phase.
     * @return the underlying task
     */
    public PhaseTask getScheduledTask() {
        return scheduledTask;
    }

    /**
     * Returns the phase scheduler.
     * @return the scheduler
     */
    public PhaseScheduler getScheduler() {
        return scheduler;
    }
}
