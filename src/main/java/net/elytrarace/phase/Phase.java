package net.elytrarace.phase;

/**
 * @author Patrick Zdarsky / Rxcki
 * @version 1.0
 * @since 03/01/2020 21:00
 *
 * Describes any Phase which will be externally started and which can finish at some point,
 * when the phase finishes it can notify a listener using the {@code finishedCallback}
 */
public abstract class Phase {

    private String name = "Unnamed phase";
    private boolean running;
    private boolean finished;
    private boolean skipping;

    private Runnable finishedCallback;

    public Phase() { }

    public Phase(String name) {
        this.name = name;
    }

    /**
     * Used to start the phase.
     *
     * A phase can only be started once, so when running is true the phase won't be started
     */
    public void start() {
        if (running) {
            return;
        }
        running = true;
        onStart();
    }

    protected abstract void onStart();

    public void onSkip() {}

    /**
     * Used to finish the phase.
     *
     * A phase can only be finished once, so when finished is true the phase won't be stated as finished again
     */
    public void finish() {
        if (finished)
            return;

        finished = true;
        running = false;
        if (finishedCallback != null)
            finishedCallback.run();
        skipping = false;
    }

    public Phase getCurrentPhase() {
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return The name of this phase
     */
    public String getName() {
        return name;
    }

    /**
     * @return True if the phase is currently active, false otherwise
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * @return True if the phase is already finished, false otherwise
     */
    public boolean isFinished() {
        return finished;
    }

    public void setFinished(boolean finished) {
        this.finished = finished;
    }

    public boolean isSkipping() {
        return skipping;
    }

    public void setSkipping(boolean skipping) {
        this.skipping = skipping;
    }

    /**
     * Sets the callback which will be called when the phase is finished
     * @param finishedCallback The callback to be called when the phase finished
     */
    public void setFinishedCallback(Runnable finishedCallback) {
        this.finishedCallback = finishedCallback;
    }
}
