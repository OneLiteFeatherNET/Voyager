package net.elytrarace.phase;
import java.util.List;

/**
 * @author Patrick Zdarsky / Rxcki
 * @version 1.0
 * @since 03/01/2020 21:12
 */

public class LinearPhaseSeries<T extends Phase> extends PhaseCollection<T> {

    protected T currentPhase;
    protected int currentPhaseIndex;
    private boolean paused;

    public LinearPhaseSeries() {
    }

    public LinearPhaseSeries(String name) {
        super(name);
    }

    public LinearPhaseSeries(List<T> phases) {
        super(phases);
    }

    public LinearPhaseSeries(String name, List<T> phases) {
        super(name, phases);
    }

    @Override
    public void onStart() {
        setCurrentPhase(get(0));
        startCurrentPhase();
    }

    public void advance() {
        if (!isRunning())
            throw new IllegalStateException("Advance called on not running phase!");

        if (isPaused())
            return;

        if (isFinished() || isLastPhase()) {
            finish();
            return;
        }

        currentPhaseIndex++;
        setCurrentPhase(phases.get(currentPhaseIndex));
        startCurrentPhase();
    }

    @Override
    public Phase getCurrentPhase() {
        return this.currentPhase == null ? null : this.currentPhase.getCurrentPhase();
    }

    @Override
    public void onSkip() {
        if (getCurrentPhase() != null)
            getCurrentPhase().onSkip();
    }

    public boolean isLastPhase() {
        return currentPhaseIndex == size() - 1;
    }

    public void startCurrentPhase() {
        currentPhase.setFinishedCallback(this::advance);
        currentPhase.start();
    }

    public void setCurrentPhaseIndex(int currentPhaseIndex) {
        this.currentPhaseIndex = currentPhaseIndex;
        setCurrentPhase(get(currentPhaseIndex));
    }

    private void setCurrentPhase(T currentPhase) {
        this.currentPhase = currentPhase;
    }

    /**
     * Returns the state if the phase series is paused or not.
     * @return True when paused otherwise false
     */

    public boolean isPaused() {
        return paused;
    }

    /**
     * Updates the value if the phase series should be paused or not.
     * @param paused True for paused or false for not paused
     */

    public void setPaused(boolean paused) {
        boolean start = !paused && this.paused;
        this.paused = paused;

        //Do we want this function? One could also use advance()
        /*if (start && getCurrentPhase() != null && getCurrentPhase().isFinished())
            advance();*/
        advance();
    }
}