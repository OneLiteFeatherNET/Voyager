package net.elytrarace.voyager.platform.world;

import net.minestom.server.instance.Instance;

/**
 * Starts the load of a chunk of an instance and reports whether it is there yet. It is the seam between a racer's
 * placement and the world: the placement never waits on a future, it asks {@link #state} on each tick.
 *
 * <p>Production is {@link #MINESTOM}, which starts the load with the instance's optional load and reads presence from
 * the instance's chunk table. Production never reports {@link ChunkState#FAILED}: a load that never completes is ended by
 * {@link PendingArrival}'s tick budget, which logs it. A test passes a loader that reports the states it chooses.
 */
public interface ChunkLoader {

    /** Production's loader: the instance's own optional load, with presence read from the instance's chunk table. */
    ChunkLoader MINESTOM = new ChunkLoader() {
        @Override
        public void start(Instance instance, int chunkX, int chunkZ) {
            instance.loadOptionalChunk(chunkX, chunkZ);
        }

        @Override
        public ChunkState state(Instance instance, int chunkX, int chunkZ) {
            return instance.getChunk(chunkX, chunkZ) == null ? ChunkState.LOADING : ChunkState.PRESENT;
        }
    };

    /**
     * Starts loading the chunk at {@code chunkX}, {@code chunkZ} of {@code instance}. Returns without waiting.
     *
     * @param instance the instance whose chunk is loaded
     * @param chunkX the chunk's x index
     * @param chunkZ the chunk's z index
     */
    void start(Instance instance, int chunkX, int chunkZ);

    /**
     * Reports where the chunk stands now.
     *
     * @param instance the instance whose chunk is read
     * @param chunkX the chunk's x index
     * @param chunkZ the chunk's z index
     * @return {@link ChunkState#PRESENT} once the chunk is there, {@link ChunkState#LOADING} until then, and
     *     {@link ChunkState#FAILED} if it cannot be loaded
     */
    ChunkState state(Instance instance, int chunkX, int chunkZ);
}
