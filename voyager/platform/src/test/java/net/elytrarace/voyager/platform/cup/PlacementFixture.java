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
import net.elytrarace.voyager.platform.tick.FlightTickDriver;
import net.elytrarace.voyager.platform.world.ChunkLoader;
import net.elytrarace.voyager.platform.world.CurrentMapBlocks;
import net.elytrarace.voyager.platform.world.MapInstances;
import net.elytrarace.voyager.platform.world.SynchronousChunkLoader;
import net.elytrarace.voyager.race.flow.RaceTimings;
import net.elytrarace.voyager.api.stats.RaceRecorder;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.instance.block.Block;
import net.minestom.server.world.DimensionType;
import net.minestom.testing.Env;
import net.onelitefeather.falco.anvil.FalcoAnvilLoader;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * A two-map cup on a live test server, for the placement, glide-out and freeze behaviour: two worlds with one floor block
 * under each spawn, one ring per map, a lobby instance the racers are connected into, and the session ticked by hand.
 *
 * <p>The clock is fixed and the step is 50 ms, so every timing is a number of ticks the test chose. Each test builds its
 * own fixture over its own temporary directory, and {@link #close()} releases the worlds, so nothing is shared between
 * tests.
 */
final class PlacementFixture implements AutoCloseable {

    static final String FIRST_WORLD = "place_first";
    static final String SECOND_WORLD = "place_second";
    static final String FIRST_MAP = "place-first";
    static final String SECOND_MAP = "place-second";
    static final String CUP = "place_cup";

    static final Vec3 FIRST_SPAWN = new Vec3(3.5, 65.0, 5.5);
    static final Vec3 SECOND_SPAWN = new Vec3(-37.5, 71.0, -88.5);
    static final Vec3 LOBBY_SPOT = new Vec3(200.5, 65.0, 200.5);

    /** Points about the first map's ring, which is centred eight blocks up and ten ahead of its spawn. */
    static final Vec3 FRONT_OF_FIRST_RING = FIRST_SPAWN.plus(new Vec3(0, 8, 9));
    static final Vec3 BEHIND_FIRST_RING = FIRST_SPAWN.plus(new Vec3(0, 8, 11));

    static final Duration STEP = Duration.ofMillis(50);
    static final Instant START = Instant.parse("2026-10-28T10:00:00Z");
    static final Clock CLOCK = Clock.fixed(START, ZoneOffset.UTC);
    static final BoostConfig BOOST = new BoostConfig(4, 9);

    static final MapDefinition FIRST_RUN = new MapDefinition(FIRST_MAP, FIRST_WORLD, FIRST_SPAWN,
            List.of(new Ring(0, FIRST_SPAWN.plus(new Vec3(0, 8, 10)), new Vec3(0, 0, 1), 6.0, 7, RingType.STANDARD)),
            Duration.ofSeconds(10), BOOST, new GuideLine(List.of(), 2, 1.0));

    static final MapDefinition SECOND_RUN = new MapDefinition(SECOND_MAP, SECOND_WORLD, SECOND_SPAWN,
            List.of(new Ring(0, SECOND_SPAWN.plus(new Vec3(0, 0, 10)), new Vec3(0, 0, 1), 6.0, 13, RingType.STANDARD)),
            Duration.ofSeconds(10), BOOST, new GuideLine(List.of(), 4, 1.5));

    private static final CupDefinition CUP_DEFINITION =
            new CupDefinition(CUP, List.of(FIRST_MAP, SECOND_MAP), GameMode.RACE);

    final Env env;
    final List<Player> online = new ArrayList<>();
    final Instance lobby;
    final Instance firstWorld;
    final Instance secondWorld;
    final MapInstances instances;
    final CupSession session;

    private final Path root;

    private PlacementFixture(Env env, Path root, RaceTimings timings, RaceRecorder recorder, ChunkLoader loader)
            throws IOException {
        this.env = env;
        this.root = root;
        writeWorld(env, FIRST_WORLD, FIRST_SPAWN);
        writeWorld(env, SECOND_WORLD, SECOND_SPAWN);
        this.instances = new MapInstances(env.process().instance(), root.resolve("worlds"));
        RaceRuns runs = new RaceRuns();
        MapTransition transition = new MapTransition(instances, runs, loader);
        CatalogHolder holder = new CatalogHolder(new LoadedCatalog(
                new CatalogSnapshot(Map.of(FIRST_MAP, FIRST_RUN, SECOND_MAP, SECOND_RUN), Map.of(CUP, CUP_DEFINITION)),
                CUP_DEFINITION, Instant.EPOCH));
        CurrentMapBlocks blocks = new CurrentMapBlocks();
        FireworkBoostTracker boosts = new FireworkBoostTracker();
        FlightTickDriver flight = new FlightTickDriver(
                new LivePlayerSampler(() -> online, boosts), new FlightTracker(), new MinestomCollisionSpace(blocks));
        this.session = new CupSession(holder, instances, transition, runs, flight, blocks, boosts, timings, STEP,
                () -> online, recorder, CLOCK, CompetitorField.none());
        this.lobby = env.createFlatInstance();
        this.firstWorld = instances.forWorld(FIRST_WORLD);
        this.secondWorld = instances.forWorld(SECOND_WORLD);
    }

    /**
     * Opens a cup with {@code timings}, and starts it with its first lobby, so the lobby ticks count down the lobby.
     */
    static PlacementFixture open(Env env, Path root, RaceTimings timings) throws IOException {
        return open(env, root, timings, new SynchronousChunkLoader());
    }

    /**
     * As {@link #open(Env, Path, RaceTimings)}, with the chunk loads of every placement started and read by {@code loader}.
     * A loader that reports every chunk as loading holds every placement where it is.
     */
    static PlacementFixture open(Env env, Path root, RaceTimings timings, ChunkLoader loader) throws IOException {
        PlacementFixture fixture = new PlacementFixture(env, root, timings, new NoRecorder(), loader);
        fixture.session.start(false);
        return fixture;
    }

    /** Connects a racer into the lobby instance, away from both spawns, and returns them. */
    Player connectRacer() {
        Player racer = env.createConnection().connect(lobby, new Pos(LOBBY_SPOT.x(), LOBBY_SPOT.y(), LOBBY_SPOT.z()));
        online.add(racer);
        return racer;
    }

    /** Advances the cup by one tick, then the environment by one tick, so the placements' futures can complete. */
    void tick() {
        session.advancePhase();
        env.tick();
    }

    /** Ticks until {@code done} holds, and fails with {@code what} if it does not within {@code budget} ticks. */
    void tickUntil(BooleanSupplier done, int budget, String what) {
        for (int tick = 0; tick < budget; tick++) {
            if (done.getAsBoolean()) {
                return;
            }
            tick();
        }
        if (!done.getAsBoolean()) {
            throw new AssertionError("%s did not happen within %s ticks".formatted(what, budget));
        }
    }

    /** Ticks until the first map's game phase has begun. */
    void untilGameStarts() {
        tickUntil(() -> session.describe().contains("phase GAME"), 2_000, "the first map's GAME phase");
    }

    /** Whether {@code racer} is standing on the spawn of {@code world} within a tenth of a block. */
    static boolean atSpawn(Player racer, Instance world, Vec3 spawn) {
        return racer.getInstance() == world && racer.getPosition().distance(new Pos(spawn.x(), spawn.y(), spawn.z())) <= 0.1;
    }

    /** Writes a world with one floor block under the spawn, through a Falco loader of its own. */
    private void writeWorld(Env env, String world, Vec3 spawn) throws IOException {
        InstanceManager manager = env.process().instance();
        FalcoAnvilLoader writer = FalcoAnvilLoader.builder()
                .build(root.resolve("worlds").resolve(world), DimensionType.OVERWORLD.key());
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

    @Override
    public void close() {
        online.forEach(racer -> racer.remove(true));
        online.clear();
        instances.close();
    }

    /** A recorder that keeps nothing: the placement tests do not read the statistics. */
    private static final class NoRecorder implements RaceRecorder {

        @Override
        public void recordRace(net.elytrarace.voyager.api.stats.RaceCompletion completion) {
        }

        @Override
        public void recordCup(net.elytrarace.voyager.api.stats.CupCompletion completion) {
        }
    }
}
