package net.elytrarace.voyager.platform.cup;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.platform.world.ChunkLoader;
import net.elytrarace.voyager.platform.world.ChunkState;
import net.elytrarace.voyager.platform.world.SynchronousChunkLoader;
import net.elytrarace.voyager.race.flow.RaceTimings;
import net.elytrarace.voyager.race.flow.StartGate;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Where the racers of a cup's first map wait while the start countdown runs, when a lobby world is configured: in the lobby
 * world until {@link RaceTimings#ARRIVAL_LEAD} before GO, then on the first map's spawn, held until GO. Played through the
 * cup on a live test server, ticked by hand, so every tick count is one the test chose.
 *
 * <p>The lobby is ten seconds, which is {@link #LOBBY_TICKS} ticks, so the move begins on tick {@link #LEAD_TICKS}, when the
 * lobby has {@link #LEAD_TICKS} ticks left. The test without a lobby world shows that nothing changes for a server that has
 * none.
 */
@EnvTest
class LobbyWorldArrivalTest {

    /** Lobby of ten seconds, a one-second race, and a short end between maps. */
    private static final RaceTimings TIMINGS = new RaceTimings(
            Duration.ofSeconds(10), Duration.ofSeconds(1), Duration.ofMillis(200), Duration.ofMillis(200));

    /** The lobby's length in ticks, which is when GO fires if every racer is placed in time. */
    private static final int LOBBY_TICKS = 200;

    /** The ticks before GO at which a racer is moved onto the map: {@link RaceTimings#ARRIVAL_LEAD} at 50 ms a tick. */
    private static final int LEAD_TICKS = (int) (RaceTimings.ARRIVAL_LEAD.toMillis() / PlacementFixture.STEP.toMillis());

    /** The longest the move may take to show in the racer's world once it has begun, when the chunks are already there. */
    private static final int MOVE_TICKS = 5;

    /** Ticks a placement may take on a live server before the test calls it a failure. */
    private static final int PLACEMENT_BUDGET = 200;

    /** The longest GO is held, in ticks: {@link RaceTimings#GO_HOLD_LIMIT} at 50 ms a tick. */
    private static final int HOLD_TICKS = 100;

    /** Ticks GO may take to fire before the test calls it missing: comfortably past the hold. */
    private static final int GO_BUDGET = 1_000;

    /** The chunk the first map's spawn stands in: the spawn is at x 3.5, z 5.5. */
    private static final int SPAWN_CHUNK_X = 0;
    private static final int SPAWN_CHUNK_Z = 0;

    @TempDir
    Path tempDir;

    /** The racer is in the lobby world with more than five seconds left, and is moved onto the map once five are left. */
    @Test
    void aRacerStaysInTheLobbyWorldUntilFiveSecondsBeforeGoAndIsMovedThen(Env env) throws IOException {
        try (PlacementFixture cup = PlacementFixture.openWithLobbyWorld(env, tempDir, TIMINGS,
                new SynchronousChunkLoader())) {
            Player racer = cup.connectRacer();

            cup.tick(LOBBY_TICKS - LEAD_TICKS - 1);

            assertThat(racer.getInstance())
                    .as("the racer's world with more than five seconds of lobby left")
                    .isSameAs(cup.lobby);
            assertThat(cup.session.lobbyRemaining())
                    .as("the lobby left when the racer is still in the lobby world")
                    .isGreaterThan(RaceTimings.ARRIVAL_LEAD);

            cup.tickUntil(() -> racer.getInstance() != cup.lobby, MOVE_TICKS, "the racer's move onto the first map");

            assertThat(cup.ticksPlayed())
                    .as("the tick the move shows in the racer's world, counted from the start of the lobby")
                    .isBetween(LOBBY_TICKS - LEAD_TICKS, LOBBY_TICKS - LEAD_TICKS + MOVE_TICKS);
            assertThat(cup.session.lobbyRemaining())
                    .as("the lobby left when the move has begun")
                    .isLessThanOrEqualTo(RaceTimings.ARRIVAL_LEAD);
        }
    }

    /** The racer is on the spawn from the move until GO, is not moved by the lobby's last ticks, and runs from GO. */
    @Test
    void aMovedRacerIsHeldOnTheSpawnUntilGoAndRunsFromGo(Env env) throws IOException {
        try (PlacementFixture cup = PlacementFixture.openWithLobbyWorld(env, tempDir, TIMINGS,
                new SynchronousChunkLoader())) {
            Player racer = cup.connectRacer();
            cup.tickUntil(() -> PlacementFixture.atSpawn(racer, cup.firstWorld, PlacementFixture.FIRST_SPAWN),
                    PLACEMENT_BUDGET, "the racer's placement on the first map");

            cup.tick(LOBBY_TICKS - 1 - cup.ticksPlayed());

            assertThat(cup.session.describe()).as("the phase one tick before GO").contains("phase LOBBY");
            assertThat(PlacementFixture.atSpawn(racer, cup.firstWorld, PlacementFixture.FIRST_SPAWN))
                    .as("the held racer on the spawn one tick before GO").isTrue();

            cup.untilGameStarts();

            assertThat(cup.runs.of(racer.getUuid())).as("the run the racer starts at GO").isPresent();
            assertThat(PlacementFixture.atSpawn(racer, cup.firstWorld, PlacementFixture.FIRST_SPAWN))
                    .as("the racer on the spawn at GO").isTrue();
        }
    }

    /**
     * The first map's chunks around the spawn are started on the first tick of the countdown, while the racer is still in
     * the lobby world, so the move five seconds before GO does not wait for them.
     */
    @Test
    void theFirstMapsChunksAreStartedAtTheStartOfTheCountdownAndTheRacerIsNotMovedThen(Env env) throws IOException {
        RecordingChunkLoader chunks = new RecordingChunkLoader();
        try (PlacementFixture cup = PlacementFixture.openWithLobbyWorld(env, tempDir, TIMINGS, chunks)) {
            Player racer = cup.connectRacer();

            cup.tick();

            assertThat(racer.getInstance()).as("the racer's world on the first lobby tick").isSameAs(cup.lobby);
            assertThat(chunks.startedIn(cup.firstWorld))
                    .as("the chunks the first map's spawn needs, started on the first lobby tick")
                    .contains(new Started(SPAWN_CHUNK_X, SPAWN_CHUNK_Z));
        }
    }

    /**
     * Without a lobby world, the racer is placed on the first map at its first lobby tick, as it always was: long before the
     * last five seconds.
     */
    @Test
    void withoutALobbyWorldTheRacerIsPlacedOnTheFirstMapFromTheFirstLobbyTick(Env env) throws IOException {
        try (PlacementFixture cup = PlacementFixture.open(env, tempDir, TIMINGS)) {
            Player racer = cup.connectRacer();

            cup.tickUntil(() -> PlacementFixture.atSpawn(racer, cup.firstWorld, PlacementFixture.FIRST_SPAWN),
                    PLACEMENT_BUDGET, "the racer's placement on the first map");

            assertThat(cup.ticksPlayed())
                    .as("the tick the racer is placed on, counted from the start of the lobby")
                    .isLessThan(LOBBY_TICKS - LEAD_TICKS);
        }
    }

    /** A countdown cancelled before the move has nothing to send back: the racer never left the lobby world. */
    @Test
    void aCancelBeforeTheMoveLeavesTheRacerWhereTheyAreInTheLobby(Env env) throws IOException {
        try (PlacementFixture cup = PlacementFixture.openWithLobbyWorld(env, tempDir, TIMINGS,
                new SynchronousChunkLoader())) {
            Player racer = cup.connectRacer();
            cup.tick(LOBBY_TICKS - LEAD_TICKS - 20);
            Pos before = racer.getPosition();

            cup.session.disarm(cup.lobby, lobbySpot());
            cup.tick(5);

            assertThat(racer.getInstance()).as("the racer's world after the cancel").isSameAs(cup.lobby);
            assertThat(racer.getPosition().distance(before))
                    .as("the racer's distance from where they stood before the cancel")
                    .isLessThanOrEqualTo(0.1);
        }
    }

    /**
     * A countdown cancelled after the move, while more than the commit window is left, sends the placed racer back to the
     * lobby world.
     */
    @Test
    void aCancelAfterTheMoveSendsThePlacedRacerBackToTheLobbyWorld(Env env) throws IOException {
        try (PlacementFixture cup = PlacementFixture.openWithLobbyWorld(env, tempDir, TIMINGS,
                new SynchronousChunkLoader())) {
            Player racer = cup.connectRacer();
            cup.tickUntil(() -> PlacementFixture.atSpawn(racer, cup.firstWorld, PlacementFixture.FIRST_SPAWN),
                    PLACEMENT_BUDGET, "the racer's placement on the first map");
            assertThat(cup.session.situation(StartGate.COMMIT_WINDOW))
                    .as("the situation before the cancel").isEqualTo(StartGate.Situation.COUNTDOWN);

            cup.session.disarm(cup.lobby, lobbySpot());
            cup.tickUntil(() -> racer.getInstance() == cup.lobby, PLACEMENT_BUDGET,
                    "the cancelled racer's return to the lobby world");

            assertThat(racer.getPosition().distance(lobbySpot()))
                    .as("the racer's distance from the lobby spot after the cancel")
                    .isLessThanOrEqualTo(0.1);
        }
    }

    /**
     * The commit window is the same with the move five seconds before GO: a cancel is still allowed once the racers are on
     * the map, and it is refused from the last three seconds on.
     */
    @Test
    void aCancelIsRefusedOnceTheLastThreeSecondsHaveBegunWithTheRacersOnTheMap(Env env) throws IOException {
        try (PlacementFixture cup = PlacementFixture.openWithLobbyWorld(env, tempDir, TIMINGS,
                new SynchronousChunkLoader())) {
            Player racer = cup.connectRacer();
            cup.tickUntil(() -> PlacementFixture.atSpawn(racer, cup.firstWorld, PlacementFixture.FIRST_SPAWN),
                    PLACEMENT_BUDGET, "the racer's placement on the first map");
            cup.tickUntil(() -> cup.session.situation(StartGate.COMMIT_WINDOW) == StartGate.Situation.COMMITTED,
                    LOBBY_TICKS, "the start being committed");

            assertThatThrownBy(() -> cup.session.disarm(cup.lobby, lobbySpot()))
                    .as("a cancel once the start is committed")
                    .isInstanceOf(IllegalStateException.class);
            assertThat(PlacementFixture.atSpawn(racer, cup.firstWorld, PlacementFixture.FIRST_SPAWN))
                    .as("the racer still on the spawn after the refused cancel").isTrue();
        }
    }

    /**
     * A racer whose chunks are still loading when the move begins is not on the map, so GO is held for them as it is for any
     * racer still loading: the lobby stands on its last tick for the whole hold, then GO fires with the racer holding no run.
     */
    @Test
    void aRacerStillLoadingWhenTheMoveBeginsHoldsGoLikeAnyOtherLoadingRacer(Env env) throws IOException {
        GatedChunkLoader loader = new GatedChunkLoader();
        loader.hold();
        try (PlacementFixture cup = PlacementFixture.openWithLobbyWorld(env, tempDir, TIMINGS, loader)) {
            Player racer = cup.connectRacer();

            cup.tickUntil(() -> cup.session.describe().contains("phase GAME"), LOBBY_TICKS + HOLD_TICKS + GO_BUDGET,
                    "the first map's GAME phase");

            assertThat(cup.ticksPlayed())
                    .as("the tick GO fired on, held for the racer still loading")
                    .isEqualTo(LOBBY_TICKS + HOLD_TICKS);
            assertThat(cup.runs.of(racer.getUuid())).as("the racer still loading holds no run at GO").isEmpty();
        }
    }

    /**
     * A racer who is placed on the next map at the end of the first map is moved as before: the lobby world is only for the
     * first map's lobby, so nothing about the results or the next map changes.
     */
    @Test
    void aRacerIsPlacedOnTheNextMapDuringTheResultsAsBefore(Env env) throws IOException {
        try (PlacementFixture cup = PlacementFixture.openWithLobbyWorld(env, tempDir, TIMINGS,
                new SynchronousChunkLoader())) {
            Player racer = cup.connectRacer();
            cup.untilGameStarts();
            cup.session.requestSkip();

            cup.tickUntil(() -> PlacementFixture.atSpawn(racer, cup.secondWorld, PlacementFixture.SECOND_SPAWN),
                    PLACEMENT_BUDGET, "the racer's placement on the second map");

            assertThat(cup.session.describe()).as("the first map is over").doesNotContain("phase GAME");
        }
    }

    private static Pos lobbySpot() {
        Vec3 spot = PlacementFixture.LOBBY_SPOT;
        return new Pos(spot.x(), spot.y(), spot.z());
    }

    /** One chunk a placement started, by its index. */
    private record Started(int chunkX, int chunkZ) {
    }

    /** A loader that completes each load as it starts, and records the chunks each instance was asked to load. */
    private static final class RecordingChunkLoader implements ChunkLoader {

        private final List<Instance> instances = new ArrayList<>();
        private final List<Started> chunks = new ArrayList<>();

        @Override
        public void start(Instance instance, int chunkX, int chunkZ) {
            instance.loadOptionalChunk(chunkX, chunkZ).join();
            instances.add(instance);
            chunks.add(new Started(chunkX, chunkZ));
        }

        @Override
        public ChunkState state(Instance instance, int chunkX, int chunkZ) {
            return ChunkState.PRESENT;
        }

        /** The chunks started in {@code instance}, in the order they were started. */
        List<Started> startedIn(Instance instance) {
            List<Started> started = new ArrayList<>();
            for (int index = 0; index < chunks.size(); index++) {
                if (instances.get(index) == instance) {
                    started.add(chunks.get(index));
                }
            }
            return started;
        }
    }
}
