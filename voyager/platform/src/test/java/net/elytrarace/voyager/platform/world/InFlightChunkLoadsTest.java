package net.elytrarace.voyager.platform.world;

import net.minestom.server.instance.Chunk;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class InFlightChunkLoadsTest {

    @Test
    void nothingIsInFlightWhenNoLoadWasTracked() {
        assertThat(new InFlightChunkLoads().awaitQuiet(Duration.ZERO)).isTrue();
    }

    @Test
    void aPendingLoadIsNotQuietAtAZeroBound() {
        InFlightChunkLoads loads = new InFlightChunkLoads();
        loads.track(new CompletableFuture<Chunk>());

        assertThat(loads.awaitQuiet(Duration.ZERO))
                .describedAs("a load that has not completed is still in flight")
                .isFalse();
    }

    @Test
    void aCompletedLoadIsNoLongerInFlight() {
        InFlightChunkLoads loads = new InFlightChunkLoads();
        CompletableFuture<Chunk> load = new CompletableFuture<>();
        loads.track(load);

        load.complete(null);

        assertThat(loads.awaitQuiet(Duration.ZERO)).isTrue();
    }

    @Test
    void aFailedLoadIsNoLongerInFlight() {
        InFlightChunkLoads loads = new InFlightChunkLoads();
        CompletableFuture<Chunk> load = new CompletableFuture<>();
        loads.track(load);

        load.completeExceptionally(new IllegalStateException("the loader failed"));

        assertThat(loads.awaitQuiet(Duration.ZERO))
                .describedAs("a load that failed has finished, so nothing is left to wait for")
                .isTrue();
    }
}
