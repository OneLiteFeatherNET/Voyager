package net.elytrarace.voyager.server.inject;

import net.elytrarace.voyager.platform.world.ChunkLoader;
import net.elytrarace.voyager.platform.world.ChunkState;
import net.minestom.server.instance.Instance;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The production {@link ChunkLoader}: starts each chunk with the instance's optional load, and reports the load as settled
 * once its future is done.
 *
 * <p>A load that finishes with no chunk, because the world has no data for it, is settled and reported
 * {@link ChunkState#PRESENT}. It has nothing to wait for, and the placement moves the racer as it did before the preload was
 * asynchronous. A load that completes exceptionally is {@link ChunkState#FAILED}. A load not yet done is
 * {@link ChunkState#LOADING}.
 */
public final class SettledChunkLoader implements ChunkLoader {

    private record Key(Instance instance, int chunkX, int chunkZ) {
    }

    private final Map<Key, CompletableFuture<?>> loads = new ConcurrentHashMap<>();

    @Override
    public void start(Instance instance, int chunkX, int chunkZ) {
        loads.computeIfAbsent(new Key(instance, chunkX, chunkZ), key -> instance.loadOptionalChunk(chunkX, chunkZ));
    }

    @Override
    public ChunkState state(Instance instance, int chunkX, int chunkZ) {
        CompletableFuture<?> load = loads.get(new Key(instance, chunkX, chunkZ));
        if (load == null || !load.isDone()) {
            return ChunkState.LOADING;
        }
        return load.isCompletedExceptionally() ? ChunkState.FAILED : ChunkState.PRESENT;
    }
}
