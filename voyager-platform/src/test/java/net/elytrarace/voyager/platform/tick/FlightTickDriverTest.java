package net.elytrarace.voyager.platform.tick;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.physics.CollisionSpace;
import net.elytrarace.voyager.api.physics.FlightInput;
import net.elytrarace.voyager.api.physics.FlightState;
import net.elytrarace.voyager.physics.ElytraSimulator;
import net.elytrarace.voyager.platform.flight.FlightEndReason;
import net.elytrarace.voyager.platform.flight.FlightTracker;
import net.elytrarace.voyager.platform.flight.FlightTransition;
import net.elytrarace.voyager.platform.tick.exception.UntrackedFlightException;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The order within a tick is what this file is for.
 *
 * <p>Three of these tests would pass under a driver that is shifted by one tick, if they were
 * written the obvious way — "the run matches a fold over the script" is true of a shifted driver
 * too, as long as the reference fold is shifted the same way. So each of them carries its own
 * falsifier: the shifted expectation is computed as well, and asserted to <em>differ</em>. A script
 * whose inputs happen not to matter would make the falsifier fail, which is the point — a varied
 * value is not yet a load-bearing one.
 */
class FlightTickDriverTest {

    private static final UUID PLAYER = UUID.fromString("0d1c9b6a-1111-4444-8888-abcdefabcdef");
    private static final UUID OTHER_PLAYER = UUID.fromString("0d1c9b6a-2222-4444-8888-abcdefabcdef");

    private static final Vec3 SPAWN = new Vec3(0.5, 100.0, 0.5);
    private static final Vec3 ENTRY_VELOCITY = new Vec3(0.42, -0.17, 0.31);

    private static final CollisionSpace OPEN_SKY = CollisionSpace.empty();

    private static final int TICKS = 12;

    /**
     * Yaw and pitch both move every tick and neither repeats, so no two ticks of the script share an
     * input. Every falsifier below depends on that.
     */
    private static FlightInput inputAt(int index) {
        return new FlightInput(31.0f + 7.5f * index, -22.0f + 3.25f * index, false, 0, 0.08);
    }

    private static FlightSample flying(int index) {
        return new FlightSample(PLAYER, true, SPAWN, ENTRY_VELOCITY, false, inputAt(index));
    }

    private static FlightState seed() {
        return new FlightState(SPAWN, ENTRY_VELOCITY, inputAt(0).yaw(), inputAt(0).pitch(), false);
    }

    // ----------------------------------------------------------------------------------------
    // The first input, which the E2a recorder twice failed to apply
    // ----------------------------------------------------------------------------------------

    @Test
    void aFlightStartsSeededFromTheObservationAndTheSampledInputIsAppliedOnThatVeryTick() {
        ScriptedSampler sampler = ScriptedSampler.of(flying(0));
        FlightTickDriver driver = new FlightTickDriver(sampler, new FlightTracker(), OPEN_SKY);

        List<FlightTick> ticks = driver.tick();

        assertThat(ticks).hasSize(1);
        FlightTick tick = ticks.getFirst();
        assertThat(tick.playerId()).isEqualTo(PLAYER);
        assertThat(tick.transition()).isInstanceOf(FlightTransition.Started.class);
        assertThat(tick.input()).isEqualTo(inputAt(0));
        assertThat(tick.before()).isEqualTo(seed());
        assertThat(tick.after()).isEqualTo(ElytraSimulator.tick(seed(), inputAt(0), OPEN_SKY));

        // The falsifier for "input[0] was never applied": a driver that only seeds and defers the
        // first step to the next tick leaves after == before, and every later value stays
        // self-consistent while being one tick behind.
        assertThat(tick.after()).isNotEqualTo(tick.before());
    }

    @Test
    void everySampledInputIsAppliedExactlyOnceAndInOrder() {
        ScriptedSampler sampler = scriptOf(TICKS);
        FlightTickDriver driver = new FlightTickDriver(sampler, new FlightTracker(), OPEN_SKY);

        List<FlightTick> ticks = drive(driver, TICKS);

        assertThat(sampler.calls()).isEqualTo(TICKS);
        assertThat(ticks).hasSize(TICKS);
        assertThat(ticks).extracting(FlightTick::input)
                .containsExactlyElementsOf(inputsOf(TICKS));
    }

    // ----------------------------------------------------------------------------------------
    // The pairing, which the E2a recorder got wrong the second time
    // ----------------------------------------------------------------------------------------

    @Test
    void eachTickPairsTheStateItStartedFromWithTheInputThatConsumedIt() {
        FlightTickDriver driver = new FlightTickDriver(scriptOf(TICKS), new FlightTracker(), OPEN_SKY);

        List<FlightTick> ticks = drive(driver, TICKS);

        for (int index = 0; index < ticks.size(); index++) {
            FlightTick tick = ticks.get(index);
            if (index > 0) {
                assertThat(tick.before())
                        .as("tick %s starts from where tick %s ended".formatted(index, index - 1))
                        .isEqualTo(ticks.get(index - 1).after());
            }
            assertThat(tick.after())
                    .as("tick %s applies its own input to its own entry state".formatted(index))
                    .isEqualTo(ElytraSimulator.tick(tick.before(), tick.input(), OPEN_SKY));

            if (index == 0) {
                continue;
            }
            // The falsifier: the previous tick's input, applied to this tick's entry state, must
            // produce something else. Without this, a driver that pairs state k with input k-1 —
            // the recorder's second defect, "the rotation recorded at k drove the transition into k"
            // — satisfies every assertion above.
            assertThat(ElytraSimulator.tick(tick.before(), ticks.get(index - 1).input(), OPEN_SKY))
                    .as("tick %s's input is load-bearing, not merely varied".formatted(index))
                    .isNotEqualTo(tick.after());
        }
    }

    @Test
    void theRunFoldsTheScriptInOrderAndNotTheScriptShiftedByOneTick() {
        FlightTickDriver driver = new FlightTickDriver(scriptOf(TICKS), new FlightTracker(), OPEN_SKY);

        List<FlightTick> ticks = drive(driver, TICKS);

        FlightState inOrder = seed();
        for (int index = 0; index < TICKS; index++) {
            inOrder = ElytraSimulator.tick(inOrder, inputAt(index), OPEN_SKY);
        }
        assertThat(ticks.getLast().after()).isEqualTo(inOrder);

        // Shifting the script by one and folding again is exactly what a driver that samples after
        // the tick produces. It has to come out different, or this test proves nothing.
        FlightState shifted = seed();
        for (int index = 0; index < TICKS; index++) {
            shifted = ElytraSimulator.tick(shifted, inputAt(Math.max(0, index - 1)), OPEN_SKY);
        }
        assertThat(shifted).isNotEqualTo(inOrder);
    }

    // ----------------------------------------------------------------------------------------
    // Authority: the client flies, the server simulates alongside
    // ----------------------------------------------------------------------------------------

    @Test
    void aContinuingFlightAdvancesTheSimulatedStateAndIsNotReseededFromTheObservation() {
        Vec3 nonsense = new Vec3(9_000.0, 9_000.0, 9_000.0);
        ScriptedSampler sampler = ScriptedSampler.of(
                flying(0),
                new FlightSample(PLAYER, true, nonsense, nonsense, true, inputAt(1)));
        FlightTickDriver driver = new FlightTickDriver(sampler, new FlightTracker(), OPEN_SKY);

        List<FlightTick> ticks = drive(driver, 2);

        FlightTick second = ticks.get(1);
        assertThat(second.transition()).isInstanceOf(FlightTransition.Continuing.class);
        assertThat(second.before()).isEqualTo(ticks.getFirst().after());
        assertThat(second.after())
                .isEqualTo(ElytraSimulator.tick(second.before(), inputAt(1), OPEN_SKY));
        // Still in the neighbourhood it started from, nowhere near the observation it ignored.
        assertThat(second.after().position().x()).isLessThan(10.0);
        assertThat(second.after().position().y()).isBetween(90.0, 101.0);
        assertThat(second.after().position().z()).isLessThan(10.0);
    }

    @Test
    void severalFlyingPlayersAreAllAdvancedWithinOneTick() {
        FlightSample first = flying(0);
        FlightSample second = new FlightSample(
                OTHER_PLAYER, true, new Vec3(-4.5, 70.0, 12.5), ENTRY_VELOCITY, false, inputAt(4));
        ScriptedSampler sampler = ScriptedSampler.ofBatches(List.of(List.of(first, second)));
        FlightTickDriver driver = new FlightTickDriver(sampler, new FlightTracker(), OPEN_SKY);

        List<FlightTick> ticks = driver.tick();

        assertThat(ticks).extracting(FlightTick::playerId).containsExactly(PLAYER, OTHER_PLAYER);
        assertThat(ticks).allSatisfy(tick ->
                assertThat(tick.after()).isEqualTo(ElytraSimulator.tick(tick.before(), tick.input(), OPEN_SKY)));
        assertThat(driver.hasSimulatedState(PLAYER)).isTrue();
        assertThat(driver.hasSimulatedState(OTHER_PLAYER)).isTrue();
    }

    @Test
    void aPlayerWhoIsNotFlyingProducesNoTickAndNoSimulatedState() {
        ScriptedSampler sampler = ScriptedSampler.of(
                new FlightSample(PLAYER, false, SPAWN, Vec3.ZERO, true, inputAt(0)));
        FlightTickDriver driver = new FlightTickDriver(sampler, new FlightTracker(), OPEN_SKY);

        assertThat(driver.tick()).isEmpty();
        assertThat(driver.hasSimulatedState(PLAYER)).isFalse();
    }

    // ----------------------------------------------------------------------------------------
    // Ending and forgetting
    // ----------------------------------------------------------------------------------------

    @Test
    void anEndedFlightSimulatesNothingFurtherAndReportsTheFinalState() {
        ScriptedSampler sampler = ScriptedSampler.of(
                flying(0),
                flying(1),
                new FlightSample(PLAYER, false, SPAWN, Vec3.ZERO, true, inputAt(2)));
        FlightTickDriver driver = new FlightTickDriver(sampler, new FlightTracker(), OPEN_SKY);

        List<FlightTick> ticks = drive(driver, 3);

        FlightTick ending = ticks.getLast();
        assertThat(ending.transition())
                .isEqualTo(FlightTransition.ended(FlightEndReason.NO_STOP_EVENT_OBSERVED));
        assertThat(ending.before()).isEqualTo(ticks.get(1).after());
        assertThat(ending.after()).isEqualTo(ending.before());
        assertThat(driver.hasSimulatedState(PLAYER)).isFalse();
    }

    /**
     * The seam test. {@code forget} clearing the simulated state cannot be observed through
     * {@link FlightTickDriver#tick()} at all: the tracker is forgotten in the same call, so the very
     * next tick reports {@code Started} and re-seeds over whatever was left behind. Only
     * {@link FlightTickDriver#hasSimulatedState(UUID)} can tell a cleared map from a stale one —
     * the same move Task 3 and Task 4 made for the same reason.
     */
    @Test
    void forgettingAPlayerDropsTheSimulatedStateNoLaterTickCouldReveal() {
        FlightTickDriver driver =
                new FlightTickDriver(ScriptedSampler.of(flying(0)), new FlightTracker(), OPEN_SKY);
        driver.tick();
        assertThat(driver.hasSimulatedState(PLAYER)).isTrue();

        driver.forget(PLAYER);

        assertThat(driver.hasSimulatedState(PLAYER)).isFalse();
    }

    @Test
    void aContinuingFlightWithNoSimulatedStateIsRefusedRatherThanSilentlyReseeded() {
        // The tracker is advanced behind the driver's back, so it reports Continuing for a player
        // the driver never seeded — the one way the two can fall out of step.
        FlightTracker tracker = new FlightTracker();
        tracker.tick(PLAYER, true);
        FlightTickDriver driver =
                new FlightTickDriver(ScriptedSampler.of(flying(1)), tracker, OPEN_SKY);

        assertThatThrownBy(driver::tick)
                .isInstanceOf(UntrackedFlightException.class)
                .hasMessageContaining(PLAYER.toString());
    }

    // ----------------------------------------------------------------------------------------
    // Fixture
    // ----------------------------------------------------------------------------------------

    private static List<FlightTick> drive(FlightTickDriver driver, int ticks) {
        List<FlightTick> all = new ArrayList<>();
        for (int tick = 0; tick < ticks; tick++) {
            all.addAll(driver.tick());
        }
        return List.copyOf(all);
    }

    private static ScriptedSampler scriptOf(int ticks) {
        List<FlightSample> samples = new ArrayList<>(ticks);
        for (int index = 0; index < ticks; index++) {
            samples.add(flying(index));
        }
        return ScriptedSampler.of(samples.toArray(FlightSample[]::new));
    }

    private static List<FlightInput> inputsOf(int ticks) {
        List<FlightInput> inputs = new ArrayList<>(ticks);
        for (int index = 0; index < ticks; index++) {
            inputs.add(inputAt(index));
        }
        return List.copyOf(inputs);
    }

    /** Hands out one scripted batch per call and counts the calls, so a skipped tick is visible. */
    private static final class ScriptedSampler implements FlightSampler {

        private final Deque<List<FlightSample>> batches;
        private int calls;

        private ScriptedSampler(List<List<FlightSample>> batches) {
            this.batches = new ArrayDeque<>(batches);
        }

        static ScriptedSampler of(FlightSample... samples) {
            return ofBatches(List.of(samples).stream().map(List::of).toList());
        }

        static ScriptedSampler ofBatches(List<List<FlightSample>> batches) {
            return new ScriptedSampler(batches);
        }

        int calls() {
            return calls;
        }

        @Override
        public List<FlightSample> sampleAtTickBoundary() {
            calls++;
            List<FlightSample> next = batches.pollFirst();
            return next == null ? List.of() : next;
        }
    }
}
