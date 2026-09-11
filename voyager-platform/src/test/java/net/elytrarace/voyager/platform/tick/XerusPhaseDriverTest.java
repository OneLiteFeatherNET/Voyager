package net.elytrarace.voyager.platform.tick;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.physics.CollisionSpace;
import net.elytrarace.voyager.api.physics.FlightInput;
import net.elytrarace.voyager.api.physics.FlightState;
import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.GameMode;
import net.elytrarace.voyager.physics.ElytraSimulator;
import net.elytrarace.voyager.platform.flight.FlightTracker;
import net.elytrarace.voyager.race.flow.RaceClock;
import net.elytrarace.voyager.race.flow.RacePhase;
import net.elytrarace.voyager.race.flow.RaceTimings;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A whole two-map cup driven through Xerus, inside a plain JUnit run with no Minestom server
 * anywhere.
 *
 * <p>That is possible because {@link net.theevilreaper.xerus.api.phase.TickedPhase} is the one
 * Xerus phase that does not schedule itself — it is ticked from outside — and because
 * {@code GamePhase.start()}/{@code finish()} only reach for {@code MinecraftServer} when a phase
 * node was registered, which this driver never does. The Minestom-bound half of Xerus (
 * {@code TickingPhase}, {@code TimedPhase}) is exactly the half that would have made this untestable.
 */
class XerusPhaseDriverTest {

    private static final Duration STEP = Duration.ofMillis(50);

    /** Lobby 1 s, race 2 s, end 0.5 s — 20, 40 and 10 ticks, all three different. */
    private static final RaceTimings TIMINGS =
            new RaceTimings(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofMillis(500));

    private static final int LOBBY_TICKS = 20;
    private static final int GAME_TICKS = 40;
    private static final int END_TICKS = 10;
    private static final int TICKS_PER_MAP = LOBBY_TICKS + GAME_TICKS + END_TICKS;

    private static final CupDefinition CUP =
            new CupDefinition("frost-circuit", List.of("ember-ascent", "glacier-chicane"), GameMode.RACE);

    private static final int TICK_BUDGET = 1_000;

    private static final UUID PLAYER = UUID.fromString("5f2b1a44-3333-4444-8888-abcdefabcdef");

    // ------------------------------------------------------------------------------------------
    // The phase loop
    // ------------------------------------------------------------------------------------------

    /**
     * The first version of this test asserted only the phase, the clock and an empty event log, and
     * a driver with its {@code isRunning()} guard removed satisfied all three — nothing happens
     * <em>visibly</em> in a {@code LOBBY} phase either way. {@code inPhase} is the one value that
     * moves on an unstarted driver's tick, so it is what this asserts, with a started driver as the
     * positive control proving the value is observable and does move.
     */
    @Test
    void aDriverThatWasNeverStartedDoesNotAdvanceTheRaceWhenTicked() {
        RecordingListener listener = new RecordingListener();
        XerusPhaseDriver driver = driver(listener, () -> false);

        driver.onUpdate();
        driver.onUpdate();

        assertThat(listener.events()).isEmpty();
        assertThat(driver.state().phase()).isEqualTo(RacePhase.LOBBY);
        assertThat(driver.state().inPhase()).isEqualTo(Duration.ZERO);
        assertThat(driver.clock().gameTick()).isZero();

        // Positive control: the same two ticks on a started driver do move the phase clock.
        XerusPhaseDriver started = driver(new RecordingListener(), () -> false);
        started.start();
        started.onUpdate();
        started.onUpdate();
        assertThat(started.state().inPhase()).isEqualTo(STEP.multipliedBy(2));
    }

    @Test
    void aWholeCupRunsToItsOwnEndAndFinishesTheXerusPhaseWithIt() {
        RecordingListener listener = new RecordingListener();
        XerusPhaseDriver driver = driver(listener, () -> false);
        AtomicBoolean finishedCallbackRan = new AtomicBoolean();
        driver.setFinishedCallback(() -> finishedCallbackRan.set(true));

        int ticks = run(driver);

        assertThat(ticks).isEqualTo(2 * TICKS_PER_MAP);
        assertThat(driver.state().cupFinished()).isTrue();
        assertThat(driver.isFinished()).isTrue();
        assertThat(finishedCallbackRan).isTrue();

        int eventsBefore = listener.events().size();
        driver.onUpdate();
        assertThat(listener.events()).hasSize(eventsBefore);
    }

    @Test
    void everyMapOfTheCupIsAnnouncedOnceAndPlayedBetweenItsOwnStartAndFinish() {
        RecordingListener listener = new RecordingListener();
        XerusPhaseDriver driver = driver(listener, () -> false);

        run(driver);

        assertThat(listener.events().stream().filter(event -> !event.startsWith("raceTick")).toList())
                .containsExactly(
                        "mapStarted 0 ember-ascent",
                        "mapFinished 0 ember-ascent @40",
                        "mapStarted 1 glacier-chicane",
                        "mapFinished 1 glacier-chicane @40");
        assertThat(listener.raceTicks()).hasSize(2 * GAME_TICKS);
    }

    @Test
    void exactlyOneMovementTickIsPlayedPerStepOfEachGamePhaseAndNoneOutsideOne() {
        RecordingListener listener = new RecordingListener();
        XerusPhaseDriver driver = driver(listener, () -> false);

        run(driver);

        // Between each mapStarted and its mapFinished, exactly GAME_TICKS movement ticks and
        // nothing else — the LOBBY and END phases play none. A driver that played the movement tick
        // before advancing the phase would slip one into the LOBBY -> GAME transition tick and land
        // on GAME_TICKS + 1 here.
        List<String> events = listener.events();
        int started = events.indexOf("mapStarted 0 ember-ascent");
        int finished = events.indexOf("mapFinished 0 ember-ascent @40");
        assertThat(started).isNotNegative();
        assertThat(finished).isGreaterThan(started);
        assertThat(events.subList(started + 1, finished)).hasSize(GAME_TICKS);
        assertThat(events.subList(started + 1, finished)).allSatisfy(event ->
                assertThat(event).startsWith("raceTick"));
    }

    // ------------------------------------------------------------------------------------------
    // The race clock
    // ------------------------------------------------------------------------------------------

    /**
     * The decision this task had to make, pinned through the driver that makes it: the race clock is
     * the driver's own count of played movement ticks, and it runs exactly one step ahead of
     * {@code RaceState.inPhase()} on every one of them.
     */
    @Test
    void theRaceClockIsTheCountOfPlayedMovementTicksAndNotThePhaseClock() {
        List<ClockSample> samples = new ArrayList<>();
        XerusPhaseDriver[] holder = new XerusPhaseDriver[1];
        RecordingListener listener = new RecordingListener() {
            @Override
            public void raceTick(RaceClock clock) {
                super.raceTick(clock);
                samples.add(new ClockSample(clock.gameTick(), clock.elapsed(), holder[0].state().inPhase()));
            }
        };
        XerusPhaseDriver driver = driver(listener, () -> false);
        holder[0] = driver;

        run(driver);

        assertThat(samples).hasSize(2 * GAME_TICKS);
        for (ClockSample sample : samples) {
            assertThat(sample.elapsed())
                    .as("movement tick %s".formatted(sample.gameTick()))
                    .isEqualTo(sample.inPhase().plus(STEP));
            assertThat(sample.elapsed()).isNotEqualTo(sample.inPhase());
        }
        assertThat(samples.getFirst().gameTick()).isEqualTo(1);
        assertThat(samples.getFirst().elapsed()).isEqualTo(STEP);
        assertThat(samples.get(GAME_TICKS - 1).elapsed()).isEqualTo(TIMINGS.race());
        assertThat(samples.get(GAME_TICKS - 1).inPhase()).isEqualTo(TIMINGS.race().minus(STEP));
    }

    @Test
    void theRaceClockRestartsOnEachMapOfTheCup() {
        RecordingListener listener = new RecordingListener();
        XerusPhaseDriver driver = driver(listener, () -> false);

        run(driver);

        assertThat(listener.raceTicks()).extracting(RaceClock::gameTick)
                .startsWith(1, 2, 3)
                .endsWith(GAME_TICKS - 2, GAME_TICKS - 1, GAME_TICKS);
        assertThat(listener.raceTicks().get(GAME_TICKS).gameTick())
                .as("the second map starts its own clock at one, not at %s".formatted(GAME_TICKS + 1))
                .isEqualTo(1);
    }

    @Test
    void aGamePhaseEndedEarlyStopsPlayingMovementTicksAtThatPoint() {
        RecordingListener listener = new RecordingListener();
        // Every player of map one is finished from its tenth movement tick onwards. Map two is left
        // to run its full length, so this proves an early end rather than a permanently stuck flag.
        XerusPhaseDriver driver =
                driver(listener, () -> listener.currentMapIndex() == 0 && listener.ticksOnCurrentMap() >= 10);

        run(driver);

        // The state machine sees the flag on the tick after the tenth, so the tenth is the last
        // movement tick of map one.
        assertThat(listener.events()).contains("mapFinished 0 ember-ascent @10");
        assertThat(listener.events()).contains("mapFinished 1 glacier-chicane @40");
        assertThat(listener.raceTicks()).hasSize(10 + GAME_TICKS);
    }

    // ------------------------------------------------------------------------------------------
    // Both halves wired together
    // ------------------------------------------------------------------------------------------

    /**
     * The whole point of the split: the phase driver counts the ticks and the flight driver plays
     * them, and one GAME phase produces exactly as many simulated flight ticks as it has steps.
     */
    @Test
    void theFlightDriverAdvancesExactlyOncePerMovementTickOfTheGamePhase() {
        FlightTracker tracker = new FlightTracker();
        ScriptedGlide glide = new ScriptedGlide();
        FlightTickDriver flightDriver = new FlightTickDriver(glide, tracker, CollisionSpace.empty());
        List<FlightTick> flown = new ArrayList<>();

        RecordingListener listener = new RecordingListener() {
            @Override
            public void raceTick(RaceClock clock) {
                super.raceTick(clock);
                flown.addAll(flightDriver.tick());
            }
        };
        XerusPhaseDriver driver = driver(listener, () -> false);

        run(driver);

        assertThat(flown).hasSize(2 * GAME_TICKS);

        FlightState expected = glide.seed();
        for (int index = 0; index < flown.size(); index++) {
            expected = ElytraSimulator.tick(expected, ScriptedGlide.inputAt(index), CollisionSpace.empty());
        }
        assertThat(flown.getLast().after()).isEqualTo(expected);
    }

    // ------------------------------------------------------------------------------------------
    // Fixture
    // ------------------------------------------------------------------------------------------

    private static XerusPhaseDriver driver(RacePhaseListener listener, BooleanSupplier finished) {
        return new XerusPhaseDriver(CUP, TIMINGS, STEP, finished, listener);
    }

    private static int run(XerusPhaseDriver driver) {
        driver.start();
        int ticks = 0;
        while (!driver.state().cupFinished() && ticks < TICK_BUDGET) {
            driver.onUpdate();
            ticks++;
        }
        return ticks;
    }

    private record ClockSample(int gameTick, Duration elapsed, Duration inPhase) {
    }

    private static class RecordingListener implements RacePhaseListener {

        private final List<String> events = new ArrayList<>();
        private final List<RaceClock> raceTicks = new ArrayList<>();
        private int currentMapIndex = -1;
        private int ticksOnCurrentMap;

        List<String> events() {
            return List.copyOf(events);
        }

        List<RaceClock> raceTicks() {
            return List.copyOf(raceTicks);
        }

        int currentMapIndex() {
            return currentMapIndex;
        }

        int ticksOnCurrentMap() {
            return ticksOnCurrentMap;
        }

        @Override
        public void mapStarted(int mapIndex, String mapName) {
            events.add("mapStarted %s %s".formatted(mapIndex, mapName));
            currentMapIndex = mapIndex;
            ticksOnCurrentMap = 0;
        }

        @Override
        public void raceTick(RaceClock clock) {
            events.add("raceTick %s".formatted(clock.gameTick()));
            raceTicks.add(clock);
            ticksOnCurrentMap++;
        }

        @Override
        public void mapFinished(int mapIndex, String mapName, RaceClock clock) {
            events.add("mapFinished %s %s @%s".formatted(mapIndex, mapName, clock.gameTick()));
        }
    }

    /** One player gliding for as long as anything asks, with a rotation that changes every tick. */
    private static final class ScriptedGlide implements FlightSampler {

        private int index;

        static FlightInput inputAt(int index) {
            return new FlightInput(17.0f + 4.5f * index, -14.0f + 2.75f * index, false, 0, 0.08);
        }

        FlightState seed() {
            return new FlightState(new Vec3(0.5, 120.0, 0.5), new Vec3(0.33, -0.21, 0.44),
                    inputAt(0).yaw(), inputAt(0).pitch(), false);
        }

        @Override
        public List<FlightSample> sampleAtTickBoundary() {
            FlightInput input = inputAt(index++);
            return List.of(new FlightSample(PLAYER, true, new Vec3(0.5, 120.0, 0.5),
                    new Vec3(0.33, -0.21, 0.44), false, input));
        }
    }
}
