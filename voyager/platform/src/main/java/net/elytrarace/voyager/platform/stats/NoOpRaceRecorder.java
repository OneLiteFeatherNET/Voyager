package net.elytrarace.voyager.platform.stats;

import net.elytrarace.voyager.api.stats.CupCompletion;
import net.elytrarace.voyager.api.stats.RaceCompletion;
import net.elytrarace.voyager.api.stats.RaceRecorder;

/**
 * The recorder of the {@code off} mode: every completion is accepted and nothing is kept. The cup calls the recorder
 * the same way in every mode, so a server without statistics runs the same code path as one with them.
 */
public final class NoOpRaceRecorder implements RaceRecorder {

    @Override
    public void recordRace(RaceCompletion completion) {
        // Statistics are off: nothing is kept.
    }

    @Override
    public void recordCup(CupCompletion completion) {
        // Statistics are off: nothing is kept.
    }
}
