package net.elytrarace.voyager.platform.cup;

import java.util.List;

/**
 * A cup's per-tick order, declared once as an ordered list of steps and run in that order on every tick.
 *
 * <p>The order is the cup's, so it lives in the cup's package rather than in the generic tick infrastructure. A
 * step that throws stops the tick: the steps after it do not run, and the exception reaches the caller, which is
 * what lets the server stop a broken cup instead of repeating the failure every tick.
 */
public final class TickPipeline {

    private final List<TickStep> steps;

    private TickPipeline(List<TickStep> steps) {
        this.steps = steps;
    }

    /**
     * Builds a pipeline that runs {@code steps} in the order given.
     *
     * @throws IllegalArgumentException if {@code steps} is empty: a tick with no order has nothing to declare
     */
    public static TickPipeline of(List<TickStep> steps) {
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("a tick pipeline needs at least one step");
        }
        return new TickPipeline(List.copyOf(steps));
    }

    /** The steps, in the order they run. The list cannot be changed. */
    public List<TickStep> steps() {
        return steps;
    }

    /** Runs every step once, in order. */
    public void run() {
        for (TickStep step : steps) {
            step.action().run();
        }
    }
}
