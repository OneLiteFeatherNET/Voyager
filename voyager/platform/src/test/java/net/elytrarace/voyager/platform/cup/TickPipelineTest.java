package net.elytrarace.voyager.platform.cup;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The per-tick order of a cup, declared once and run in that order. Pure: each test builds its own steps. */
class TickPipelineTest {

    @Test
    void runsEachStepInTheOrderItWasDeclared() {
        List<String> order = new ArrayList<>();
        TickPipeline pipeline = TickPipeline.of(List.of(
                new TickStep("flight sample", () -> order.add("flight sample")),
                new TickStep("boost burn", () -> order.add("boost burn")),
                new TickStep("phase advance", () -> order.add("phase advance"))));

        pipeline.run();

        assertThat(order).containsExactly("flight sample", "boost burn", "phase advance");
    }

    @Test
    void runsEachStepOnceForEachRun() {
        List<String> order = new ArrayList<>();
        TickPipeline pipeline = TickPipeline.of(List.of(new TickStep("only", () -> order.add("only"))));

        pipeline.run();
        pipeline.run();

        assertThat(order).containsExactly("only", "only");
    }

    @Test
    void keepsTheNameOfEachStepInDeclaredOrder() {
        TickPipeline pipeline = TickPipeline.of(List.of(
                new TickStep("flight sample", () -> { }),
                new TickStep("phase advance", () -> { })));

        assertThat(pipeline.steps()).extracting(TickStep::name)
                .containsExactly("flight sample", "phase advance");
    }

    @Test
    void theBuiltStepListCannotBeChanged() {
        TickPipeline pipeline = TickPipeline.of(List.of(new TickStep("only", () -> { })));

        assertThatThrownBy(() -> pipeline.steps().add(new TickStep("intruder", () -> { })))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void refusesAPipelineWithNoSteps() {
        assertThatThrownBy(() -> TickPipeline.of(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aStepThatThrowsStopsTheTickAndLeavesTheLaterStepsUnrun() {
        List<String> order = new ArrayList<>();
        TickPipeline pipeline = TickPipeline.of(List.of(
                new TickStep("flight sample", () -> order.add("flight sample")),
                new TickStep("boost burn", () -> {
                    throw new IllegalStateException("broken");
                }),
                new TickStep("phase advance", () -> order.add("phase advance"))));

        assertThatThrownBy(pipeline::run).isInstanceOf(IllegalStateException.class);

        assertThat(order).containsExactly("flight sample");
    }
}
