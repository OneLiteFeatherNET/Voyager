package net.elytrarace.voyager.platform.world;

/** Where a chunk stands on one tick, as a {@link ChunkLoader} reports it. */
public enum ChunkState {
    /** Not there yet; the load is in progress or has not been started. */
    LOADING,
    /** Present in the instance. */
    PRESENT,
    /** Cannot be loaded. */
    FAILED
}
