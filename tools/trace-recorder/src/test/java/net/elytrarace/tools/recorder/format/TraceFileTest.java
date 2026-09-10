package net.elytrarace.tools.recorder.format;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.elytrarace.tools.recorder.format.exception.InvalidTraceException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TraceFileTest {

    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private static TraceTick tick(int index, double y) {
        return new TraceTick(index, 0.0, y, 0.0, 0.0, -0.08, 0.0, 0.0f, -5.0f, false, false, 0);
    }

    private static TraceMetadata metadata() {
        return new TraceMetadata("26.2", "steady-glide", 0.08, 1, List.of());
    }

    @Test
    void roundTripsThroughJsonUnchanged() {
        TraceFile original = new TraceFile(metadata(), List.of(tick(0, 100.0), tick(1, 99.92)));

        TraceFile restored = GSON.fromJson(GSON.toJson(original), TraceFile.class);

        assertThat(restored).isEqualTo(original);
    }

    @Test
    void rejectsAnEmptyTickList() {
        assertThatThrownBy(() -> new TraceFile(metadata(), List.of()))
                .isInstanceOf(InvalidTraceException.class);
    }

    @Test
    void rejectsTicksThatAreNotConsecutiveFromZero() {
        assertThatThrownBy(() -> new TraceFile(metadata(), List.of(tick(0, 100.0), tick(2, 99.0))))
                .isInstanceOf(InvalidTraceException.class);
    }

    @Test
    void rejectsNonFiniteSamples() {
        // TraceTick validates finiteness in its own compact constructor, so a non-finite sample can
        // never reach TraceFile in the first place — constructing it here throws immediately, so the
        // assertion must wrap the TraceTick construction itself, not a TraceFile built from an
        // already-invalid tick (which could never exist).
        assertThatThrownBy(() -> new TraceTick(0, 0.0, Double.NaN, 0.0, 0.0, 0.0, 0.0, 0.0f, 0.0f, false, false, 0))
                .isInstanceOf(InvalidTraceException.class);
    }

    @Test
    void rejectsANonPositiveGravity() {
        assertThatThrownBy(() -> new TraceMetadata("26.2", "steady-glide", 0.0, 1, List.of()))
                .isInstanceOf(InvalidTraceException.class);
    }

    @Test
    void keepsTheTickListImmutable() {
        TraceFile file = new TraceFile(metadata(), List.of(tick(0, 100.0)));

        assertThatThrownBy(() -> file.ticks().add(tick(1, 99.0)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsABlankMinecraftVersion() {
        assertThatThrownBy(() -> new TraceMetadata(" ", "steady-glide", 0.08, 1, List.of()))
                .isInstanceOf(InvalidTraceException.class);
    }

    @Test
    void rejectsABlankProfile() {
        assertThatThrownBy(() -> new TraceMetadata("26.2", " ", 0.08, 1, List.of()))
                .isInstanceOf(InvalidTraceException.class);
    }

    @Test
    void rejectsAFormatVersionBelowOne() {
        assertThatThrownBy(() -> new TraceMetadata("26.2", "steady-glide", 0.08, 0, List.of()))
                .isInstanceOf(InvalidTraceException.class);
    }

    @Test
    void rejectsAMissingWorldSlice() {
        assertThatThrownBy(() -> new TraceMetadata("26.2", "steady-glide", 0.08, 1, null))
                .isInstanceOf(InvalidTraceException.class);
    }

    @Test
    void rejectsANegativeTickIndex() {
        assertThatThrownBy(() -> new TraceTick(-1, 0.0, 100.0, 0.0, 0.0, -0.08, 0.0, 0.0f, -5.0f, false, false, 0))
                .isInstanceOf(InvalidTraceException.class);
    }

    @Test
    void rejectsANonFiniteRotation() {
        assertThatThrownBy(
                () -> new TraceTick(0, 0.0, 100.0, 0.0, 0.0, -0.08, 0.0, Float.NaN, -5.0f, false, false, 0))
                .isInstanceOf(InvalidTraceException.class);
    }

    @Test
    void rejectsANegativeFireworkTickCount() {
        assertThatThrownBy(() -> new TraceTick(0, 0.0, 100.0, 0.0, 0.0, -0.08, 0.0, 0.0f, -5.0f, false, false, -1))
                .isInstanceOf(InvalidTraceException.class);
    }

    @Test
    void rejectsABlockBoxWhoseMinimumExceedsItsMaximum() {
        assertThatThrownBy(() -> new BlockBox(0.0, 0.0, 0.0, -1.0, 1.0, 1.0))
                .isInstanceOf(InvalidTraceException.class);
    }

    @Test
    void acceptsAValidBlockBoxAsPartOfAWorldSlice() {
        BlockBox box = new BlockBox(0.0, 64.0, 0.0, 1.0, 65.0, 1.0);

        TraceMetadata metadata = new TraceMetadata("26.2", "steady-glide", 0.08, 1, List.of(box));

        assertThat(metadata.worldSlice()).containsExactly(box);
    }
}
