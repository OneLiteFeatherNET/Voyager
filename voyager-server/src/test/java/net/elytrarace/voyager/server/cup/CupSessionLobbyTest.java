package net.elytrarace.voyager.server.cup;

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
import net.elytrarace.voyager.platform.cup.CupSession;
import net.elytrarace.voyager.platform.flight.FlightTracker;
import net.elytrarace.voyager.platform.flight.Racers;
import net.elytrarace.voyager.platform.world.MapInstances;
import net.elytrarace.voyager.platform.world.MapTransition;
import net.elytrarace.voyager.platform.world.RaceRuns;
import net.elytrarace.voyager.race.flow.RaceTimings;
import net.elytrarace.voyager.race.flow.StartGate.Situation;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.Material;
import net.minestom.server.world.DimensionType;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import net.onelitefeather.falco.anvil.FalcoAnvilLoader;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The cup's lobby operations: what situation it is in for the start gate, and the two ways a start is undone.
 *
 * <p>Every test drives the cup with explicit {@code pipeline.run()} ticks, so a lobby of ten seconds is two
 * hundred ticks and no wall-clock time passes. Each test builds its own worlds under its own temp directory.
 */
@EnvTest
class CupSessionLobbyTest {

    private static final String ARENA = "lobby_arena";
    private static final String OTHER = "lobby_other";
    private static final Vec3 SPAWN = new Vec3(3.5, 65.0, 5.5);
    private static final Vec3 OTHER_SPAWN = new Vec3(-37.5, 71.0, -88.5);
    private static final Duration STEP = Duration.ofMillis(50);
    private static final Duration TEN_SECONDS = Duration.ofSeconds(10);
    private static final BoostConfig BOOST = new BoostConfig(4, 9);

    private static final MapDefinition ARENA_RUN = new MapDefinition("arena-run", ARENA, SPAWN,
            List.of(new Ring(0, SPAWN.plus(new Vec3(0, 8, 10)), new Vec3(0, 0, 1), 6.0, 7, RingType.STANDARD)),
            Duration.ofMillis(400), BOOST, new GuideLine(List.of(), 2, 1.0));

    private static final MapDefinition OTHER_RUN = new MapDefinition("other-run", OTHER, OTHER_SPAWN,
            List.of(new Ring(0, OTHER_SPAWN.plus(new Vec3(0, 0, 10)), new Vec3(0, 0, 1), 6.0, 13, RingType.STANDARD)),
            Duration.ofMillis(650), BOOST, new GuideLine(List.of(), 4, 1.5));

    @TempDir
    Path tempDir;

    private final List<Player> racers = new ArrayList<>();
    private MapInstances worlds;

    @AfterEach
    void releaseTheWorlds() {
        racers.forEach(racer -> racer.remove(true));
        racers.clear();
        if (worlds != null) {
            worlds.close();
            worlds = null;
        }
    }

    // ------------------------------------------------------------------------------------------
    // situation()
    // ------------------------------------------------------------------------------------------

    @Test
    void situationIsWaitingBeforeAnyStart(Env env) throws IOException {
        Fixture fixture = fixture(env, TEN_SECONDS, RaceTimings.DEFAULT.race(), GameMode.RACE);

        assertThat(fixture.session.situation(Duration.ofSeconds(3))).isEqualTo(Situation.WAITING);
    }

    @Test
    void situationIsCountdownBeforeTheCommitWindow(Env env) throws IOException {
        Fixture fixture = fixture(env, TEN_SECONDS, RaceTimings.DEFAULT.race(), GameMode.RACE);
        fixture.session.start(false);

        fixture.ticks(100);

        assertThat(fixture.session.situation(Duration.ofSeconds(3))).isEqualTo(Situation.COUNTDOWN);
    }

    /** One tick either side of the window: at three seconds left the start is committed, at 3.05 s it is not. */
    @Test
    void situationIsCommittedInTheLastThreeSeconds(Env env) throws IOException {
        Fixture fixture = fixture(env, TEN_SECONDS, RaceTimings.DEFAULT.race(), GameMode.RACE);
        fixture.session.start(false);

        fixture.ticks(139);
        assertThat(fixture.session.situation(Duration.ofSeconds(3))).isEqualTo(Situation.COUNTDOWN);

        fixture.ticks(1);
        assertThat(fixture.session.situation(Duration.ofSeconds(3))).isEqualTo(Situation.COMMITTED);
    }

    @Test
    void situationIsRunningOnceTheMapHasLaunched(Env env) throws IOException {
        Fixture fixture = fixture(env, Duration.ZERO, RaceTimings.DEFAULT.race(), GameMode.RACE);
        fixture.session.start(false);

        fixture.ticks(1);

        assertThat(fixture.session.situation(Duration.ofSeconds(3))).isEqualTo(Situation.RUNNING);
    }

    /**
     * A practice cup returns to its lobby on the same map after every attempt. That lobby is a retry, so the cup is
     * running, and the start gate must never cancel it.
     */
    @Test
    void practiceRetryLobbyIsRunning(Env env) throws IOException {
        Fixture fixture = fixture(env, Duration.ofMillis(100), Duration.ofMillis(100), GameMode.PRACTICE);
        fixture.session.start(false);

        fixture.ticks(6);

        assertThat(fixture.session.situation(Duration.ofSeconds(3))).isEqualTo(Situation.RUNNING);
    }

    // ------------------------------------------------------------------------------------------
    // disarm()
    // ------------------------------------------------------------------------------------------

    @Test
    void disarmReturnsTheCountdownToWaitingAndTheNextStartHasTheFullLobby(Env env) throws IOException {
        Fixture fixture = fixture(env, TEN_SECONDS, RaceTimings.DEFAULT.race(), GameMode.RACE);
        fixture.session.start(false);
        fixture.ticks(100);

        fixture.session.disarm();
        assertThat(fixture.session.situation(Duration.ofSeconds(3))).isEqualTo(Situation.WAITING);

        fixture.session.start(false);
        fixture.ticks(139);
        assertThat(fixture.session.situation(Duration.ofSeconds(3)))
                .describedAs("a full lobby, so 3.05 s are still ahead and the start is not yet committed")
                .isEqualTo(Situation.COUNTDOWN);
    }

    @Test
    void disarmIsRefusedOnceAMapHasBeenEntered(Env env) throws IOException {
        Fixture fixture = fixture(env, TEN_SECONDS, RaceTimings.DEFAULT.race(), GameMode.RACE);
        fixture.session.start(false);
        fixture.ticks(140);

        assertThatThrownBy(fixture.session::disarm).isInstanceOf(IllegalStateException.class);
    }

    // ------------------------------------------------------------------------------------------
    // abort()
    // ------------------------------------------------------------------------------------------

    @Test
    void abortForgetsRunsStandsRacersDownAndDescribesNotStarted(Env env) throws IOException {
        Fixture fixture = fixture(env, Duration.ZERO, RaceTimings.DEFAULT.race(), GameMode.RACE);
        Player racer = fixture.connect(ARENA, SPAWN);
        fixture.session.start(false);
        fixture.ticks(1);
        assertThat(fixture.runs.of(racer.getUuid())).isPresent();

        fixture.session.abort();

        assertThat(fixture.runs.of(racer.getUuid())).isEmpty();
        assertThat(racer.isFlyingWithElytra()).isFalse();
        assertThat(racer.getEquipment(EquipmentSlot.CHESTPLATE).material()).isNotEqualTo(Material.ELYTRA);
        assertThat(fixture.session.running()).isFalse();
        assertThat(fixture.session.describe()).contains("armed");
    }

    // ------------------------------------------------------------------------------------------
    // The fixture
    // ------------------------------------------------------------------------------------------

    /** A session on one or two worlds under the temp directory, with the given lobby, race and cup mode. */
    private Fixture fixture(Env env, Duration lobby, Duration race, GameMode mode) throws IOException {
        writeWorld(env, ARENA, SPAWN);
        writeWorld(env, OTHER, OTHER_SPAWN);
        MapInstances instances = new MapInstances(env.process().instance(), tempDir.resolve("worlds"));
        this.worlds = instances;
        RaceRuns runs = new RaceRuns();
        MapTransition transition = new MapTransition(instances, runs);
        CupDefinition cup = new CupDefinition("lobby_cup", List.of("arena-run"), mode);
        CatalogHolder holder = new CatalogHolder(new LoadedCatalog(
                new CatalogSnapshot(Map.of("arena-run", ARENA_RUN, "other-run", OTHER_RUN), Map.of(cup.name(), cup)),
                cup, Instant.EPOCH));
        RaceTimings timings = new RaceTimings(lobby, race, Duration.ofMillis(100), Duration.ofMillis(100));
        List<Player> field = racers;
        CupWiring.Cup wired = CupWiring.assemble(holder, instances, transition, runs, new FlightTracker(),
                timings, STEP, () -> field);
        return new Fixture(wired.session(), wired.pipeline(), runs, instances, env);
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

    private final class Fixture {

        private final CupSession session;
        private final net.elytrarace.voyager.platform.cup.TickPipeline pipeline;
        private final RaceRuns runs;
        private final MapInstances instances;
        private final Env env;

        private Fixture(CupSession session, net.elytrarace.voyager.platform.cup.TickPipeline pipeline,
                RaceRuns runs, MapInstances instances, Env env) {
            this.session = session;
            this.pipeline = pipeline;
            this.runs = runs;
            this.instances = instances;
            this.env = env;
        }

        void ticks(int count) {
            for (int tick = 0; tick < count; tick++) {
                pipeline.run();
            }
        }

        Player connect(String world, Vec3 spawn) {
            Pos at = new Pos(spawn.x(), spawn.y(), spawn.z());
            Instance instance = instances.forWorld(world);
            instance.loadChunk(at.chunkX(), at.chunkZ()).join();
            Player racer = env.createPlayer(instance, at);
            racers.add(racer);
            Racers.prepare(racer);
            return racer;
        }
    }
}
