package net.elytrarace.voyager.server;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.GameMode;
import net.elytrarace.voyager.api.race.GuideLine;
import net.elytrarace.voyager.api.race.LobbyDefinition;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;
import net.elytrarace.voyager.api.race.com.ComDifficulty;
import net.elytrarace.voyager.platform.catalog.CatalogHolder;
import net.elytrarace.voyager.platform.catalog.CatalogSnapshot;
import net.elytrarace.voyager.platform.catalog.LoadedCatalog;
import net.elytrarace.voyager.platform.cup.TickPipeline;
import net.elytrarace.voyager.platform.com.ComField;
import net.elytrarace.voyager.platform.com.ComNames;
import net.elytrarace.voyager.platform.collision.MinestomCollisionSpace;
import net.elytrarace.voyager.platform.flight.FireworkBoostTracker;
import net.elytrarace.voyager.platform.cup.LivePlayerSampler;
import net.elytrarace.voyager.platform.tick.FlightTickDriver;
import net.elytrarace.voyager.platform.world.CurrentMapBlocks;
import net.elytrarace.voyager.platform.flight.FlightTracker;
import net.elytrarace.voyager.platform.world.MapInstances;
import net.elytrarace.voyager.platform.cup.MapTransition;
import net.elytrarace.voyager.platform.cup.RaceRuns;
import net.elytrarace.voyager.platform.stats.NoOpRaceRecorder;
import net.elytrarace.voyager.server.inject.CupBeans;
import net.elytrarace.voyager.race.flow.RaceTimings;
import net.elytrarace.voyager.platform.cup.CompetitorField;
import net.elytrarace.voyager.platform.cup.CupSession;
import net.elytrarace.voyager.platform.cup.NoCompetitors;
import net.elytrarace.voyager.platform.flight.Racers;
import net.elytrarace.voyager.platform.handoff.HubHandoff;
import net.elytrarace.voyager.platform.lobby.LobbyHud;
import net.elytrarace.voyager.platform.lobby.LobbySidebar;
import net.elytrarace.voyager.platform.lobby.LobbyTransition;
import net.elytrarace.voyager.platform.lobby.WaitingRoom;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.minestom.server.coordinate.ChunkRange;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.instance.block.Block;
import net.minestom.server.network.player.GameProfile;
import net.minestom.server.world.DimensionType;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import net.minestom.testing.TestConnection;
import net.onelitefeather.falco.anvil.FalcoAnvilLoader;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * The Golden Master of a running cup: four scenarios whose whole observable behaviour, tick by tick, is pinned
 * to a committed text file.
 *
 * <p>The golden files were generated from the code of {@code main}, and they are not regenerated for a refactor. A move
 * that changes a line of any transcript is a behaviour change, and the test fails naming the first differing step and
 * line. The one exception is {@code lobby-world-start}, which the hub handoff changed on purpose (ADR-0033): a finished
 * cup no longer returns its racers to the lobby, so its transcript now ends on the tick the racer is handed to the hub.
 * The test itself never writes a golden file.
 *
 * <p>Every scenario has its own server ({@link EnvTest}), its own worlds in a {@link TempDir}, fixed player
 * names and identities, and a fixed catalogue load instant. Time advances only by explicit ticks, and nothing
 * sleeps or reads the wall clock, so the transcript is the same in any order and at any time of day.
 *
 * <p>The scenarios are: the lobby and the launch of the first map; a race with a boost, a ring crossed by a
 * second racer and that racer's disconnect; a cup played to its end with both maps skipped; and a restart that
 * takes a catalogue offered during the round.
 */
@EnvTest
class CupSessionGoldenMasterTest {

    private static final String RIDGE = "ridge_arena";
    private static final String DUNE = "dune_arena";

    private static final Vec3 RIDGE_SPAWN = new Vec3(3.5, 65.0, 5.5);
    private static final Vec3 DUNE_SPAWN = new Vec3(-37.5, 71.0, -88.5);

    private static final Block RIDGE_FLOOR = Block.GOLD_BLOCK;
    private static final Block DUNE_FLOOR = Block.DIAMOND_BLOCK;

    private static final String LOBBY = "lobby_hall";
    private static final Vec3 LOBBY_SPAWN = new Vec3(20.5, 65.0, -12.5);
    private static final Block LOBBY_FLOOR = Block.STONE;

    private static final BoostConfig RIDGE_BOOST = new BoostConfig(4, 9);
    private static final BoostConfig DUNE_BOOST = new BoostConfig(6, 13);

    private static final MapDefinition RIDGE_RUN = new MapDefinition("ridge-run", RIDGE, RIDGE_SPAWN,
            List.of(ring(0, RIDGE_SPAWN.plus(new Vec3(0, 8, 10)), 7)),
            Duration.ofMillis(400), RIDGE_BOOST, new GuideLine(List.of(), 2, 1.0));

    private static final MapDefinition DUNE_RUN = new MapDefinition("dune-run", DUNE, DUNE_SPAWN,
            List.of(ring(0, DUNE_SPAWN.plus(new Vec3(0, 0, 10)), 13),
                    ring(1, DUNE_SPAWN.plus(new Vec3(0, 0, 20)), 13)),
            Duration.ofMillis(650), DUNE_BOOST, new GuideLine(List.of(), 4, 1.5));

    private static final CupDefinition CUP =
            new CupDefinition("grand_tour", List.of("ridge-run", "dune-run"), GameMode.RACE);

    private static final CupDefinition RESTARTED_CUP =
            new CupDefinition("grand_tour_two", List.of("ridge-run", "dune-run"), GameMode.RACE);

    /** A ridge course whose first ring is a CHECKPOINT, which a reset targets, and whose second ring is never reached. */
    private static final MapDefinition CHECKPOINT_RUN = new MapDefinition("checkpoint-run", RIDGE, RIDGE_SPAWN,
            List.of(new Ring(0, RIDGE_SPAWN.plus(new Vec3(0, 8, 10)), new Vec3(0, 0, 1), 6.0, 7, RingType.CHECKPOINT),
                    ring(1, RIDGE_SPAWN.plus(new Vec3(0, 8, 30)), 9)),
            Duration.ofMillis(400), RIDGE_BOOST, new GuideLine(List.of(), 2, 1.0));

    private static final CupDefinition CHECKPOINT_CUP =
            new CupDefinition("checkpoint_cup", List.of("checkpoint-run"), GameMode.RACE);

    /**
     * Lobby 0.1 s, so the transcript starts close to the race; a race of ten seconds, so the reset can come after the
     * airborne grace of twenty race ticks.
     */
    private static final RaceTimings CHECKPOINT_TIMINGS = new RaceTimings(
            Duration.ofMillis(100), Duration.ofSeconds(10), Duration.ofMillis(200), Duration.ofMillis(200));

    /** Lobby 3.5 s, so the countdown runs; race 1 s; results 0.2 s each. */
    private static final RaceTimings TIMINGS = new RaceTimings(
            Duration.ofMillis(3500), Duration.ofSeconds(1), Duration.ofMillis(200), Duration.ofMillis(200));

    private static final Duration STEP = Duration.ofMillis(50);

    private static final String ADA = "Ada";

    /** The map and the GAME phase of a cup, as {@code CupSession.describe()} names them. */
    private static final Pattern GAME_PHASE = Pattern.compile("map \\d+/\\d+ '[^']*', phase GAME");
    private static final String BEN = "Ben";

    /** Every scenario ends well before this; it guards against a hang, and is not a length anything asserts. */
    private static final int BUDGET = 400;

    /**
     * The ridge course of the computer scenario. It has two rings, because a line through one ring and no guide point
     * is a single point, and a computer racer needs a direction.
     */
    private static final MapDefinition COM_RIDGE_RUN = new MapDefinition("com-ridge", RIDGE, RIDGE_SPAWN,
            List.of(ring(0, RIDGE_SPAWN.plus(new Vec3(0, 8, 10)), 7),
                    ring(1, RIDGE_SPAWN.plus(new Vec3(0, 8, 20)), 9)),
            Duration.ofMillis(400), RIDGE_BOOST, new GuideLine(List.of(), 2, 1.0));

    private static final CupDefinition COM_CUP =
            new CupDefinition("com_tour", List.of("com-ridge", "dune-run"), GameMode.RACE);

    /**
     * The clock reading at which the computer racers' round starts, fixed so that the field plays the same race on every
     * run; its milliseconds are the round seed.
     */
    private static final long COM_ROUND_MILLIS = 20261014L;

    /** The grid of the computer scenario: one human and three computer racers, the grid size counting the human. */
    private static final int COM_GRID = 4;

    @TempDir
    Path tempDir;

    private Harness harness;

    @AfterEach
    void releaseTheWorlds() {
        if (harness != null) {
            harness.close();
            harness = null;
        }
    }

    @Test
    void lobbyAndStart(Env env) throws IOException {
        harness = Harness.open(env, tempDir);
        harness.connect(ADA, RIDGE_RUN);
        harness.act("start", () -> harness.session.start(false));
        for (int step = 0; step < 100; step++) {
            harness.tick(() -> { });
        }

        assertGolden("lobby-and-start", harness.transcript);
    }

    @Test
    void racingBoostDisconnect(Env env) throws IOException {
        harness = Harness.open(env, tempDir);
        harness.connect(ADA, RIDGE_RUN);
        harness.connect(BEN, RIDGE_RUN);
        harness.act("start", () -> harness.session.start(false));

        int[] raceTick = {0};
        harness.runUntil(() -> harness.session.cupFinished(), () -> {
            if (harness.session.describe().contains("phase GAME")) {
                raceTick[0]++;
            }
            switch (raceTick[0]) {
                case 3 -> harness.session.requestBoost(harness.racer(ADA));
                case 5 -> harness.racer(BEN).teleport(new Pos(RIDGE_SPAWN.x(), RIDGE_SPAWN.y() + 8,
                        RIDGE_SPAWN.z() + 20)).join();
                case 12 -> harness.disconnect(BEN);
                default -> { }
            }
        });

        assertGolden("racing-boost-disconnect", harness.transcript);
    }

    @Test
    void skipFinish(Env env) throws IOException {
        harness = Harness.open(env, tempDir);
        harness.connect(ADA, RIDGE_RUN);
        harness.act("start", () -> harness.session.start(false));

        Set<String> skipped = new HashSet<>();
        harness.runUntil(() -> harness.session.cupFinished(), () -> {
            String described = harness.session.describe();
            if (!described.contains("phase GAME")) {
                return;
            }
            for (String map : List.of("ridge-run", "dune-run")) {
                if (described.contains(map) && skipped.add(map)) {
                    harness.session.requestSkip();
                }
            }
        });

        assertGolden("skip-finish", harness.transcript);
    }

    /**
     * The lobby world as the server runs it: a racer waits in the lobby, the room starts the cup, the racer moves from
     * the lobby to the first map at the launch, and the cup's results linger before the room hands the racer to the hub
     * (ADR-0033). Characterises the lobby transfers and the hand-off; the four scenarios above run without a room and are
     * unchanged.
     */
    @Test
    void lobbyWorldStart(Env env) throws IOException {
        writeWorld(env, tempDir, LOBBY, LOBBY_SPAWN, LOBBY_FLOOR);
        harness = Harness.open(env, tempDir);
        harness.useLobby(new LobbyDefinition(LOBBY, LOBBY_SPAWN));
        harness.connectToLobby(ADA);

        // Stops on the tick the racer is handed to the hub: the cup's end, then the linger of the results. The racer is
        // never moved back to the lobby, so the transcript shows the cup's end and the hand-off, and nothing after it.
        UUID ada = harness.racer(ADA).getUuid();
        harness.runUntil(() -> harness.handedToHub.contains(ada), () -> { });

        assertGolden("lobby-world-start", harness.transcript);
    }

    @Test
    void restartPendingReload(Env env) throws IOException {
        harness = Harness.open(env, tempDir);
        harness.connect(ADA, RIDGE_RUN);
        harness.act("start", () -> harness.session.start(false));
        for (int step = 0; step < 30; step++) {
            harness.tick(() -> { });
        }
        harness.act("offer a reload", () -> harness.holder.offer(Harness.restartedCatalog()));
        for (int step = 0; step < 10; step++) {
            harness.tick(() -> { });
        }
        harness.act("restart", () -> harness.session.start(true));
        harness.runUntil(() -> harness.session.cupFinished(), () -> { });

        assertGolden("restart-pending-reload", harness.transcript);
    }

    /**
     * A landing after a checkpoint is a reset back to the checkpoint, and it is the only reset in the transcript.
     *
     * <p>The racer crosses the CHECKPOINT ring on the third race tick and is airborne for the twenty race ticks after it,
     * so the touchdown on the next tick counts. The reset places them behind and above the checkpoint, gliding, with the
     * landing title and the checkpoint subtitle. The velocity sent is zero, which the transcript does not show; the
     * velocity is pinned by {@code RunResetterTest}. The transcript ends a few race ticks after the reset, while the map
     * is still in its GAME phase.
     *
     * <p>The race-tick counter in the lambda reads the ticks already played, so {@code case n} acts on the tick after the
     * n-th. The cases are kept as they are because the golden file records the sequence they produce.
     */
    @Test
    void checkpointReset(Env env) throws IOException {
        harness = Harness.open(env, tempDir, Harness.checkpointCatalog(), CHECKPOINT_TIMINGS);
        harness.connect(ADA, CHECKPOINT_RUN);
        harness.act("start", () -> harness.session.start(false));

        int[] raceTick = {0};
        harness.runUntil(() -> raceTick[0] >= 25, () -> {
            if (harness.session.describe().contains("phase GAME")) {
                raceTick[0]++;
            }
            Player racer = harness.racer(ADA);
            switch (raceTick[0]) {
                case 2 -> {
                    racer.refreshOnGround(false);
                    racer.teleport(new Pos(RIDGE_SPAWN.x(), RIDGE_SPAWN.y() + 8, RIDGE_SPAWN.z() + 20)).join();
                }
                case 22 -> racer.refreshOnGround(true);
                default -> racer.refreshOnGround(false);
            }
        });

        assertGolden("checkpoint-reset", harness.transcript);
    }

    /**
     * A cup on a grid of {@link #COM_GRID} with one human: three computer racers at MEDIUM, on a fixed round seed.
     * The human races the ridge course, and the transcript records the computer racers on every tick, until the cup ends.
     */
    @Test
    void comField(Env env) throws IOException {
        harness = Harness.open(env, tempDir, Harness.comCatalog(), TIMINGS, COM_GRID);
        harness.connect(ADA, COM_RIDGE_RUN);
        harness.act("start", () -> harness.session.start(false));
        harness.runUntil(() -> harness.session.cupFinished(), () -> { });

        assertGolden("com-field", harness.transcript);
    }

    // ------------------------------------------------------------------------------------------
    // Comparison
    // ------------------------------------------------------------------------------------------

    /**
     * Compares the transcript with the committed golden file. On a difference the received transcript is written
     * to the test's temporary directory and the failure names the first differing step and line.
     */
    private void assertGolden(String scenario, CupTranscript transcript) throws IOException {
        String received = transcript.text();
        String expected = readGolden(scenario);
        if (received.equals(expected)) {
            return;
        }
        Path written = tempDir.resolve(scenario + ".received.txt");
        Files.writeString(Path.of("/tmp/claude-1000/-mnt-projects-oss-onelitefeather-Voyager/1ea74273-57cf-4a93-943d-208e59f8d7ec/scratchpad/" + scenario + ".DIAG2.txt"), received, StandardCharsets.UTF_8);
        Files.writeString(written, received, StandardCharsets.UTF_8);        List<String> want = List.of(expected.split("\n"));
        List<String> got = List.of(received.split("\n"));
        int line = 0;
        while (line < want.size() && line < got.size() && want.get(line).equals(got.get(line))) {
            line++;
        }
        String step = stepAt(got, line);
        fail("scenario '%s' differs from its golden file at %s, line %d%n  expected: %s%n  received: %s%n"
                + "the received transcript is at %s",
                scenario, step, line + 1,
                line < want.size() ? want.get(line) : "(end of golden file)",
                line < got.size() ? got.get(line) : "(end of transcript)",
                written);
    }

    private static String stepAt(List<String> lines, int index) {
        for (int line = Math.min(index, lines.size() - 1); line >= 0; line--) {
            if (lines.get(line).startsWith("## ")) {
                return lines.get(line).substring(3);
            }
        }
        return "the start";
    }

    private static String readGolden(String scenario) throws IOException {
        String resource = "/golden/cup-session/" + scenario + ".txt";
        try (InputStream in = CupSessionGoldenMasterTest.class.getResourceAsStream(resource)) {
            if (in == null) {
                fail("the golden file %s is missing from the test resources", resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Harness
    // ------------------------------------------------------------------------------------------

    /** One cup, its worlds, its racers and its transcript, assembled as the composition root assembles them. */
    private static final class Harness implements AutoCloseable {

        private final Env env;
        private final MapInstances worlds;
        /** Whether the cup has computer racers, which the transcript then records on every tick. */
        private final int comGrid;
        private int ticks;
        private final RaceRuns runs = new RaceRuns();
        private final CatalogHolder holder;
        private final List<Player> field = new ArrayList<>();
        private String deployedFor;

        /** The map and the GAME phase of the cup, when it is in one; {@code null} otherwise. */
        private String gamePhase() {
            Matcher matcher = GAME_PHASE.matcher(session.describe());
            return matcher.find() ? matcher.group() : null;
        }
        private final Map<String, Player> racers = new LinkedHashMap<>();
        private final Map<String, TestConnection> connections = new LinkedHashMap<>();
        final CupTranscript transcript = new CupTranscript();
        final CupSession session;
        private final TickPipeline pipeline;
        private LobbyTransition lobby;
        private WaitingRoom room;
        /** The racers the room handed to the hub, in order. */
        private final List<UUID> handedToHub = new ArrayList<>();

        private Harness(Env env, MapInstances worlds, LoadedCatalog catalog, RaceTimings timings, int comGrid) {
            this.env = env;
            this.worlds = worlds;
            this.comGrid = comGrid;
            this.holder = new CatalogHolder(catalog);
            MapTransition transition = new MapTransition(worlds, runs);
            CurrentMapBlocks blocks = new CurrentMapBlocks();
            FireworkBoostTracker boosts = new FireworkBoostTracker();
            FlightTickDriver flight = new FlightTickDriver(
                    new LivePlayerSampler(() -> field, boosts), new FlightTracker(),
                    new MinestomCollisionSpace(blocks));
            CompetitorField competitors = comGrid == 0 ? NoCompetitors.INSTANCE
                    : new ComField(() -> field, comGrid, ComDifficulty.MEDIUM,
                            Clock.fixed(Instant.ofEpochMilli(COM_ROUND_MILLIS), ZoneOffset.UTC), worlds::forWorld);
            this.session = new CupSession(holder, worlds, transition, runs, flight, blocks, boosts, timings, STEP,
                    () -> field, new NoOpRaceRecorder(), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), competitors);
            this.pipeline = new CupBeans().tickPipeline(session, boosts);
        }

        static Harness open(Env env, Path tempDir) throws IOException {
            return open(env, tempDir, bootCatalog(), TIMINGS);
        }

        static Harness open(Env env, Path tempDir, LoadedCatalog catalog, RaceTimings timings) throws IOException {
            return open(env, tempDir, catalog, timings, 0);
        }

        /** Opens a cup whose grid holds {@code comGrid} racers, humans included; zero means no computer racers. */
        static Harness open(Env env, Path tempDir, LoadedCatalog catalog, RaceTimings timings, int comGrid)
                throws IOException {
            writeWorld(env, tempDir, RIDGE, RIDGE_SPAWN, RIDGE_FLOOR);
            writeWorld(env, tempDir, DUNE, DUNE_SPAWN, DUNE_FLOOR);
            MapInstances instances = new MapInstances(env.process().instance(), tempDir.resolve("worlds"));
            return new Harness(env, instances, catalog, timings, comGrid);
        }

        /** Connects a racer on the spawn of {@code map}, prepared as the join handler prepares one. */
        void connect(String name, MapDefinition map) {
            seat(name, worlds.forWorld(map.world()), spawnOf(map));
        }

        /**
         * Puts the room in front of the cup: the lobby world, and a room that starts the cup and hands its racers to the hub
         * when the results have lingered. The hub is installed, and every racer it is sent is recorded in {@link #handedToHub}.
         */
        void useLobby(LobbyDefinition definition) {
            lobby = new LobbyTransition(worlds, definition);
            HubHandoff hub = new HubHandoff(() -> Optional.of(handedToHub::add), (racer, reason) -> { },
                    message -> {
                        throw new AssertionError("no refused handoff was expected, but got: " + message);
                    });
            room = new WaitingRoom(session, holder, () -> field, 1, 16, lobby, hub, new LobbyHud(),
                    new LobbySidebar(), null, () -> worlds.forWorld(RIDGE));
        }

        /** Connects a racer on the lobby's spawn, as the join handler does when a lobby is configured. */
        void connectToLobby(String name) {
            seat(name, lobby.instance(), lobby.spawn());
        }

        Instance lobbyInstance() {
            return lobby.instance();
        }

        /** Loads every chunk within {@code viewDistance} of {@code spawn}, as the preload of a placement would have them. */
    private static void preloadAround(Instance world, Vec3 spawn, int viewDistance) {
        preloadAround(world, new Pos(spawn.x(), spawn.y(), spawn.z()), viewDistance);
    }

    /** As {@link #preloadAround(Instance, Vec3, int)}, from a position. */
    private static void preloadAround(Instance world, Pos spawn, int viewDistance) {
        ChunkRange.chunksInRange(spawn, viewDistance, (chunkX, chunkZ) -> world.loadChunk(chunkX, chunkZ).join());
    }

    private void seat(String name, Instance instance, Pos spawn) {
            instance.loadChunk(spawn.chunkX(), spawn.chunkZ()).join();
            TestConnection connection = env.createConnection(new GameProfile(
                    UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name));
            Player racer = connection.connect(instance, spawn);
            Racers.prepare(racer);
            // Production preloads the chunks around each spawn during the wait, so a racer is placed before GO. The harness
            // has the same chunks present up front, so placements finish within the transcript's ticks.
            // A placement into another world uses the smaller of the racer's setting and that world's view distance, plus one.
            int viewDistance = Math.min(racer.getSettings().viewDistance(), worlds.forWorld(RIDGE).viewDistance()) + 1;
            preloadAround(worlds.forWorld(RIDGE), RIDGE_SPAWN, viewDistance);
            preloadAround(worlds.forWorld(DUNE), DUNE_SPAWN, viewDistance);
            connections.put(name, connection);
            racers.put(name, racer);
            field.add(racer);
            transcript.seat(name, connection);
        }

        Player racer(String name) {
            return racers.get(name);
        }

        /** Removes a racer the way the disconnect handler does: the session forgets them, then they leave. */
        void disconnect(String name) {
            Player racer = racers.remove(name);
            session.forget(racer.getUuid());
            field.remove(racer);
            racer.remove(true);
        }

        /** One step that does not tick: an action recorded with the state it leaves behind. */
        void act(String label, Runnable action) {
            transcript.begin();
            action.run();
            transcript.end(label, session.describe());
        }

        /**
         * One server tick, in the order the server runs it: the cup's tick, the room's, then Minestom's.
         *
         * <p>The server does not start a glide at GO. Each racer deploys their elytra on the first tick of the
         * GAME phase, as a player does after GO, and the transcript shows the deployment on the tick after it.
         */
        void tick(Runnable before) {
            ticks++;
            transcript.begin();
            before.run();
            String phase = gamePhase();
            if (phase != null && !phase.equals(deployedFor)) {
                for (Player racer : field) {
                    racer.setFlyingWithElytra(true);
                }
                deployedFor = phase;
            }
            pipeline.run();
            if (room != null) {
                room.tick();
            }
            env.tick();
            transcript.end("tick " + ticks, session.describe() + computerRacers());
        }

        /**
         * The computer racers on the courses, one line each in name order, with their position, rings passed and whether they
         * finished. Read from the entities and the runs after the tick, so the transcript shows what a player would see.
         * Empty for a cup without computer racers, so the other scenarios are unchanged.
         */
        private String computerRacers() {
            if (comGrid == 0) {
                return "";
            }
            List<String> lines = new ArrayList<>();
            for (Instance instance : List.of(worlds.forWorld(RIDGE), worlds.forWorld(DUNE))) {
                for (Entity entity : instance.getEntities()) {
                    if (ComNames.isComputer(entity.getUuid())) {
                        lines.add(computerLine(entity));
                    }
                }
            }
            lines.sort(Comparator.naturalOrder());
            StringBuilder block = new StringBuilder("\ncomputer racers");
            for (String line : lines) {
                block.append("\n  ").append(line);
            }
            return block.toString();
        }

        private String computerLine(Entity entity) {
            Pos at = entity.getPosition();
            String name = entity.getCustomName() == null
                    ? "none" : GsonComponentSerializer.gson().serialize(entity.getCustomName());
            String progress = runs.of(entity.getUuid())
                    .map(run -> "rings %d, %s".formatted(run.progress().passedCount(),
                            run.finished() ? "finished" : "racing"))
                    .orElse("not on the course");
            return "%s at %s, %s, %s, %s".formatted(name, coordinate(at.x()), coordinate(at.y()),
                    coordinate(at.z()), progress);
        }

        /** A coordinate to two decimals in the root locale, so the transcript reads the same on every machine. */
        private static String coordinate(double value) {
            return String.format(Locale.ROOT, "%.2f", value);
        }

        /** Ticks, running {@code before} ahead of each tick, until {@code stop} answers true or the budget runs out. */
        void runUntil(BooleanSupplier stop, Runnable before) {
            for (int step = 0; step < BUDGET && !stop.getAsBoolean(); step++) {
                tick(before);
            }
        }

        @Override
        public void close() {
            for (Player racer : List.copyOf(field)) {
                racer.remove(true);
            }
            field.clear();
            racers.clear();
            connections.clear();
            transcript.close();
            worlds.close();
        }

        private static LoadedCatalog bootCatalog() {
            return new LoadedCatalog(snapshot(CUP), CUP, Instant.EPOCH);
        }

        private static LoadedCatalog comCatalog() {
            return new LoadedCatalog(new CatalogSnapshot(Map.of(COM_RIDGE_RUN.name(), COM_RIDGE_RUN, DUNE_RUN.name(), DUNE_RUN),
                    Map.of(COM_CUP.name(), COM_CUP)), COM_CUP, Instant.EPOCH);
        }

        private static LoadedCatalog checkpointCatalog() {
            return new LoadedCatalog(new CatalogSnapshot(Map.of(CHECKPOINT_RUN.name(), CHECKPOINT_RUN),
                    Map.of(CHECKPOINT_CUP.name(), CHECKPOINT_CUP)), CHECKPOINT_CUP, Instant.EPOCH);
        }

        private static LoadedCatalog restartedCatalog() {
            return new LoadedCatalog(snapshot(RESTARTED_CUP), RESTARTED_CUP, Instant.EPOCH);
        }

        private static CatalogSnapshot snapshot(CupDefinition cup) {
            return new CatalogSnapshot(Map.of("ridge-run", RIDGE_RUN, "dune-run", DUNE_RUN), Map.of(cup.name(), cup));
        }
    }

    private static Ring ring(int index, Vec3 center, int points) {
        return new Ring(index, center, new Vec3(0, 0, 1), 6.0, points, RingType.STANDARD);
    }

    private static Pos spawnOf(MapDefinition map) {
        return new Pos(map.spawn().x(), map.spawn().y(), map.spawn().z());
    }

    /**
     * Writes a world through a Falco loader of its own, as {@code CupSessionTest} does, so the scenario reads the
     * on-disk format rather than a committed file.
     */
    private static void writeWorld(Env env, Path tempDir, String world, Vec3 spawn, Block floor) throws IOException {
        InstanceManager instanceManager = env.process().instance();
        FalcoAnvilLoader writer = FalcoAnvilLoader.builder()
                .build(tempDir.resolve("worlds").resolve(world), DimensionType.OVERWORLD.key());
        InstanceContainer instance = instanceManager.createInstanceContainer(DimensionType.OVERWORLD, writer);
        int x = (int) Math.floor(spawn.x());
        int y = (int) Math.floor(spawn.y()) - 1;
        int z = (int) Math.floor(spawn.z());
        try {
            instance.loadChunk(x >> 4, z >> 4).join();
            instance.setBlock(x, y, z, floor);
            instance.saveChunksToStorage().join();
        } finally {
            instanceManager.unregisterInstance(instance);
            writer.close();
        }
    }
}
