package net.elytrarace.voyager.persistence;

import java.util.ArrayList;
import java.util.List;

/** Keeps every warning and error line the writer logs, so a test asserts on what was said. */
final class RecordingStatsLog implements StatsLog {

    private final List<String> warnings = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();

    @Override
    public void warn(String message) {
        warnings.add(message);
    }

    @Override
    public void error(String message) {
        errors.add(message);
    }

    List<String> warnings() {
        return List.copyOf(warnings);
    }

    List<String> errors() {
        return List.copyOf(errors);
    }
}
