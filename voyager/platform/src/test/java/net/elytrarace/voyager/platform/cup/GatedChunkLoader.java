package net.elytrarace.voyager.platform.cup;

import net.elytrarace.voyager.platform.world.ChunkLoader;
import net.elytrarace.voyager.platform.world.ChunkState;
import net.minestom.server.instance.Instance;

/**
 * A {@link ChunkLoader} whose chunks stay loading until a test calls {@link #release()}, so the test decides on which tick
 * a placement's chunks arrive. A load is started synchronously, as {@link net.elytrarace.voyager.platform.world.SynchronousChunkLoader}
 * does, so the chunks are in the world; only the report of them is held back, which is what the placement reads.
 *
 * <p>The gate is shared by every placement of the fixture that uses it. A test holds it only once the racers it must keep
 * placed are already placed, because a placed racer no longer reads it.
 */
final class GatedChunkLoader implements ChunkLoader {

    private boolean released = true;

    /** Reports every chunk as loading from now until {@link #release()}. */
    void hold() {
        released = false;
    }

    /** Reports the chunks as present from now on, so every placement that is waiting completes. */
    void release() {
        released = true;
    }

    @Override
    public void start(Instance instance, int chunkX, int chunkZ) {
        instance.loadOptionalChunk(chunkX, chunkZ).join();
    }

    @Override
    public ChunkState state(Instance instance, int chunkX, int chunkZ) {
        return released ? ChunkState.PRESENT : ChunkState.LOADING;
    }
}
