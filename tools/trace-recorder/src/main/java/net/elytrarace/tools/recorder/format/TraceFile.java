package net.elytrarace.tools.recorder.format;

import net.elytrarace.tools.recorder.format.exception.InvalidTraceException;

import java.util.List;

/** A complete recording: what was flown, in what world, and every sampled tick in order. */
public record TraceFile(TraceMetadata metadata, List<TraceTick> ticks) {

    public TraceFile {
        if (ticks.isEmpty()) {
            throw new InvalidTraceException("a trace must contain at least one tick");
        }
        for (int i = 0; i < ticks.size(); i++) {
            if (ticks.get(i).index() != i) {
                throw new InvalidTraceException(
                        "tick indices must be consecutive from zero; position %s holds index %s"
                                .formatted(i, ticks.get(i).index()));
            }
        }
        ticks = List.copyOf(ticks);
    }
}
