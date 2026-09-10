package net.elytrarace.voyager.physics.trace;

import com.google.gson.Gson;

import net.elytrarace.voyager.physics.trace.exception.InvalidTraceFixtureException;

/**
 * Reads and writes {@link TraceFixture} in E2a's fixture JSON format.
 *
 * <p>Fixture reading is not production behaviour — this class exists only in {@code
 * voyager-physics}'s test source set, alongside the rest of the replay harness. Gson deserialises
 * records through their canonical constructor, so a malformed or incomplete document surfaces as
 * whatever {@link TraceFixture}'s own validation throws; this loader exists to guarantee that
 * boundary is always an {@link InvalidTraceFixtureException} rather than a raw Gson exception, a
 * {@code NullPointerException}, or a silently accepted {@code null}.
 */
public abstract class TraceFixtureLoader {

    private static final Gson GSON = new Gson();

    private TraceFixtureLoader() {
    }

    /**
     * Parses {@code json} into a {@link TraceFixture}. Fails with {@link
     * InvalidTraceFixtureException} for blank input, syntactically invalid JSON, a document missing
     * required fields, or one whose values fail {@link TraceFixture}'s own invariants — never with a
     * {@code NullPointerException}.
     */
    public static TraceFixture fromJson(String json) {
        if (json == null || json.isBlank()) {
            throw new InvalidTraceFixtureException("fixture JSON must not be null or blank");
        }
        try {
            TraceFixture fixture = GSON.fromJson(json, TraceFixture.class);
            if (fixture == null) {
                throw new InvalidTraceFixtureException("fixture JSON did not contain a trace fixture");
            }
            return fixture;
        } catch (InvalidTraceFixtureException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new InvalidTraceFixtureException(
                    "fixture JSON could not be parsed: %s".formatted(e.getMessage()));
        }
    }

    /** Serialises {@code fixture} into E2a's fixture JSON format. */
    public static String toJson(TraceFixture fixture) {
        if (fixture == null) {
            throw new InvalidTraceFixtureException("fixture must not be null");
        }
        return GSON.toJson(fixture);
    }
}
