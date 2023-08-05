package net.elytrarace.phase;

import java.util.List;

/**
 * @author Patrick Zdarsky / Rxcki
 * @version 1.0
 * @since 03/01/2020 21:33
 */

public class CyclicPhaseSeries<T extends Phase> extends LinearPhaseSeries<T> {

    private int maxIterations;
    private int iterations;

    public CyclicPhaseSeries() { }

    public CyclicPhaseSeries(String name) {
        super(name);
    }

    public CyclicPhaseSeries(List<T> phases) {
        super(phases);
    }

    public CyclicPhaseSeries(String name, List<T> phases) {
        super(name, phases);
    }

    @Override
    public void advance() {
        if (isFinished()) {
            return;
        }

        //Check if the current phase is the last one
        if (isLastPhase()) {
            //Increase iterations, if this is exceeding the maxiterations, finish this phase
            if (!increaseIteration())
                return;

            //Reset underlying LinearPhaseSeries for next iteration
            setCurrentPhaseIndex(0);
            getCurrentPhase().setFinished(false);

            startCurrentPhase();
            return;
        }

        setCurrentPhaseIndex(currentPhaseIndex + 1);
        //Reset current phase since it could be already finished because this is another iteration
        currentPhase.setFinished(false);
        startCurrentPhase();
    }

    public void skipIteration() {
        //Pause the linearPhaseSeries and skip the current phase
        setPaused(true);
        getCurrentPhase().onSkip();
        getCurrentPhase().finish();

        //Increase the iteration count and finish if we have reached the end
        if (!increaseIteration())
            return;

        //Reset to the first phase and reset it
        setCurrentPhaseIndex(0);
        getCurrentPhase().setFinished(false);
        //Unpause
        setPaused(false);
        //Start the current phase
        startCurrentPhase();
    }

    private boolean increaseIteration() {
        iterations++;

        //Check if we reached the max allowed iterations, if yes => finish
        if (iterations == maxIterations) {
            finish();
            return false;
        }
        return true;
    }

    public int getMaxIterations() {
        return maxIterations;
    }

    public int getIterations() {
        return iterations;
    }

    public void setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
    }
}