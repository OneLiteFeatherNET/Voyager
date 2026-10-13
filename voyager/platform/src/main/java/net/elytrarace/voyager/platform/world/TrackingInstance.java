package net.elytrarace.voyager.platform.world;

import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.LightingChunk;
import net.minestom.server.world.DimensionType;
import net.minestom.server.registry.RegistryKey;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * An {@link InstanceContainer} that records every chunk load it starts, whoever starts it.
 *
 * <p>Minestom starts loads from more than one place: the code that asks for a chunk, and the view of each player
 * as they fly across the world. Every one of them goes through {@code retrieveChunk}, so overriding that one
 * method sees all of them, without the caller having to remember to report its loads.
 *
 * <p>Package-private. Built only by {@link MapInstances}, which waits on the recorded loads before it unregisters
 * the instance.
 */
final class TrackingInstance extends InstanceContainer {

    private final InFlightChunkLoads loads;

    TrackingInstance(UUID uuid, RegistryKey<DimensionType> dimensionType, InFlightChunkLoads loads) {
        super(uuid, dimensionType);
        this.loads = loads;
        setChunkSupplier(LightingChunk::new);
    }

    @Override
    protected CompletableFuture<Chunk> retrieveChunk(int chunkX, int chunkZ) {
        CompletableFuture<Chunk> load = super.retrieveChunk(chunkX, chunkZ);
        loads.track(load);
        return load;
    }
}
