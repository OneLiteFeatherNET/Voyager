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
import net.elytrarace.voyager.platform.catalog.CatalogHolder;
import net.elytrarace.voyager.platform.catalog.CatalogSnapshot;
import net.elytrarace.voyager.platform.catalog.LoadedCatalog;
import net.elytrarace.voyager.platform.cup.TickPipeline;
import net.elytrarace.voyager.platform.collision.MinestomCollisionSpace;
import net.elytrarace.voyager.platform.flight.FireworkBoostTracker;
import net.elytrarace.voyager.platform.cup.LivePlayerSampler;
import net.elytrarace.voyager.platform.tick.FlightTickDriver;
import net.elytrarace.voyager.platform.world.CurrentMapBlocks;
import net.elytrarace.voyager.platform.flight.FlightTracker;
import net.elytrarace.voyager.platform.world.MapInstances;
import net.elytrarace.voyager.platform.cup.MapTransition;
import net.elytrarace.voyager.platform.cup.RaceRuns;
import net.elytrarace.voyager.server.inject.CupBeans;
import net.elytrarace.voyager.race.flow.RaceTimings;
import net.elytrarace.voyager.platform.cup.CupSession;
import net.elytrarace.voyager.platform.flight.Racers;
import net.elytrarace.voyager.platform.lobby.LobbyHud;
import net.elytrarace.voyager.platform.lobby.LobbyTransition;
import net.elytrarace.voyager.platform.lobby.WaitingRoom;
import net.minestom.server.coordinate.Pos;
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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * <p>The golden files were generated once, from the code of {@code main} before the cup was moved out of the
 * server module, and they are never regenerated. A move that changes a line of any transcript is a behaviour
 * change, and the test fails naming the first differing step and line. The test itself never writes a golden
 * file.
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
     * the lobby to the first map at the launch, and the room returns the racer to the lobby when the cup has finished.
     * Characterises the lobby transfers; the four scenarios above run without a room and are unchanged.
     */
    @Test
    void lobbyWorldStart(Env env) throws IOException {
        writeWorld(env, tempDir, LOBBY, LOBBY_SPAWN, LOBBY_FLOOR);
        harness = Harness.open(env, tempDir);
        harness.useLobby(new LobbyDefinition(LOBBY, LOBBY_SPAWN));
        harness.connectToLobby(ADA);

        // Stops on the first tick after the racer has left the lobby and come back to it: the cup's end. The room
        // starts the next countdown on that same tick, so the transcript ends with that countdown's first tick.
        boolean[] left = {false};
        harness.runUntil(() -> {
            boolean inLobby = harness.racer(ADA).getInstance() == harness.lobbyInstance();
            left[0] |= !inLobby;
            return left[0] && inLobby;
        }, () -> { });

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

        private Harness(Env env, MapInstances worlds, LoadedCatalog catalog, RaceTimings timings) {
            this.env = env;
            this.worlds = worlds;
            this.holder = new CatalogHolder(catalog);
            MapTransition transition = new MapTransition(worlds, runs);
            CurrentMapBlocks blocks = new CurrentMapBlocks();
            FireworkBoostTracker boosts = new FireworkBoostTracker();
            FlightTickDriver flight = new FlightTickDriver(
                    new LivePlayerSampler(() -> field, boosts), new FlightTracker(),
                    new MinestomCollisionSpace(blocks));
            this.session = new CupSession(holder, worlds, transition, runs, flight, blocks, boosts, timings, STEP,
                    () -> field);
            this.pipeline = new CupBeans().tickPipeline(session, boosts);
        }

        static Harness open(Env env, Path tempDir) throws IOException {
            return open(env, tempDir, bootCatalog(), TIMINGS);
        }

        static Harness open(Env env, Path tempDir, LoadedCatalog catalog, RaceTimings timings) throws IOException {
            writeWorld(env, tempDir, RIDGE, RIDGE_SPAWN, RIDGE_FLOOR);
            writeWorld(env, tempDir, DUNE, DUNE_SPAWN, DUNE_FLOOR);
            MapInstances instances = new MapInstances(env.process().instance(), tempDir.resolve("worlds"));
            return new Harness(env, instances, catalog, timings);
        }

        /** Connects a racer on the spawn of {@code map}, prepared as the join handler prepares one. */
        void connect(String name, MapDefinition map) {
            seat(name, worlds.forWorld(map.world()), spawnOf(map));
        }

        /** Puts the room in front of the cup: the lobby world, and a room that starts the cup and returns racers to it. */
        void useLobby(LobbyDefinition definition) {
            lobby = new LobbyTransition(worlds, definition);
            room = new WaitingRoom(session, () -> field, 1, 16, lobby, racer -> { }, new LobbyHud(),
                    () -> worlds.forWorld(RIDGE));
        }

        /** Connects a racer on the lobby's spawn, as the join handler does when a lobby is configured. */
        void connectToLobby(String name) {
            seat(name, lobby.instance(), lobby.spawn());
        }

        Instance lobbyInstance() {
            return lobby.instance();
        }

        private void seat(String name, Instance instance, Pos spawn) {
            instance.loadChunk(spawn.chunkX(), spawn.chunkZ()).join();
            TestConnection connection = env.createConnection(new GameProfile(
                    UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name));
            Player racer = connection.connect(instance, spawn);
            Racers.prepare(racer);
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
            transcript.end("tick " + ticks, session.describe());
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
