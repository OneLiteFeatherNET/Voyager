package net.elytrarace.voyager.platform.world;

import net.minestom.server.instance.Instance;

/**
 * A test {@link ChunkLoader} that completes each chunk's load when the load starts, so a placement never waits on a real
 * chunk load running on its own thread. The load's duration depends on the machine, so a test that waited on it would
 * not be repeatable (F.I.R.S.T.).
 *
 * <p>Every chunk reads as present once started, including one the world has no data for: an absent chunk is settled, as
 * it is in production.
 */
public final class SynchronousChunkLoader implements ChunkLoader {

    @Override
    public void start(Instance instance, int chunkX, int chunkZ) {
        instance.loadOptionalChunk(chunkX, chunkZ).join();
    }

    @Override
    public ChunkState state(Instance instance, int chunkX, int chunkZ) {
        return ChunkState.PRESENT;
    }
}
