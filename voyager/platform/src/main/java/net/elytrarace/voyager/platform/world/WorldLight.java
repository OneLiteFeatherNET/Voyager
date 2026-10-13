package net.elytrarace.voyager.platform.world;

import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.ChunkLoader;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.Section;
import net.minestom.server.instance.light.Light;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Marks the light a loaded chunk does not carry, so that Minestom's {@code LightingChunk} computes it on the first send
 * of that chunk.
 *
 * <p>A stored light is kept as it was saved. A light with no stored array is marked for relight, and only that kind:
 * a section can keep its stored sky light and still be relit for block light. The marking runs inside a
 * {@link ChunkLoader} decorator and not after the load completes. Minestom caches a loaded chunk, and so makes it visible
 * to the viewers, before the load future completes, so a mark applied afterwards could be too late for a chunk that was
 * already sent.
 *
 * <p>Package-private. {@link MapInstances} wraps the Falco loader of every world it opens in {@link #markingLoader}.
 */
final class WorldLight {

    private WorldLight() {
    }

    /**
     * Wraps a loader so every chunk it loads has its light-less sections marked before the chunk is cached. Every other
     * call goes to the delegate unchanged, so the wrapper saves and unloads exactly as the delegate would.
     *
     * @param delegate the loader that reads the world, for Falco the region files
     * @return a loader that marks what the delegate loads
     */
    static ChunkLoader markingLoader(ChunkLoader delegate) {
        return new MarkingLoader(delegate);
    }

    /**
     * Marks each light of each section that has no stored array.
     *
     * <p>{@code Light.array()} cannot be used for this test, because Minestom returns the same empty array for a light
     * that was never set and for an all-zero stored one. {@code requiresSend()} can: {@code set(...)} arms it and an
     * absent light never has it armed. Reading it clears the flag, so a stored light is re-armed with
     * {@code set(array())}, the round trip that {@code Section.copy()} uses, which leaves the light as it was.
     *
     * @param chunk a chunk that has just been loaded and is not yet visible to any viewer
     */
    static void markLightless(Chunk chunk) {
        for (int sectionY = chunk.getMinSection(); sectionY < chunk.getMaxSection(); sectionY++) {
            Section section = chunk.getSection(sectionY);
            markIfAbsent(section.skyLight());
            markIfAbsent(section.blockLight());
        }
    }

    private static void markIfAbsent(Light light) {
        if (light.requiresSend()) {
            light.set(light.array());
            return;
        }
        light.invalidate();
    }

    private record MarkingLoader(ChunkLoader delegate) implements ChunkLoader {

        @Override
        public void loadInstance(Instance instance) {
            delegate.loadInstance(instance);
        }

        @Override
        public @Nullable Chunk loadChunk(Instance instance, int chunkX, int chunkZ) {
            Chunk chunk = delegate.loadChunk(instance, chunkX, chunkZ);
            if (chunk != null) {
                markLightless(chunk);
            }
            return chunk;
        }

        @Override
        public void saveInstance(Instance instance) {
            delegate.saveInstance(instance);
        }

        @Override
        public void saveChunk(Chunk chunk) {
            delegate.saveChunk(chunk);
        }

        @Override
        public void saveChunks(Collection<Chunk> chunks) {
            delegate.saveChunks(chunks);
        }

        @Override
        public boolean supportsParallelSaving() {
            return delegate.supportsParallelSaving();
        }

        @Override
        public boolean supportsParallelLoading() {
            return delegate.supportsParallelLoading();
        }

        @Override
        public void unloadChunk(Chunk chunk) {
            delegate.unloadChunk(chunk);
        }
    }
}
