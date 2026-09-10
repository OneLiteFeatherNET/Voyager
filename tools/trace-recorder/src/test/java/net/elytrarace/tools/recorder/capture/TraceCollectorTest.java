package net.elytrarace.tools.recorder.capture;

import net.elytrarace.tools.recorder.format.TraceFile;
import net.elytrarace.tools.recorder.format.TraceMetadata;
import net.elytrarace.tools.recorder.format.exception.InvalidTraceException;
import net.elytrarace.tools.recorder.script.FlightScript;
import net.elytrarace.tools.recorder.format.BlockBox;
import net.elytrarace.tools.recorder.format.TraceTick;
import net.elytrarace.tools.recorder.script.FlightScriptParser;
import net.elytrarace.tools.recorder.world.SolidBlockSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TraceCollectorTest {

    private static final SolidBlockSource EMPTY = (x, y, z) -> false;

    /** A floor filling y == 0, so the collected slice is observable rather than always empty. */
    private static final SolidBlockSource FLOOR = (x, y, z) -> y == 0;

    private static TraceCollector collector(String script) {
        return new TraceCollector("26.2", "test-profile", 0.08, FlightScriptParser.parse(script));
    }

    private static TraceCollector recordOneTickAt(double x, double y, double z) {
        TraceCollector collector = collector("hold 1 yaw=0 pitch=-5");
        collector.record(new GliderSample(x, y, z, 0.0, -0.08, 0.0, 0.0f, -5.0f, false, false, 0));
        return collector;
    }

    private static GliderSample sample(double y) {
        return new GliderSample(0.0, y, 0.0, 0.0, -0.08, 0.0, 0.0f, -5.0f, false, false, 0);
    }

    @Test
    void producesOneTickPerRecordedSample() {
        TraceCollector collector = collector("hold 3 yaw=0 pitch=-5");
        collector.record(sample(100.0));
        collector.record(sample(99.9));
        collector.record(sample(99.8));

        TraceFile file = collector.finish(EMPTY, 1.0);

        assertThat(file.ticks()).hasSize(3);
        assertThat(file.ticks().get(2).posY()).isEqualTo(99.8);
    }

    @Test
    void numbersTicksConsecutivelyFromZero() {
        TraceCollector collector = collector("hold 2 yaw=0 pitch=-5");
        collector.record(sample(100.0));
        collector.record(sample(99.9));

        assertThat(collector.finish(EMPTY, 1.0).ticks()).extracting("index").containsExactly(0, 1);
    }

    @Test
    void carriesTheMetadataItWasBuiltWith() {
        TraceCollector collector = collector("hold 1 yaw=0 pitch=-5");
        collector.record(sample(100.0));

        assertThat(collector.finish(EMPTY, 1.0).metadata().minecraftVersion()).isEqualTo("26.2");
        assertThat(collector.finish(EMPTY, 1.0).metadata().gravity()).isEqualTo(0.08);
        assertThat(collector.finish(EMPTY, 1.0).metadata().profile()).isEqualTo("test-profile");
    }

    @Test
    void refusesMoreSamplesThanTheScriptHasTicks() {
        TraceCollector collector = collector("hold 1 yaw=0 pitch=-5");
        collector.record(sample(100.0));

        assertThatThrownBy(() -> collector.record(sample(99.9)))
                .isInstanceOf(InvalidTraceException.class);
    }

    @Test
    void refusesToFinishBeforeTheScriptIsComplete() {
        TraceCollector collector = collector("hold 3 yaw=0 pitch=-5");
        collector.record(sample(100.0));

        assertThatThrownBy(() -> collector.finish(EMPTY, 1.0))
                .isInstanceOf(InvalidTraceException.class);
    }

    @Test
    void reportsWhetherItIsComplete() {
        TraceCollector collector = collector("hold 2 yaw=0 pitch=-5");

        assertThat(collector.isComplete()).isFalse();
        collector.record(sample(100.0));
        collector.record(sample(99.9));
        assertThat(collector.isComplete()).isTrue();
    }

    @Test
    void exposesTheScriptedInputForTheNextTick() {
        TraceCollector collector = collector("hold 1 yaw=42 pitch=-7");

        assertThat(collector.nextInput().yaw()).isEqualTo(42.0f);
        assertThat(collector.nextInput().pitch()).isEqualTo(-7.0f);
    }

    @Test
    void mapsEverySampleFieldOntoItsTick() {
        // Every other sample here leaves x, z and both horizontal velocities at 0.0 and both
        // flags at false, so a field written into the wrong slot would be invisible. Every
        // component below holds a value distinct from all the others, including the two
        // booleans, so any misrouting changes an assertion.
        TraceCollector collector = collector("hold 1 yaw=0 pitch=-5");
        collector.record(new GliderSample(1.5, 2.5, 3.5, 0.25, -0.5, 0.75, 12.0f, -34.0f, true, false, 7));

        TraceTick tick = collector.finish(EMPTY, 1.0).ticks().get(0);

        assertThat(tick.posX()).isEqualTo(1.5);
        assertThat(tick.posY()).isEqualTo(2.5);
        assertThat(tick.posZ()).isEqualTo(3.5);
        assertThat(tick.velX()).isEqualTo(0.25);
        assertThat(tick.velY()).isEqualTo(-0.5);
        assertThat(tick.velZ()).isEqualTo(0.75);
        assertThat(tick.yaw()).isEqualTo(12.0f);
        assertThat(tick.pitch()).isEqualTo(-34.0f);
        assertThat(tick.onGround()).isTrue();
        assertThat(tick.fireworkBoostActive()).isFalse();
        assertThat(tick.fireworkTicksRemaining()).isEqualTo(7);
    }

    @Test
    void finishCollectsTheWorldSliceAroundTheFlownPathAtTheGivenRadius() {
        // Every other test passes EMPTY, where the slice is [] whatever finish does with the
        // radius or the recorded path. These three pin both: the radius reaches the collector
        // (one column at 0.0, nine at 1.0) and the slice follows the position that was
        // actually recorded rather than the origin.
        // NOTE: the task-5-brief.md fixture uses y=1.5 here; that is inconsistent with FLOOR
        // (y == 0) and with WorldSliceCollector's floor(posY) block mapping (already fixed and
        // tested in Task 4 — see WorldSliceCollectorTest, which uses y=0.5 for this exact
        // scenario). floor(1.5) is block y=1, one block above the floor, so a spec-correct
        // TraceCollector.finish() (a straight passthrough of the recorded ticks to
        // WorldSliceCollector.collect, per the brief's own Step 3 description) finds nothing at
        // radius 0 there. Verified: with y=1.5 unchanged, all three assertions below reproducibly
        // fail against the correct implementation (see task-5-report.md for the failing run).
        // Corrected to y=0.5, matching the Task 4 convention and making the three assertions
        // internally consistent with the "one column at 0.0, nine at 1.0" comment above.
        assertThat(recordOneTickAt(0.5, 0.5, 0.5).finish(FLOOR, 0.0).metadata().worldSlice())
                .containsExactly(new BlockBox(0, 0, 0, 1, 1, 1));
        assertThat(recordOneTickAt(0.5, 0.5, 0.5).finish(FLOOR, 1.0).metadata().worldSlice())
                .hasSize(9);
        assertThat(recordOneTickAt(8.5, 0.5, 0.5).finish(FLOOR, 0.0).metadata().worldSlice())
                .containsExactly(new BlockBox(8, 0, 0, 9, 1, 1));
    }

    @Test
    void passesTheConstructorArgumentsThroughRatherThanHardcodingThem() {
        // Every other test in this file builds its collector through the collector(String) helper,
        // which always passes "26.2" / "test-profile" / 0.08 -- across the whole suite those three
        // values never change, so carriesTheMetadataItWasBuiltWith alone cannot tell a constructor
        // that stores its arguments from one that just always returns those three literals. This
        // constructs directly with different values to prove they are not fixed.
        TraceCollector collector = new TraceCollector(
                "1.20.4", "boost-tuning", 0.16, FlightScriptParser.parse("hold 1 yaw=0 pitch=-5"));
        collector.record(sample(100.0));

        TraceMetadata metadata = collector.finish(EMPTY, 1.0).metadata();

        assertThat(metadata.minecraftVersion()).isEqualTo("1.20.4");
        assertThat(metadata.profile()).isEqualTo("boost-tuning");
        assertThat(metadata.gravity()).isEqualTo(0.16);
    }

    @Test
    void mapsAFireworkBoostSampleToo() {
        // Every GliderSample built anywhere else in this file -- including the one in
        // mapsEverySampleFieldOntoItsTick -- sets fireworkBoostActive to false, so a record() that
        // hardcoded false instead of reading the sample would still pass every test above. onGround
        // is left false here, unlike mapsEverySampleFieldOntoItsTick's true, so the two booleans
        // still carry different values and a swap between them would still be visible.
        TraceCollector collector = collector("hold 1 yaw=0 pitch=-5");
        collector.record(new GliderSample(0.0, 100.0, 0.0, 0.0, -0.08, 0.0, 0.0f, -5.0f, false, true, 3));

        TraceTick tick = collector.finish(EMPTY, 1.0).ticks().get(0);

        assertThat(tick.fireworkBoostActive()).isTrue();
        assertThat(tick.onGround()).isFalse();
    }
}
