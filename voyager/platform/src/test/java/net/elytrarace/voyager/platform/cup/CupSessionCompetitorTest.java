package net.elytrarace.voyager.platform.cup;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.GameMode;
import net.elytrarace.voyager.api.race.GuideLine;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;
import net.elytrarace.voyager.platform.catalog.CatalogHolder;
import net.elytrarace.voyager.platform.catalog.CatalogSnapshot;
import net.elytrarace.voyager.platform.catalog.LoadedCatalog;
import net.elytrarace.voyager.platform.collision.MinestomCollisionSpace;
import net.elytrarace.voyager.platform.flight.FireworkBoostTracker;
import net.elytrarace.voyager.platform.flight.FlightTracker;
import net.elytrarace.voyager.platform.stats.NoOpRaceRecorder;
import net.elytrarace.voyager.platform.tick.FlightTickDriver;
import net.elytrarace.voyager.platform.world.CurrentMapBlocks;
import net.elytrarace.voyager.platform.world.MapInstances;
import net.elytrarace.voyager.race.flow.RaceClock;
import net.elytrarace.voyager.race.flow.RaceTimings;
import net.elytrarace.voyager.race.run.RaceRun;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.instance.block.Block;
import net.minestom.server.network.packet.server.play.SystemChatPacket;
import net.minestom.server.world.DimensionType;
import net.minestom.testing.Collector;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import net.minestom.testing.TestConnection;
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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The cup's seam for computer racers, driven by explicit ticks against a fake {@link CompetitorField}.
 *
 * <p>The cup asks the field which racers take part on each map, starts a run for each of them, advances those
 * runs with the positions the field reports, and tells the field to dismiss them when the cup ends or is aborted.
 * The human supplier is empty throughout: a competitor's run must come from the field alone.
 *
 * <p>Every test builds its own server, its own world in a {@link TempDir} and its own cup session. Ticks are
 * called directly, nothing sleeps, and the clock is a fixed {@link RaceClock} rather than the wall clock.
 */
@EnvTest
class CupSessionCompetitorTest {

    private static final String WORLD = "ridge_arena";
    private static final Vec3 SPAWN = new Vec3(3.5, 65.0, 5.5);
    private static final Block FLOOR = Block.GOLD_BLOCK;
    private static final Duration STEP = Duration.ofMillis(50);

    private static final UUID RIVAL = UUID.fromString("5b0f1c52-2d1a-4c1e-9a55-0c0ffee00001");
    private static final UUID STRANGER = UUID.fromString("5b0f1c52-2d1a-4c1e-9a55-0c0ffee00002");

    /** Two rings, so a computer racer can pass one and still be on its way to the next. */
    private static final MapDefinition MAP = new MapDefinition("ridge-run", WORLD, SPAWN,
            List.of(new Ring(0, SPAWN.plus(new Vec3(0, 8, 10)), new Vec3(0, 0, 1), 6.0, 7, RingType.STANDARD),
                    new Ring(1, SPAWN.plus(new Vec3(0, 8, 20)), new Vec3(0, 0, 1), 6.0, 7, RingType.STANDARD)),
            Duration.ofMillis(400), new BoostConfig(4, 9), new GuideLine(List.of(), 2, 1.0));

    /** Just past the plane of each ring, on the line of its centre: a racer there has passed the ring. */
    private static final Vec3 PAST_RING_ZERO = SPAWN.plus(new Vec3(0, 8, 11));
    private static final Vec3 PAST_RING_ONE = SPAWN.plus(new Vec3(0, 8, 21));

    /** Five race ticks: a computer racer needs three to pass both rings, and the human has the rest to run out. */
    private static final RaceTimings FIVE_TICK_RACE = new RaceTimings(
            Duration.ZERO, STEP.multipliedBy(5), Duration.ZERO, Duration.ZERO);

    private static final CupDefinition CUP = new CupDefinition("grand_tour", List.of(MAP.name()), GameMode.RACE);

    /** No lobby and a one-tick race, so a cup ends within a handful of explicit ticks. */
    /** Two seconds of lobby, then five race ticks: a human placed during the lobby has time to be placed before GO. */
    private static final RaceTimings PLACED_LOBBY_RACE = new RaceTimings(
            STEP.multipliedBy(40), STEP.multipliedBy(5), Duration.ZERO, Duration.ZERO);

    private static final RaceTimings ONE_TICK_RACE = new RaceTimings(
            Duration.ZERO, STEP, Duration.ZERO, Duration.ZERO);

    private static final int TICK_LIMIT = 200;

    @TempDir
    Path tempDir;

    private MapInstances worlds;

    /** The humans a test puts in a world, removed before the worlds are closed. */
    private final List<Player> humans = new ArrayList<>();

    @AfterEach
    void releaseTheWorlds() {
        // The players go first: the instance manager refuses to unregister a world that still holds one.
        humans.forEach(human -> human.remove(true));
        humans.clear();
        if (worlds != null) {
            worlds.close();
            worlds = null;
        }
    }

    @Test
    void aCompetitorsRunStartsAtItsMapsStart(Env env) throws IOException {
        RecordingField field = new RecordingField(List.of(RIVAL), List.of());
        RaceRuns runs = new RaceRuns();
        CupSession session = openSession(env, runs, field, ONE_TICK_RACE);

        session.mapStarted(0, MAP.name());

        assertThat(field.prepareCalls)
                .as("the field is asked once, for the map that starts")
                .containsExactly(0);
        assertThat(runs.of(RIVAL))
                .as("the competitor holds a fresh run at the start of the map")
                .contains(RaceRun.atStart());
    }

    @Test
    void aCompetitorsPositionFeedsItsRunAndNotTheHumanSupplier(Env env) throws IOException {
        Vec3 flown = SPAWN.plus(new Vec3(0, 0, 5));
        RecordingField field = new RecordingField(List.of(RIVAL), List.of(new Competitor(RIVAL, flown, true)));
        RaceRuns runs = new RaceRuns();
        CupSession session = openSession(env, runs, field, ONE_TICK_RACE);
        RaceClock clock = new RaceClock(1, STEP);

        session.mapStarted(0, MAP.name());
        session.raceTick(clock);

        RaceRun expected = RaceRun.atStart().advance(MAP, clock, flown, true, false);
        assertThat(expected)
                .as("the reported position must move the run, or the assertion below proves nothing")
                .isNotEqualTo(RaceRun.atStart());
        assertThat(runs.of(RIVAL))
                .as("the run is advanced with the position the competitor reported")
                .contains(expected);
    }

    @Test
    void aCompetitorThatIsNotOnTheCourseIsNotAdvanced(Env env) throws IOException {
        RecordingField field = new RecordingField(List.of(RIVAL), List.of(new Competitor(STRANGER, SPAWN, true)));
        RaceRuns runs = new RaceRuns();
        CupSession session = openSession(env, runs, field, ONE_TICK_RACE);

        session.mapStarted(0, MAP.name());
        session.raceTick(new RaceClock(1, STEP));

        assertThat(runs.of(STRANGER))
                .as("a competitor the field did not prepare for this map holds no run and gets none")
                .isEmpty();
        assertThat(runs.of(RIVAL))
                .as("a prepared competitor the field did not report on this tick is left where it was")
                .contains(RaceRun.atStart());
    }

    @Test
    void dismissIsCalledWhenTheCupEnds(Env env) throws IOException {
        RecordingField field = new RecordingField(List.of(RIVAL), List.of());
        CupSession session = openSession(env, new RaceRuns(), field, ONE_TICK_RACE);

        session.start(true);
        for (int tick = 0; tick < TICK_LIMIT && !session.cupFinished(); tick++) {
            session.advancePhase();
        }

        assertThat(session.cupFinished())
                .as("the one-map cup must reach its terminal state within %s ticks", TICK_LIMIT)
                .isTrue();
        assertThat(field.dismissals)
                .as("the field is dismissed exactly once, when the cup ends")
                .isEqualTo(1);
    }

    @Test
    void dismissIsCalledOnAbort(Env env) throws IOException {
        RecordingField field = new RecordingField(List.of(RIVAL), List.of());
        CupSession session = openSession(env, new RaceRuns(), field, ONE_TICK_RACE);

        session.start(true);
        session.abort();

        assertThat(field.dismissals)
                .as("the field is dismissed exactly once, when the cup is aborted")
                .isEqualTo(1);
    }

    @Test
    void aFinishedComputerRacerIsListedInTheResultsAsComWithItsTimeAndNoPoints(Env env) throws IOException {
        ScriptedField field = new ScriptedField(RIVAL, List.of(SPAWN, PAST_RING_ZERO, PAST_RING_ONE));
        TestConnection connection = env.createConnection();
        Player human = connection.connect(env.createFlatInstance(), new Pos(0, 41, 0));
        humans.add(human);
        // The human is placed on the map during the lobby, so the lobby is long enough for the placement to finish.
        CupSession session = openSession(env, new RaceRuns(), field, PLACED_LOBBY_RACE, List.of(human));
        Collector<SystemChatPacket> chat = connection.trackIncoming(SystemChatPacket.class);

        session.start(true);
        runToTheEnd(session);

        List<TranslatableComponent> rows = chatWithKey(chat, "voyager.result.cup.row");
        assertThat(rows)
                .as("the final standings list the computer racer and the human")
                .hasSize(2);
        TranslatableComponent com = rows.get(1);
        assertThat(describe(com.arguments().get(0).asComponent()))
                .as("the computer racer is placed after the human, whose points for the map come first")
                .isEqualTo("2");
        assertThat(describe(com.arguments().get(1).asComponent()))
                .as("the computer racer is named COM 1, the number of its seat")
                .isEqualTo("voyager.com.racer.name(1)");
        assertThat(describe(com.arguments().get(2).asComponent()))
                .as("a computer racer takes no points, so no placement bonus either")
                .isEqualTo("0");
        assertThat(describe(com.arguments().get(5).asComponent()))
                .as("the computer racer's own finish time is shown, not the no-time placeholder")
                .doesNotStartWith("voyager.result.cup.notime");
        assertThat(session.standings().order())
                .as("the computer racer is never recorded in the cup round")
                .extracting(standing -> standing.playerId())
                .doesNotContain(RIVAL);
    }

    @Test
    void aComputerRacerFinishSendsOneChatLineAndNoLineForEachRing(Env env) throws IOException {
        ScriptedField field = new ScriptedField(RIVAL, List.of(SPAWN, PAST_RING_ZERO, PAST_RING_ONE));
        TestConnection connection = env.createConnection();
        Player human = connection.connect(env.createFlatInstance(), new Pos(0, 41, 0));
        humans.add(human);
        CupSession session = openSession(env, new RaceRuns(), field, FIVE_TICK_RACE, List.of(human));
        Collector<SystemChatPacket> chat = connection.trackIncoming(SystemChatPacket.class);

        session.start(true);
        runToTheEnd(session);

        List<TranslatableComponent> finishes = chatWithKey(chat, "voyager.com.racer.finished");
        assertThat(finishes)
                .as("one line for the computer racer's finish, and none for the two rings it passed on the way")
                .hasSize(1);
        assertThat(describe(finishes.get(0).arguments().get(0).asComponent()))
                .as("the line names the computer racer by its seat")
                .isEqualTo("voyager.com.racer.name(1)");
    }

    /** Ticks the cup from its first phase until it reports the cup as finished, and asserts that it does. */
    private void runToTheEnd(CupSession session) {
        for (int tick = 0; tick < TICK_LIMIT && !session.cupFinished(); tick++) {
            session.advancePhase();
        }
        assertThat(session.cupFinished())
                .as("the one-map cup must reach its terminal state within %s ticks", TICK_LIMIT)
                .isTrue();
    }

    /** The chat lines of the given translation key that {@code chat} collected, in the order they were sent. */
    private static List<TranslatableComponent> chatWithKey(Collector<SystemChatPacket> chat, String key) {
        return chat.collect().stream()
                .map(SystemChatPacket::message)
                .filter(TranslatableComponent.class::isInstance)
                .map(TranslatableComponent.class::cast)
                .filter(line -> line.key().equals(key))
                .toList();
    }

    /** A component as a short string: its text, or its key with its arguments in brackets. */
    private static String describe(Component component) {
        if (component instanceof TextComponent text) {
            return text.content();
        }
        if (component instanceof TranslatableComponent translatable) {
            return translatable.key() + translatable.arguments().stream()
                    .map(argument -> describe(argument.asComponent()))
                    .collect(Collectors.joining(", ", "(", ")"));
        }
        return component.toString();
    }

    private CupSession openSession(Env env, RaceRuns runs, CompetitorField field, RaceTimings timings)
            throws IOException {
        return openSession(env, runs, field, timings, List.of());
    }

    private CupSession openSession(Env env, RaceRuns runs, CompetitorField field, RaceTimings timings,
            List<Player> humans) throws IOException {
        writeWorld(env);
        worlds = new MapInstances(env.process().instance(), tempDir.resolve("worlds"));
        LoadedCatalog catalog = new LoadedCatalog(
                new CatalogSnapshot(Map.of(MAP.name(), MAP), Map.of(CUP.name(), CUP)), CUP, Instant.EPOCH);
        CurrentMapBlocks blocks = new CurrentMapBlocks();
        FireworkBoostTracker boosts = new FireworkBoostTracker();
        FlightTickDriver flight = new FlightTickDriver(
                new LivePlayerSampler(nobody(), boosts), new FlightTracker(), new MinestomCollisionSpace(blocks));
        return new CupSession(new CatalogHolder(catalog), worlds, new MapTransition(worlds, runs), runs, flight,
                blocks, boosts, timings, STEP, () -> humans, new NoOpRaceRecorder(),
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), field);
    }

    private static Supplier<Collection<Player>> nobody() {
        return () -> List.of();
    }

    private void writeWorld(Env env) throws IOException {
        InstanceManager instanceManager = env.process().instance();
        FalcoAnvilLoader writer = FalcoAnvilLoader.builder()
                .build(tempDir.resolve("worlds").resolve(WORLD), DimensionType.OVERWORLD.key());
        InstanceContainer instance = instanceManager.createInstanceContainer(DimensionType.OVERWORLD, writer);
        int x = (int) Math.floor(SPAWN.x());
        int y = (int) Math.floor(SPAWN.y()) - 1;
        int z = (int) Math.floor(SPAWN.z());
        try {
            instance.loadChunk(x >> 4, z >> 4).join();
            instance.setBlock(x, y, z, FLOOR);
            instance.saveChunksToStorage().join();
        } finally {
            instanceManager.unregisterInstance(instance);
            writer.close();
        }
    }

    /**
     * A competitor field that reports one competitor at the positions it was built with, one per race tick, and
     * repeats the last of them once they are used up.
     */
    private static final class ScriptedField implements CompetitorField {

        private final UUID id;
        private final List<Vec3> positions;
        private int advances;

        ScriptedField(UUID id, List<Vec3> positions) {
            this.id = id;
            this.positions = positions;
        }

        @Override
        public List<UUID> prepare(int mapIndex, MapDefinition map) {
            return List.of(id);
        }

        @Override
        public List<Competitor> advance(RaceClock clock) {
            Vec3 at = positions.get(Math.min(advances, positions.size() - 1));
            advances++;
            return List.of(new Competitor(id, at, true));
        }

        @Override
        public void dismiss() {
            // Nothing to let go: the scripted competitor has no body.
        }
    }

    /** A competitor field that answers what it was built with and counts how it was called. */
    private static final class RecordingField implements CompetitorField {

        private final List<UUID> prepared;
        private final List<Competitor> reported;
        private final List<Integer> prepareCalls = new ArrayList<>();
        private int dismissals;

        RecordingField(List<UUID> prepared, List<Competitor> reported) {
            this.prepared = prepared;
            this.reported = reported;
        }

        @Override
        public List<UUID> prepare(int mapIndex, MapDefinition map) {
            prepareCalls.add(mapIndex);
            return prepared;
        }

        @Override
        public List<Competitor> advance(RaceClock clock) {
            return reported;
        }

        @Override
        public void dismiss() {
            dismissals++;
        }
    }
}
