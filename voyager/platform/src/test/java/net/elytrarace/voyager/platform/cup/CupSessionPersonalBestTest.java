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
import net.elytrarace.voyager.platform.stats.BestAnswer;
import net.elytrarace.voyager.platform.stats.NoOpRaceRecorder;
import net.elytrarace.voyager.platform.stats.PersonalBestReader;
import net.elytrarace.voyager.platform.tick.FlightTickDriver;
import net.elytrarace.voyager.platform.world.CurrentMapBlocks;
import net.elytrarace.voyager.platform.world.MapInstances;
import net.elytrarace.voyager.platform.world.SynchronousChunkLoader;
import net.elytrarace.voyager.race.flow.RaceTimings;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
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
 * When the cup asks the store for a racer's stored best: once for each human racer at the map's GO, and only in a ranked cup
 * with statistics on. The store is a fake that records each request and never answers, so the cup is not held up by it.
 */
@EnvTest
class CupSessionPersonalBestTest {

    private static final String WORLD = "best_world";
    private static final String MAP = "best-map";
    private static final String CUP = "best_cup";
    private static final Vec3 SPAWN = new Vec3(3.5, 65.0, 5.5);
    private static final Duration STEP = Duration.ofMillis(50);
    private static final Instant START = Instant.parse("2026-10-29T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(START, ZoneOffset.UTC);
    private static final MapDefinition MAP_DEFINITION = new MapDefinition(MAP, WORLD, SPAWN,
            List.of(new Ring(0, SPAWN.plus(new Vec3(0, 8, 10)), new Vec3(0, 0, 1), 6.0, 7, RingType.STANDARD)),
            Duration.ofMillis(400), new BoostConfig(4, 9), new GuideLine(List.of(), 2, 1.0));
    /** A one-second lobby and a ten-second race: the wait is the lobby, and the racer is placed before GO. */
    private static final RaceTimings TIMINGS = new RaceTimings(
            Duration.ofSeconds(1), Duration.ofSeconds(10), Duration.ofSeconds(1), Duration.ofSeconds(1));
    private static final int BUDGET = 2_000;

    @TempDir
    Path tempDir;

    private final List<Player> online = new ArrayList<>();
    private MapInstances worlds;

    @AfterEach
    void releaseTheWorlds() {
        online.forEach(racer -> racer.remove(true));
        online.clear();
        if (worlds != null) {
            worlds.close();
            worlds = null;
        }
    }

    @Test
    void theBestIsRequestedOnceForTheRacerAtTheGoOfARankedMap(Env env) throws IOException {
        FakeStore store = new FakeStore(true);
        Player racer = startCup(env, store, GameMode.RACE);

        assertThat(store.requests).as("nothing is read while the racer waits in the lobby").isEmpty();
        untilGameStarts(env);

        assertThat(store.requests).as("one request, made at the GO").hasSize(1);
        FakeStore.Request request = store.requests.getFirst();
        assertThat(request.playerId()).isEqualTo(racer.getUuid());
        assertThat(request.mapKey()).isEqualTo("elytrarace:" + MAP);
        assertThat(request.mode()).isEqualTo(GameMode.RACE);
    }

    @Test
    void noBestIsRequestedWhenStatisticsAreOff(Env env) throws IOException {
        FakeStore store = new FakeStore(false);
        startCup(env, store, GameMode.RACE);

        untilGameStarts(env);

        assertThat(store.requests).as("with statistics off no read is started, so no best is ever shown").isEmpty();
    }

    @Test
    void noBestIsRequestedInAPracticeCup(Env env) throws IOException {
        FakeStore store = new FakeStore(true);
        startCup(env, store, GameMode.PRACTICE);

        untilGameStarts(env);

        assertThat(store.requests).as("a practice cup is not ranked, so its finishes have no stored best").isEmpty();
    }

    // ------------------------------------------------------------------------------------------
    // Fixture
    // ------------------------------------------------------------------------------------------

    /** Builds a one-map cup in {@code mode} with {@code store}, connects one racer on the spawn and starts the cup. */
    private Player startCup(Env env, PersonalBestReader store, GameMode mode) throws IOException {
        writeWorld(env);
        worlds = new MapInstances(env.process().instance(), tempDir.resolve("worlds"));
        CupDefinition cup = new CupDefinition(CUP, List.of(MAP), mode);
        CatalogHolder holder = new CatalogHolder(new LoadedCatalog(
                new CatalogSnapshot(Map.of(MAP, MAP_DEFINITION), Map.of(CUP, cup)), cup, Instant.EPOCH));
        RaceRuns runs = new RaceRuns();
        MapTransition transition = new MapTransition(worlds, runs, new SynchronousChunkLoader());
        CurrentMapBlocks blocks = new CurrentMapBlocks();
        FireworkBoostTracker boosts = new FireworkBoostTracker();
        FlightTickDriver flight = new FlightTickDriver(
                new LivePlayerSampler(() -> online, boosts), new FlightTracker(), new MinestomCollisionSpace(blocks));
        CupSession session = new CupSession(holder, worlds, transition, runs, flight, blocks, boosts, TIMINGS, STEP,
                () -> online, new NoOpRaceRecorder(), CLOCK, CompetitorField.none(), store);
        Instance world = worlds.forWorld(WORLD);
        world.loadChunk(0, 0).join();
        Player racer = env.createConnection().connect(world, new Pos(SPAWN.x(), SPAWN.y(), SPAWN.z()));
        online.add(racer);
        session.start(false);
        this.session = session;
        this.env = env;
        return racer;
    }

    private CupSession session;
    private Env env;

    /** Ticks until the map's GAME phase has begun, the same tick order the server runs. */
    private void untilGameStarts(Env env) {
        for (int tick = 0; tick < BUDGET; tick++) {
            session.advancePhase();
            env.tick();
            if (session.describe().contains("phase GAME")) {
                return;
            }
        }
        throw new AssertionError("the GAME phase did not begin within %s ticks".formatted(BUDGET));
    }

    private void writeWorld(Env env) throws IOException {
        InstanceManager manager = env.process().instance();
        FalcoAnvilLoader writer = FalcoAnvilLoader.builder()
                .build(tempDir.resolve("worlds").resolve(WORLD), DimensionType.OVERWORLD.key());
        InstanceContainer instance = manager.createInstanceContainer(DimensionType.OVERWORLD, writer);
        try {
            instance.loadChunk(0, 0).join();
            instance.setBlock((int) Math.floor(SPAWN.x()), (int) Math.floor(SPAWN.y()) - 1,
                    (int) Math.floor(SPAWN.z()), Block.GOLD_BLOCK);
            instance.saveChunksToStorage().join();
        } finally {
            manager.unregisterInstance(instance);
            writer.close();
        }
    }

    /** A store that records each request and never answers it, so a test decides when, or whether, an answer comes. */
    private static final class FakeStore implements PersonalBestReader {

        record Request(UUID playerId, String mapKey, String layoutVersion, GameMode mode) {
        }

        private final boolean enabled;
        final List<Request> requests = new ArrayList<>();

        FakeStore(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public boolean enabled() {
            return enabled;
        }

        @Override
        public BestAnswer bestOn(UUID playerId, String mapKey, String layoutVersion, GameMode mode) {
            requests.add(new Request(playerId, mapKey, layoutVersion, mode));
            return new BestAnswer(CLOCK);
        }
    }
}
