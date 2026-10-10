package net.elytrarace.voyager.platform.tick;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.physics.CollisionSpace;
import net.elytrarace.voyager.api.physics.FlightInput;
import net.elytrarace.voyager.api.physics.FlightState;
import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.GameMode;
import net.elytrarace.voyager.api.race.GuideLine;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;
import net.elytrarace.voyager.physics.ElytraSimulator;
import net.elytrarace.voyager.platform.flight.FlightTracker;
import net.elytrarace.voyager.race.flow.RaceClock;
import net.elytrarace.voyager.race.flow.RacePhase;
import net.elytrarace.voyager.race.flow.RaceTimings;
import net.elytrarace.voyager.race.run.RaceRun;

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

    /**
     * Lobby 1 s, race 2 s, end 0.5 s — 20, 40 and 10 ticks, all three different, and the same 10 for
     * both ends so that {@code TICKS_PER_MAP} means what it says on both maps.
     */
    private static final RaceTimings TIMINGS = new RaceTimings(
            Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofMillis(500), Duration.ofMillis(500));

    private static final int LOBBY_TICKS = 20;
    private static final int GAME_TICKS = 40;
    private static final int END_TICKS = 10;
    private static final int TICKS_PER_MAP = LOBBY_TICKS + GAME_TICKS + END_TICKS;

    private static final CupDefinition CUP =
            new CupDefinition("frost-circuit", List.of("ember-ascent", "glacier-chicane"), GameMode.RACE);

    private static final int TICK_BUDGET = 1_000;

    private static final UUID PLAYER = UUID.fromString("5f2b1a44-3333-4444-8888-abcdefabcdef");

    // ------------------------------------------------------------------------------------------
    // A one-ring course per map, for the run that decides when its own phase ends
    // ------------------------------------------------------------------------------------------

    /** The lane a {@link Racer} flies: constant x and y, z advancing three blocks a movement tick. */
    private static final double LANE_X = 0.5;
    private static final double LANE_Y = 70.0;
    private static final double SPAWN_Z = 2.0;
    private static final double BLOCKS_PER_TICK = 3.0;

    private static final Vec3 COURSE_SPAWN = new Vec3(LANE_X, LANE_Y, SPAWN_Z);

    /**
     * The normals are tilted, so a crossing does not sit on the ring's own {@code z}:
     * {@code 17.0 + (0.6 * (2.0 - 0.5)) / 0.8 = 18.125}, and
     * {@code ceil((18.125 - 2.0) / 3.0)} is movement tick 6.
     */
    private static final MapDefinition EMBER_ASCENT = new MapDefinition("ember-ascent", "ember_arena",
            COURSE_SPAWN,
            List.of(new Ring(0, new Vec3(2.0, 68.0, 17.0), new Vec3(0.6, 0.0, 0.8), 5.0, 7, RingType.STANDARD)),
            Duration.ofSeconds(3), new BoostConfig(12, 25), new GuideLine(List.of(), 3, 1.5));

    /**
     * Tilted the other way and further out: {@code 26.0 + (0.6 * (72.5 - 70.0)) / 0.8 = 27.875}, and
     * {@code ceil((27.875 - 2.0) / 3.0)} is movement tick 9.
     */
    private static final MapDefinition GLACIER_CHICANE = new MapDefinition("glacier-chicane", "glacier_arena",
            COURSE_SPAWN,
            List.of(new Ring(0, new Vec3(-1.0, 72.5, 26.0), new Vec3(0.0, 0.6, 0.8), 6.0, 11, RingType.BOOST)),
            Duration.ofSeconds(3), new BoostConfig(19, 44), new GuideLine(List.of(), 2, 0.75));

    private static final List<MapDefinition> COURSES = List.of(EMBER_ASCENT, GLACIER_CHICANE);

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

    /**
     * The lobby is reported every tick, with the time still to come after that tick.
     *
     * <p>This is what a start countdown is driven by, and the value has to be the time <em>left</em>
     * rather than the time spent — a countdown built on the wrong one counts up. The first lobby tick
     * of a one-second lobby leaves 950 ms and the last leaves 50 ms; there is no tick reporting zero,
     * because the tick that would have is the tick that enters {@code GAME}, and that one reports
     * {@code mapStarted} instead.
     */
    @Test
    void everyLobbyTickReportsHowMuchLobbyIsLeftAfterIt() {
        RecordingListener listener = new RecordingListener();
        XerusPhaseDriver driver = driver(listener, () -> false);
        driver.start();

        for (int tick = 0; tick < LOBBY_TICKS; tick++) {
            driver.onUpdate();
        }

        assertThat(listener.lobbyRemaining()).hasSize(LOBBY_TICKS - 1);
        assertThat(listener.lobbyRemaining().getFirst()).isEqualTo(TIMINGS.lobby().minus(STEP));
        assertThat(listener.lobbyRemaining().getLast()).isEqualTo(STEP);
        assertThat(listener.lobbyRemaining()).doesNotContain(Duration.ZERO);
        assertThat(listener.events()).contains("mapStarted 0 ember-ascent");
        assertThat(listener.events().indexOf("mapStarted 0 ember-ascent"))
                .as("every lobby tick is reported before the map starts")
                .isGreaterThan(listener.events().indexOf("lobbyTick 0 ember-ascent %s".formatted(STEP.toMillis())));
    }

    /** The second map's lobby names the second map, so a countdown cannot announce the wrong course. */
    @Test
    void theSecondMapsLobbyNamesTheSecondMap() {
        RecordingListener listener = new RecordingListener();
        XerusPhaseDriver driver = driver(listener, () -> false);
        driver.start();

        for (int tick = 0; tick < TICKS_PER_MAP + 1; tick++) {
            driver.onUpdate();
        }

        assertThat(listener.events()).contains("lobbyTick 1 glacier-chicane %s".formatted(
                TIMINGS.lobby().minus(STEP).toMillis()));
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

        assertThat(listener.events().stream()
                .filter(event -> !event.startsWith("raceTick") && !event.startsWith("lobbyTick"))
                .toList())
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

    /**
     * The per-player race state this driver deliberately does not hold is a {@link RaceRun}, and the
     * two have to agree on what tick it is across a module boundary.
     *
     * <p>{@code XerusPhaseDriver} knows the clock and nothing about players; a run knows a player and
     * nothing about phases. The only thing joining them is the {@link RaceClock} handed to
     * {@link RacePhaseListener#raceTick}, and the only way to tell a correct join from an off-by-one
     * is to make the phase's own end depend on it: the {@code everyPlayerFinished} supplier here is
     * the run's own {@link RaceRun#finished()}, so a run that recorded its finish a tick late would
     * move the tick the {@code GAME} phase ends on.
     *
     * <p>The two courses finish on different ticks — six and nine, both well inside the phase's
     * forty — so neither number can be a constant and the clock is visibly restarted per map.
     */
    @Test
    void aRunAdvancedFromTheDriversRaceTickEndsTheGamePhaseOnTheTickItFinishedOn() {
        Racer racer = new Racer();
        XerusPhaseDriver driver = driver(racer, racer::finished);

        run(driver);

        assertThat(racer.finishClocks())
                .containsExactly(new RaceClock(6, STEP), new RaceClock(9, STEP));
        assertThat(racer.finishClocks()).extracting(RaceClock::elapsed)
                .containsExactly(Duration.ofMillis(300), Duration.ofMillis(450));

        // The driver stopped playing movement ticks on exactly the tick the run finished on, and the
        // clock it reported the map finished with is the run's own finish clock.
        assertThat(racer.events()).contains("mapFinished 0 ember-ascent @6", "mapFinished 1 glacier-chicane @9");
        assertThat(racer.raceTicks()).hasSize(6 + 9);
        assertThat(racer.raceTicks()).extracting(RaceClock::gameTick)
                .containsExactly(1, 2, 3, 4, 5, 6, 1, 2, 3, 4, 5, 6, 7, 8, 9);

        // Positive control: a phase that runs its full length is what the same courses produce when
        // nobody's finish is reported, so the sixes and nines above are the run's doing.
        Racer ignored = new Racer();
        run(driver(ignored, () -> false));
        assertThat(ignored.raceTicks()).hasSize(2 * GAME_TICKS);
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

        private final List<Duration> lobbyRemaining = new ArrayList<>();

        List<Duration> lobbyRemaining() {
            return List.copyOf(lobbyRemaining);
        }

        @Override
        public void lobbyTick(int mapIndex, String mapName, Duration remaining) {
            events.add("lobbyTick %s %s %s".formatted(mapIndex, mapName, remaining.toMillis()));
            lobbyRemaining.add(remaining);
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

    /**
     * One player flying a scripted lane, whose {@link RaceRun} is advanced from the clock the driver
     * hands to {@link #raceTick(RaceClock)} and from nothing else.
     */
    private static final class Racer extends RecordingListener {

        private final List<RaceClock> finishClocks = new ArrayList<>();
        private RaceRun run = RaceRun.atStart();
        private MapDefinition course = COURSES.getFirst();

        List<RaceClock> finishClocks() {
            return List.copyOf(finishClocks);
        }

        /** What the driver's {@code everyPlayerFinished} supplier reads, with one player racing. */
        boolean finished() {
            return run.finished();
        }

        @Override
        public void mapStarted(int mapIndex, String mapName) {
            super.mapStarted(mapIndex, mapName);
            course = COURSES.get(mapIndex);
            run = RaceRun.atStart();
        }

        @Override
        public void raceTick(RaceClock clock) {
            super.raceTick(clock);
            run = run.advance(course, clock, positionAt(clock.gameTick()), true);
        }

        @Override
        public void mapFinished(int mapIndex, String mapName, RaceClock clock) {
            super.mapFinished(mapIndex, mapName, clock);
            finishClocks.add(run.finishedAt().orElseThrow());
        }

        private static Vec3 positionAt(int gameTick) {
            return new Vec3(LANE_X, LANE_Y, SPAWN_Z + BLOCKS_PER_TICK * gameTick);
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
