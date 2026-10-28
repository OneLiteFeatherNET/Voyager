package net.elytrarace.voyager.platform.cup;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.GameMode;
import net.elytrarace.voyager.api.race.GuideLine;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;
import net.elytrarace.voyager.api.stats.CupCompletion;
import net.elytrarace.voyager.api.stats.RaceCompletion;
import net.elytrarace.voyager.api.stats.RaceRecorder;
import net.elytrarace.voyager.api.stats.RacerRun;
import net.elytrarace.voyager.platform.catalog.CatalogHolder;
import net.elytrarace.voyager.platform.catalog.CatalogSnapshot;
import net.elytrarace.voyager.platform.catalog.LoadedCatalog;
import net.elytrarace.voyager.platform.collision.MinestomCollisionSpace;
import net.elytrarace.voyager.platform.flight.FireworkBoostTracker;
import net.elytrarace.voyager.platform.flight.FlightTracker;
import net.elytrarace.voyager.platform.tick.FlightTickDriver;
import net.elytrarace.voyager.platform.world.CurrentMapBlocks;
import net.elytrarace.voyager.platform.world.MapInstances;
import net.elytrarace.voyager.race.flow.RaceClock;
import net.elytrarace.voyager.race.flow.RaceTimings;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.instance.block.Block;
import net.minestom.server.world.DimensionType;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import net.onelitefeather.falco.anvil.FalcoAnvilLoader;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The cup's hand-off to the race recorder, on a live server and ticked explicitly. A two-map cup is played with
 * one racer who never reaches a ring; each map is ended by a skip, so the recorder sees a map that did not finish
 * its rings and a cup that did finish its rotation. The recorder is a fake written here, so every call it receives
 * is visible to the assertions.
 *
 * <p>The clock is fixed. Every instant the recorder receives is therefore a value the test chose, not the time of
 * the run.
 */
@EnvTest
class CupSessionRecordingTest {

    private static final String FIRST_WORLD = "recording_first";
    private static final String SECOND_WORLD = "recording_second";
    private static final String FIRST_MAP = "rec-first";
    private static final String SECOND_MAP = "rec-second";
    private static final String CUP = "rec_tour";
    private static final Vec3 FIRST_SPAWN = new Vec3(3.5, 65.0, 5.5);
    private static final Vec3 SECOND_SPAWN = new Vec3(-37.5, 71.0, -88.5);
    private static final Duration STEP = Duration.ofMillis(50);
    private static final BoostConfig BOOST = new BoostConfig(4, 9);
    private static final Instant START = Instant.parse("2026-10-13T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(START, ZoneOffset.UTC);

    private static final MapDefinition FIRST_RUN = new MapDefinition(FIRST_MAP, FIRST_WORLD, FIRST_SPAWN,
            List.of(new Ring(0, FIRST_SPAWN.plus(new Vec3(0, 8, 10)), new Vec3(0, 0, 1), 6.0, 7, RingType.STANDARD)),
            Duration.ofMillis(400), BOOST, new GuideLine(List.of(), 2, 1.0));

    private static final MapDefinition SECOND_RUN = new MapDefinition(SECOND_MAP, SECOND_WORLD, SECOND_SPAWN,
            List.of(new Ring(0, SECOND_SPAWN.plus(new Vec3(0, 0, 10)), new Vec3(0, 0, 1), 6.0, 13, RingType.STANDARD)),
            Duration.ofMillis(650), BOOST, new GuideLine(List.of(), 4, 1.5));

    private static final CupDefinition TOUR = new CupDefinition(CUP, List.of(FIRST_MAP, SECOND_MAP), GameMode.RACE);

    /** The one computer racer of the bot tests. */
    private static final UUID BOT = UUID.fromString("00000000-0000-0000-0000-00000000c0de");

    /** Points around the first map's ring, which is centred eight blocks up and ten ahead of the spawn. */
    private static final Vec3 FRONT_OF_RING = FIRST_SPAWN.plus(new Vec3(0, 8, 9));
    private static final Vec3 BEHIND_RING = FIRST_SPAWN.plus(new Vec3(0, 8, 11));
    private static final Vec3 FAR_BEHIND_RING = FIRST_SPAWN.plus(new Vec3(0, 8, 13));
    /** Far under the world's floor, so a racer there is outside the bounds a reset is due for. */
    private static final Vec3 BELOW_THE_WORLD = FIRST_SPAWN.plus(new Vec3(0, -200, 0));

    /** Lobby zero for the first map, race ten seconds (200 ticks), results 100 ms: a map ends by its skip, never by time. */
    private static final RaceTimings TIMINGS = new RaceTimings(
            Duration.ZERO, Duration.ofSeconds(10), Duration.ofMillis(100), Duration.ofMillis(100));

    /** How many ticks a cup may take before the test gives up, so a hang fails instead of running on. */
    private static final int BUDGET = 2_000;

    @TempDir
    Path tempDir;

    /** Who is online now: the set the cup reads. */
    private final List<Player> online = new ArrayList<>();

    private final RecordingRaceRecorder recorder = new RecordingRaceRecorder();

    private MapInstances worlds;
    private CupSession session;

    @AfterEach
    void releaseTheWorlds() {
        online.forEach(racer -> racer.remove(true));
        online.clear();
        if (worlds != null) {
            worlds.close();
            worlds = null;
        }
    }

    // ------------------------------------------------------------------------------------------
    // Recording
    // ------------------------------------------------------------------------------------------

    @Test
    void aFinishedMapRecordsOneCompletionPerMap(Env env) throws IOException {
        startTwoMapCup(env, recorder);

        playUntilCupFinishes();

        assertThat(recorder.races).as("completions recorded, one per map").extracting(RaceCompletion::mapKey)
                .containsExactly("elytrarace:" + FIRST_MAP, "elytrarace:" + SECOND_MAP);
    }

    @Test
    void aMapCompletionIsTimedFromTheInjectedClock(Env env) throws IOException {
        startTwoMapCup(env, recorder);

        playUntilCupFinishes();

        assertThat(recorder.races.get(0).startedAt()).as("start of the first map").isEqualTo(START);
        assertThat(recorder.races.get(0).finishedAt()).as("end of the first map").isEqualTo(START);
    }

    @Test
    void aFinishedCupRecordsOneCupCompletion(Env env) throws IOException {
        startTwoMapCup(env, recorder);

        playUntilCupFinishes();

        assertThat(recorder.cups).as("cup completions recorded").hasSize(1);
        CupCompletion cup = recorder.cups.get(0);
        assertThat(cup.cupKey()).as("cup key").isEqualTo("elytrarace:" + CUP);
        assertThat(cup.racers()).as("racers in the cup completion").hasSize(1);
        assertThat(cup.racers().get(0).finalPosition()).as("final position of the only racer").isEqualTo(1);
    }

    @Test
    void theRecorderIsNeverCalledOnATickThatDoesNotFinishAMap(Env env) throws IOException {
        startTwoMapCup(env, recorder);

        for (int tick = 1; tick <= 10; tick++) {
            session.advancePhase();
            assertThat(recorder.races).as("completions after tick %s, while the map is still racing", tick).isEmpty();
        }
        assertThat(recorder.cups).as("cup completions while the map is still racing").isEmpty();
    }

    @Test
    void aThrowingRecorderDoesNotStopTheMap(Env env) throws IOException {
        ThrowingRaceRecorder failing = new ThrowingRaceRecorder();
        Player racer = startTwoMapCup(env, failing);

        playUntilCupFinishes();

        assertThat(session.cupFinished()).as("the cup finished although the recorder threw").isTrue();
        assertThat(failing.racesOffered).as("completions offered to the failing recorder").isEqualTo(2);
        assertThat(session.standings().of(racer.getUuid())).as("both maps were still scored").hasSize(2);
    }

    // ------------------------------------------------------------------------------------------
    // Counts stop at the finish
    // ------------------------------------------------------------------------------------------

    /**
     * Two gliding ticks come before the crossing of the ring, and the crossing tick itself is the finish, so the
     * count is two. Two more gliding ticks follow the finish while the second racer still races; they are not counted.
     */
    @Test
    void glidingAfterTheFinishIsNotCounted(Env env) throws IOException {
        Player racer = startTwoMapCup(env, recorder, 2);
        untilGameStarts();
        racer.setFlyingWithElytra(true);
        moveTo(racer, FRONT_OF_RING);
        session.advancePhase();
        moveTo(racer, FRONT_OF_RING);
        session.advancePhase();
        moveTo(racer, BEHIND_RING);
        session.advancePhase();
        moveTo(racer, FAR_BEHIND_RING);
        session.advancePhase();
        moveTo(racer, FAR_BEHIND_RING);
        session.advancePhase();
        playUntilCupFinishes();

        RacerRun run = runOf(recorder.races.get(0), racer);
        assertThat(run.finishPosition()).as("the racer finished the first map").isEqualTo(1);
        assertThat(run.flightMillis()).as("flight time: the two ticks before the finish").isEqualTo(2 * 50);
    }

    /**
     * A boost accepted after the finish is really accepted, so only the count can keep it out of the record. A
     * racer who is sent below the world after the finish is not reset, because the run is finished.
     */
    @Test
    void boostsAndResetsAfterTheFinishAreNotCounted(Env env) throws IOException {
        Player racer = startTwoMapCup(env, recorder, 2);
        untilGameStarts();
        racer.setFlyingWithElytra(true);
        moveTo(racer, FRONT_OF_RING);
        session.advancePhase();
        moveTo(racer, BEHIND_RING);
        session.advancePhase();

        assertThat(session.requestBoost(racer)).as("a boost after the finish is accepted").isTrue();
        moveTo(racer, BELOW_THE_WORLD);
        session.advancePhase();
        playUntilCupFinishes();

        RacerRun run = runOf(recorder.races.get(0), racer);
        assertThat(run.finishPosition()).as("the racer finished the first map").isEqualTo(1);
        assertThat(run.boosts()).as("boosts accepted after the finish").isZero();
        assertThat(run.resets()).as("resets after the finish").isZero();
    }

    /**
     * One accepted boost, one reset and two gliding ticks before the finish. The reset is an out-of-bounds send-back
     * on a tick the racer is not gliding, so it adds no flight time; the finishing tick is not counted either, as
     * {@link #glidingAfterTheFinishIsNotCounted} says.
     */
    @Test
    void theCompletionCarriesTheRacersBoostsResetsAndFlightMillis(Env env) throws IOException {
        Player racer = startTwoMapCup(env, recorder, 2);
        untilGameStarts();
        racer.setFlyingWithElytra(true);
        moveTo(racer, FRONT_OF_RING);
        session.advancePhase();
        moveTo(racer, FRONT_OF_RING);
        assertThat(session.requestBoost(racer)).as("the boost is accepted").isTrue();
        session.advancePhase();
        racer.setFlyingWithElytra(false);
        moveTo(racer, BELOW_THE_WORLD);
        session.advancePhase();
        moveTo(racer, FRONT_OF_RING);
        session.advancePhase();
        racer.setFlyingWithElytra(true);
        moveTo(racer, BEHIND_RING);
        session.advancePhase();
        playUntilCupFinishes();

        RacerRun run = runOf(recorder.races.get(0), racer);
        assertThat(run.finishPosition()).as("the racer finished the first map").isEqualTo(1);
        assertThat(run.boosts()).as("boosts in the completion").isEqualTo(1);
        assertThat(run.resets()).as("resets in the completion").isEqualTo(1);
        assertThat(run.flightMillis()).as("flight millis in the completion").isEqualTo(2 * 50);
    }

    // ------------------------------------------------------------------------------------------
    // Computer racers
    // ------------------------------------------------------------------------------------------

    /**
     * One human and one computer racer both finish the first map. The completion holds the human alone, and the
     * computer racer is counted in the bot count and nowhere else.
     */
    @Test
    void aComputerRacerIsNotRecorded(Env env) throws IOException {
        Player racer = startTwoMapCup(env, recorder, 1, new OneComputerRacer(BOT));
        untilGameStarts();
        racer.setFlyingWithElytra(true);
        moveTo(racer, FRONT_OF_RING);
        session.advancePhase();
        moveTo(racer, BEHIND_RING);
        session.advancePhase();
        playUntilCupFinishes();

        RaceCompletion completion = recorder.races.get(0);
        assertThat(completion.racers()).as("racers in the completion of the first map")
                .extracting(RacerRun::playerId).containsExactly(racer.getUuid());
        assertThat(completion.botCount()).as("computer racers counted as the bot count").isEqualTo(1);
    }

    // ------------------------------------------------------------------------------------------
    // Nothing to record
    // ------------------------------------------------------------------------------------------

    /** The racer leaves during the first map, so the map ends with no human racer and nothing is stored for it. */
    @Test
    void aMapWithNoHumanRacerRecordsNothing(Env env) throws IOException {
        Player racer = startTwoMapCup(env, recorder);
        untilGameStarts();
        leave(racer);

        playUntilCupFinishes();

        assertThat(recorder.races).as("completions of maps nobody raced").isEmpty();
    }

    /** The racer leaves during the first map, so the cup ends with no human racer and no cup result is stored. */
    @Test
    void aCupWithNoHumanRacerRecordsNoCupCompletion(Env env) throws IOException {
        Player racer = startTwoMapCup(env, recorder);
        untilGameStarts();
        leave(racer);

        playUntilCupFinishes();

        assertThat(recorder.cups).as("cup completions of a cup nobody raced").isEmpty();
    }

    /** Takes the racer out of the race and out of the world, as a disconnect would. */
    private void leave(Player racer) {
        online.remove(racer);
        racer.remove(true);
    }

    // ------------------------------------------------------------------------------------------
    // Fixture
    // ------------------------------------------------------------------------------------------

    /**
     * Writes both worlds, builds the cup with {@code recorder} and connects one racer to the first map's spawn.
     * The cup is started at once, with no lobby, so the first map's race begins on the first tick.
     */
    private Player startTwoMapCup(Env env, RaceRecorder raceRecorder) throws IOException {
        return startTwoMapCup(env, raceRecorder, 1);
    }

    /**
     * As {@link #startTwoMapCup(Env, RaceRecorder)}, with {@code racerCount} humans connected at the first spawn
     * before the cup starts. The first of them is returned; the others never move, so they hold a run that a map
     * can end without.
     */
    private Player startTwoMapCup(Env env, RaceRecorder raceRecorder, int racerCount) throws IOException {
        return startTwoMapCup(env, raceRecorder, racerCount, CompetitorField.none());
    }

    /** As {@link #startTwoMapCup(Env, RaceRecorder, int)}, with the computer racers {@code competitors} reports. */
    private Player startTwoMapCup(Env env, RaceRecorder raceRecorder, int racerCount, CompetitorField competitors)
            throws IOException {
        writeWorld(env, FIRST_WORLD, FIRST_SPAWN);
        writeWorld(env, SECOND_WORLD, SECOND_SPAWN);
        MapInstances instances = new MapInstances(env.process().instance(), tempDir.resolve("worlds"));
        this.worlds = instances;
        RaceRuns runs = new RaceRuns();
        MapTransition transition = new MapTransition(instances, runs);
        CatalogHolder holder = new CatalogHolder(new LoadedCatalog(
                new CatalogSnapshot(Map.of(FIRST_MAP, FIRST_RUN, SECOND_MAP, SECOND_RUN), Map.of(CUP, TOUR)),
                TOUR, Instant.EPOCH));
        CurrentMapBlocks blocks = new CurrentMapBlocks();
        FireworkBoostTracker boosts = new FireworkBoostTracker();
        FlightTickDriver flight = new FlightTickDriver(
                new LivePlayerSampler(() -> online, boosts), new FlightTracker(), new MinestomCollisionSpace(blocks));
        this.session = new CupSession(holder, instances, transition, runs, flight, blocks, boosts, TIMINGS, STEP,
                () -> online, raceRecorder, CLOCK, competitors);

        Instance first = instances.forWorld(FIRST_WORLD);
        first.loadChunk(Math.floorDiv((int) Math.floor(FIRST_SPAWN.x()), 16),
                Math.floorDiv((int) Math.floor(FIRST_SPAWN.z()), 16)).join();
        List<Player> joined = new ArrayList<>();
        for (int connected = 0; connected < racerCount; connected++) {
            Player racer = env.createConnection().connect(first,
                    new Pos(FIRST_SPAWN.x(), FIRST_SPAWN.y(), FIRST_SPAWN.z()));
            online.add(racer);
            joined.add(racer);
        }
        session.start(true);
        return joined.get(0);
    }

    /** Ticks until the first map's {@code GAME} phase has begun, so the ticks counted after this call are race ticks. */
    private void untilGameStarts() {
        for (int tick = 0; tick < BUDGET; tick++) {
            session.advancePhase();
            if (session.describe().contains("phase GAME")) {
                return;
            }
        }
        throw new AssertionError("the first map's GAME phase did not begin within %s ticks".formatted(BUDGET));
    }

    /** Moves the racer to {@code at}, as a movement packet would, and waits until the move has happened. */
    private static void moveTo(Player racer, Vec3 at) {
        racer.teleport(new Pos(at.x(), at.y(), at.z())).join();
    }

    /** The racer's entry in a completion, found by UUID so a fixture with two racers cannot pass by order. */
    private static RacerRun runOf(RaceCompletion completion, Player racer) {
        return completion.racers().stream()
                .filter(run -> run.playerId().equals(racer.getUuid()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no run of %s in the completion".formatted(racer.getUsername())));
    }

    /**
     * Ticks until the cup has finished, asking for the skip on every tick. A skip is only taken while a {@code GAME}
     * phase is running, so the request is a no-op everywhere else. The budget guards against a hang.
     */
    private void playUntilCupFinishes() {
        for (int tick = 0; tick < BUDGET; tick++) {
            session.advancePhase();
            session.requestSkip();
            if (session.cupFinished()) {
                return;
            }
        }
        throw new AssertionError("the cup did not finish within %s ticks".formatted(BUDGET));
    }

    /** Writes a world with one floor block under the spawn, through a Falco loader of its own. */
    private void writeWorld(Env env, String world, Vec3 spawn) throws IOException {
        InstanceManager manager = env.process().instance();
        FalcoAnvilLoader writer = FalcoAnvilLoader.builder()
                .build(tempDir.resolve("worlds").resolve(world), DimensionType.OVERWORLD.key());
        InstanceContainer instance = manager.createInstanceContainer(DimensionType.OVERWORLD, writer);
        int x = (int) Math.floor(spawn.x());
        int y = (int) Math.floor(spawn.y()) - 1;
        int z = (int) Math.floor(spawn.z());
        try {
            instance.loadChunk(x >> 4, z >> 4).join();
            instance.setBlock(x, y, z, Block.GOLD_BLOCK);
            instance.saveChunksToStorage().join();
        } finally {
            manager.unregisterInstance(instance);
            writer.close();
        }
    }

    /**
     * One computer racer that walks the first map's ring: one point per race tick, the last point held after it.
     * It is seated on every map, so the fixture is the same whichever map it races.
     */
    private static final class OneComputerRacer implements CompetitorField {

        private final UUID id;
        private final List<Vec3> path = List.of(FRONT_OF_RING, BEHIND_RING);
        private int tick;

        OneComputerRacer(UUID id) {
            this.id = id;
        }

        @Override
        public List<UUID> prepare(int mapIndex, MapDefinition map) {
            return List.of(id);
        }

        @Override
        public List<Competitor> advance(RaceClock clock) {
            Vec3 at = path.get(Math.min(tick, path.size() - 1));
            tick++;
            return List.of(new Competitor(id, at, true));
        }

        @Override
        public void dismiss() {
            // Nothing to let go: the fake holds no state beyond its path.
        }
    }

    /** A recorder that keeps everything it is offered, in order. */
    private static final class RecordingRaceRecorder implements RaceRecorder {

        private final List<RaceCompletion> races = new ArrayList<>();
        private final List<CupCompletion> cups = new ArrayList<>();

        @Override
        public void recordRace(RaceCompletion completion) {
            races.add(completion);
        }

        @Override
        public void recordCup(CupCompletion completion) {
            cups.add(completion);
        }
    }

    /** A recorder whose storage is down: every offer fails, and the failures are counted. */
    private static final class ThrowingRaceRecorder implements RaceRecorder {

        private int racesOffered;

        @Override
        public void recordRace(RaceCompletion completion) {
            racesOffered++;
            throw new IllegalStateException("statistics storage is down");
        }

        @Override
        public void recordCup(CupCompletion completion) {
            throw new IllegalStateException("statistics storage is down");
        }
    }
}
