package net.elytrarace.voyager.platform.world;

import net.elytrarace.voyager.platform.LogCapture;
import net.elytrarace.voyager.platform.world.PendingArrival.Outcome;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * A racer's placement on a map, begun without waiting for its chunks: the tick starts the preload and returns, reads each
 * chunk's state on later ticks, and places the racer once every chunk is present.
 *
 * <p>The chunk states are a fake {@link ChunkLoader} whose states the test sets by hand, so "loading", "present" and "failed"
 * are decided by the test and no chunk waits on a real load. The teleport itself is Minestom's, run by the test
 * environment's own ticks.
 */
@EnvTest
class AsyncPreloadTest {

    private static final String WORLD = "ridge_arena";
    private static final Pos SPAWN = new Pos(0.5, 65.0, 0.5);

    /** How many environment ticks a placement may take before the test gives up rather than running on. */
    private static final int BUDGET = 200;

    @Test
    void startingAPreloadReturnsOnTheSameTickAndWaitsForNoChunk(Env env) {
        Instance target = env.createFlatInstance();
        Player racer = connect(env);
        FakeChunkLoader loader = new FakeChunkLoader();

        PendingArrival arrival = PendingArrival.start(racer, WORLD, target, SPAWN, loader);

        assertThat(loader.started()).as("chunks requested for the spawn").isNotEmpty();
        assertThat(arrival.tick()).as("the first tick reports the placement still waiting").isEqualTo(Outcome.WAITING);
        assertThat(racer.getInstance()).as("the racer is not moved while chunks load").isNotSameAs(target);
    }

    @Test
    void theRacerIsNotPlacedWhileOneChunkIsLoadingAndIsPlacedAfterTheLastOneIsPresent(Env env) {
        Instance target = env.createFlatInstance();
        Player racer = connect(env);
        FakeChunkLoader loader = new FakeChunkLoader();
        PendingArrival arrival = PendingArrival.start(racer, WORLD, target, SPAWN, loader);
        loader.setAllButLast(ChunkState.PRESENT);

        for (int tick = 0; tick < 3; tick++) {
            assertThat(arrival.tick()).as("tick %s with one chunk still loading", tick).isEqualTo(Outcome.WAITING);
            env.tick();
        }
        assertThat(racer.getInstance()).as("the racer is not placed while a chunk is loading").isNotSameAs(target);

        loader.setLast(ChunkState.PRESENT);
        assertThat(placeWithin(env, arrival)).as("the racer is placed once every chunk is present")
                .isEqualTo(Outcome.PLACED);
        assertThat(racer.getInstance()).as("the racer's instance after the placement").isSameAs(target);
    }

    @Test
    void aFailedChunkHoldsTheRacerInPlaceLogsOnceWithTheWorldNameAndThrowsNothingIntoTheTick(Env env) {
        Instance target = env.createFlatInstance();
        Player racer = connect(env);
        FakeChunkLoader loader = new FakeChunkLoader();
        PendingArrival arrival = PendingArrival.start(racer, WORLD, target, SPAWN, loader);
        loader.setAllButLast(ChunkState.PRESENT);
        loader.setLast(ChunkState.FAILED);

        try (LogCapture log = LogCapture.of(PendingArrival.class)) {
            assertThatCode(arrival::tick).as("a failed chunk is not thrown into the tick").doesNotThrowAnyException();
            assertThat(arrival.tick()).as("the failure is kept").isEqualTo(Outcome.FAILED);
            assertThat(arrival.tick()).as("and it stays failed").isEqualTo(Outcome.FAILED);

            assertThat(log.warnings()).as("warnings logged for the one failure").hasSize(1);
            assertThat(log.warnings().getFirst()).as("the warning names the world").contains(WORLD);
        }
        assertThat(racer.getInstance()).as("the racer is held where they stood").isNotSameAs(target);
    }

    @Test
    void aLoadThatNeverCompletesFailsWhenItsTickBudgetRunsOut(Env env) {
        Instance target = env.createFlatInstance();
        Player racer = connect(env);
        FakeChunkLoader loader = new FakeChunkLoader();
        PendingArrival arrival = PendingArrival.start(racer, WORLD, target, SPAWN, loader);

        Outcome outcome = Outcome.WAITING;
        for (int tick = 0; tick <= PendingArrival.LOAD_BUDGET_TICKS && outcome == Outcome.WAITING; tick++) {
            outcome = arrival.tick();
        }

        assertThat(outcome).as("a chunk still loading after the budget ends the placement").isEqualTo(Outcome.FAILED);
        assertThat(racer.getInstance()).as("the racer is not moved").isNotSameAs(target);
    }

    @Test
    void oneRacersLoadingChunksDoNotDelayAnotherRacersPlacement(Env env) {
        Instance target = env.createFlatInstance();
        Player waiting = connect(env);
        Player ready = connect(env);
        FakeChunkLoader waitingLoader = new FakeChunkLoader();
        FakeChunkLoader readyLoader = new FakeChunkLoader();
        PendingArrival waitingArrival = PendingArrival.start(waiting, WORLD, target, SPAWN, waitingLoader);
        PendingArrival readyArrival = PendingArrival.start(ready, WORLD, target, SPAWN, readyLoader);
        waitingLoader.setAllButLast(ChunkState.PRESENT);
        readyLoader.setAll(ChunkState.PRESENT);

        assertThat(placeWithin(env, readyArrival)).as("the racer whose chunks are present is placed")
                .isEqualTo(Outcome.PLACED);
        assertThat(waitingArrival.tick()).as("the racer with a chunk loading is not").isEqualTo(Outcome.WAITING);
        assertThat(waiting.getInstance()).isNotSameAs(target);
    }

    /** Ticks the environment and the arrival until the arrival reaches {@link Outcome#PLACED} or {@link Outcome#FAILED}. */
    private static Outcome placeWithin(Env env, PendingArrival arrival) {
        Outcome outcome = Outcome.WAITING;
        for (int tick = 0; tick < BUDGET && outcome == Outcome.WAITING; tick++) {
            outcome = arrival.tick();
            env.tick();
        }
        return outcome;
    }

    private static Player connect(Env env) {
        Instance source = env.createFlatInstance();
        return env.createConnection().connect(source, new Pos(0.5, 65.0, 0.5));
    }

    /**
     * A {@link ChunkLoader} whose chunks the test sets to a state by hand. A chunk starts {@link ChunkState#LOADING}, and an
     * unset chunk stays there.
     */
    private static final class FakeChunkLoader implements ChunkLoader {

        private final List<String> started = new ArrayList<>();
        private final Map<String, ChunkState> states = new HashMap<>();

        @Override
        public void start(Instance instance, int chunkX, int chunkZ) {
            String key = chunkX + "," + chunkZ;
            started.add(key);
            states.putIfAbsent(key, ChunkState.LOADING);
        }

        @Override
        public ChunkState state(Instance instance, int chunkX, int chunkZ) {
            return states.getOrDefault(chunkX + "," + chunkZ, ChunkState.LOADING);
        }

        List<String> started() {
            return started;
        }

        void setAll(ChunkState state) {
            for (String key : started) {
                states.put(key, state);
            }
        }

        /** Sets every chunk but the last one started. */
        void setAllButLast(ChunkState state) {
            for (int index = 0; index < started.size() - 1; index++) {
                states.put(started.get(index), state);
            }
        }

        void setLast(ChunkState state) {
            states.put(started.getLast(), state);
        }
    }
}
