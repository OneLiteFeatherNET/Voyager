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
import net.elytrarace.voyager.platform.cup.MapTransition;
import net.elytrarace.voyager.platform.cup.RaceRuns;
import net.elytrarace.voyager.platform.cup.TickPipeline;
import net.elytrarace.voyager.platform.flight.FlightTracker;
import net.elytrarace.voyager.platform.world.MapInstances;
import net.elytrarace.voyager.race.flow.RaceTimings;
import net.elytrarace.voyager.race.flow.StartGate;
import net.minestom.server.entity.Player;
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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Shortening a running countdown: the cup's own lobby, changed on a live tick loop. Every test drives explicit
 * {@code pipeline.run()} ticks, so no wall-clock time passes, and builds its own worlds under its own temp directory.
 */
@EnvTest
class CupSessionShortenLobbyTest {

    private static final String ARENA = "shorten_arena";
    private static final Vec3 SPAWN = new Vec3(3.5, 65.0, 5.5);
    private static final Duration STEP = Duration.ofMillis(50);
    private static final BoostConfig BOOST = new BoostConfig(4, 9);
    private static final Duration COMMIT = StartGate.COMMIT_WINDOW;

    private static final MapDefinition ARENA_RUN = new MapDefinition("arena-run", ARENA, SPAWN,
            List.of(new Ring(0, SPAWN.plus(new Vec3(0, 8, 10)), new Vec3(0, 0, 1), 6.0, 7, RingType.STANDARD)),
            Duration.ofMillis(400), BOOST, new GuideLine(List.of(), 2, 1.0));

    @TempDir
    Path tempDir;

    private MapInstances worlds;

    @AfterEach
    void releaseTheWorlds() {
        if (worlds != null) {
            worlds.close();
            worlds = null;
        }
    }

    @Test
    void shorteningTakesEffectOnTheNextTick(Env env) throws IOException {
        Fixture fixture = fixture(env, Duration.ofSeconds(10));
        fixture.session.start(false);
        fixture.ticks(100);

        fixture.session.shortenLobby(Duration.ofSeconds(4));
        assertThat(fixture.session.lobbyRemaining()).isEqualTo(Duration.ofSeconds(4));

        fixture.ticks(1);
        assertThat(fixture.session.lobbyRemaining()).isEqualTo(Duration.ofMillis(3_950));
    }

    @Test
    void aCommittedLobbyIsNotShortened(Env env) throws IOException {
        Fixture fixture = fixture(env, Duration.ofSeconds(10));
        fixture.session.start(false);
        fixture.ticks(140);
        assertThat(fixture.session.situation(COMMIT)).isEqualTo(StartGate.Situation.COMMITTED);
        Duration before = fixture.session.lobbyRemaining();

        fixture.session.shortenLobby(Duration.ofSeconds(10));

        assertThat(fixture.session.lobbyRemaining()).isEqualTo(before);
    }

    @Test
    void aShortenedLobbyIsNotLengthened(Env env) throws IOException {
        Fixture fixture = fixture(env, Duration.ofSeconds(20));
        fixture.session.start(false);
        fixture.ticks(100);
        fixture.session.shortenLobby(Duration.ofSeconds(10));

        fixture.session.shortenLobby(Duration.ofSeconds(15));

        assertThat(fixture.session.lobbyRemaining()).isEqualTo(Duration.ofSeconds(10));
    }

    // ------------------------------------------------------------------------------------------
    // The fixture
    // ------------------------------------------------------------------------------------------

    private Fixture fixture(Env env, Duration lobby) throws IOException {
        writeWorld(env);
        MapInstances instances = new MapInstances(env.process().instance(), tempDir.resolve("worlds"));
        this.worlds = instances;
        RaceRuns runs = new RaceRuns();
        CupDefinition cup = new CupDefinition("shorten_cup", List.of("arena-run"), GameMode.RACE);
        CatalogHolder holder = new CatalogHolder(new LoadedCatalog(
                new CatalogSnapshot(Map.of("arena-run", ARENA_RUN), Map.of(cup.name(), cup)), cup, Instant.EPOCH));
        RaceTimings timings = new RaceTimings(lobby, Duration.ofSeconds(5), Duration.ofMillis(100),
                Duration.ofMillis(100));
        CupWiring.Cup wired = CupWiring.assemble(holder, instances, new MapTransition(instances, runs), runs,
                new FlightTracker(), timings, STEP, () -> List.<Player>of());
        return new Fixture(wired.session(), wired.pipeline());
    }

    /** Writes a world with one floor block under the spawn, through a Falco loader of its own. */
    private void writeWorld(Env env) throws IOException {
        InstanceManager manager = env.process().instance();
        FalcoAnvilLoader writer = FalcoAnvilLoader.builder()
                .build(tempDir.resolve("worlds").resolve(ARENA), DimensionType.OVERWORLD.key());
        InstanceContainer instance = manager.createInstanceContainer(DimensionType.OVERWORLD, writer);
        int x = (int) Math.floor(SPAWN.x());
        int y = (int) Math.floor(SPAWN.y()) - 1;
        int z = (int) Math.floor(SPAWN.z());
        try {
            instance.loadChunk(x >> 4, z >> 4).join();
            instance.setBlock(x, y, z, Block.GOLD_BLOCK);
            instance.saveChunksToStorage().join();
        } finally {
            manager.unregisterInstance(instance);
            writer.close();
        }
    }

    private record Fixture(CupSession session, TickPipeline pipeline) {

        void ticks(int count) {
            for (int tick = 0; tick < count; tick++) {
                pipeline.run();
            }
        }
    }
}
