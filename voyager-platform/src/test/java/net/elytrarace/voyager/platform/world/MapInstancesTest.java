package net.elytrarace.voyager.platform.world;

import net.elytrarace.voyager.platform.world.exception.UnknownWorldException;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.instance.block.Block;
import net.minestom.server.world.DimensionType;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import net.onelitefeather.falco.anvil.AnvilDiagnostics;
import net.onelitefeather.falco.anvil.FalcoAnvilLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnvTest
class MapInstancesTest {

    private static final String TRACK = "goldrush";
    private static final String OTHER_TRACK = "bluecanyon";

    /**
     * Three blocks, each a different type, in three different chunks and across two region files.
     * Every coordinate differs from every other on every axis, and the third one is negative on
     * purpose: above zero a chunk index computed by a shift and one computed by a truncating divide
     * agree, so a round trip that only ever wrote into {@code r.0.0.mca} could not tell a correct
     * region index from a wrong one.
     */
    private static final List<PlacedBlock> TRACK_BLOCKS = List.of(
            new PlacedBlock(3, 64, 5, Block.GOLD_BLOCK),
            new PlacedBlock(43, -12, 71, Block.DIAMOND_BLOCK),
            new PlacedBlock(-7, 100, -3, Block.EMERALD_BLOCK));

    /**
     * The second world puts a different block at the first world's first coordinate on purpose: two
     * instances being distinct objects does not yet prove they are reading different region files,
     * and a cache that handed out the wrong entry would still satisfy an identity assertion.
     */
    private static final List<PlacedBlock> OTHER_TRACK_BLOCKS = List.of(
            new PlacedBlock(3, 64, 5, Block.REDSTONE_BLOCK));

    @TempDir
    Path tempDir;

    private Path worldsRoot() {
        return tempDir.resolve("worlds");
    }

    @Test
    void readsBackEveryBlockAWorldWasSavedWith(Env env) throws IOException {
        writeWorld(env, TRACK, TRACK_BLOCKS);

        try (MapInstances instances = new MapInstances(env.process().instance(), worldsRoot())) {
            Instance instance = instances.forWorld(TRACK);

            assertBlocks(instance, TRACK_BLOCKS);
        }
    }

    @Test
    void returnsTheSameInstanceForTheSameWorldName(Env env) throws IOException {
        // A correctness requirement rather than an optimisation: one world backs more than one map,
        // and two loaders over one set of region files is a bug.
        writeWorld(env, TRACK, TRACK_BLOCKS);

        try (MapInstances instances = new MapInstances(env.process().instance(), worldsRoot())) {
            Instance first = instances.forWorld(TRACK);
            Instance second = instances.forWorld(TRACK);

            assertThat(first).isSameAs(second);
            assertThat(instances.diagnosticsFor(TRACK)).isSameAs(instances.diagnosticsFor(TRACK));
        }
    }

    @Test
    void returnsADistinctInstanceOverItsOwnRegionFilesForEachWorldName(Env env) throws IOException {
        writeWorld(env, TRACK, TRACK_BLOCKS);
        writeWorld(env, OTHER_TRACK, OTHER_TRACK_BLOCKS);

        try (MapInstances instances = new MapInstances(env.process().instance(), worldsRoot())) {
            Instance track = instances.forWorld(TRACK);
            Instance otherTrack = instances.forWorld(OTHER_TRACK);

            assertThat(track).isNotSameAs(otherTrack);
            assertBlocks(track, TRACK_BLOCKS);
            assertBlocks(otherTrack, OTHER_TRACK_BLOCKS);
        }
    }

    @Test
    void throwsNamingTheWorldWhenTheDirectoryDoesNotExist(Env env) {
        try (MapInstances instances = new MapInstances(env.process().instance(), worldsRoot())) {
            assertThatThrownBy(() -> instances.forWorld(TRACK))
                    .isInstanceOf(UnknownWorldException.class)
                    .hasMessageContaining(TRACK)
                    .hasMessageContaining("holds no region data");
        }
    }

    @Test
    void throwsWhenTheDirectoryExistsButHoldsNoRegionFile(Env env) throws IOException {
        // Separate from the case above because this is the one a half-copied world produces, and a
        // check that only tested for the directory's existence would wave it through as a world.
        Path regionDirectory = worldsRoot().resolve(TRACK)
                .resolve("dimensions").resolve("minecraft").resolve("overworld").resolve("region");
        Files.createDirectories(regionDirectory);
        Files.writeString(regionDirectory.resolve("readme.txt"), "not a region file");

        try (MapInstances instances = new MapInstances(env.process().instance(), worldsRoot())) {
            assertThatThrownBy(() -> instances.forWorld(TRACK))
                    .isInstanceOf(UnknownWorldException.class)
                    .hasMessageContaining(TRACK);
        }
    }

    @Test
    void closeUnregistersEveryInstanceItCreated(Env env) throws IOException {
        writeWorld(env, TRACK, TRACK_BLOCKS);
        writeWorld(env, OTHER_TRACK, OTHER_TRACK_BLOCKS);
        InstanceManager instanceManager = env.process().instance();
        MapInstances instances = new MapInstances(instanceManager, worldsRoot());
        Instance track = instances.forWorld(TRACK);
        Instance otherTrack = instances.forWorld(OTHER_TRACK);

        instances.close();

        assertThat(instanceManager.getInstances()).doesNotContain(track, otherTrack);
    }

    @Test
    void closeClosesEveryLoaderItOpened(Env env) throws IOException {
        writeWorld(env, TRACK, TRACK_BLOCKS);
        MapInstances instances = new MapInstances(env.process().instance(), worldsRoot());
        Instance instance = instances.forWorld(TRACK);
        // Held across the close because close() clears the cache: nothing on the public surface can
        // still name the loader afterwards, which is why loaderFor exists.
        FalcoAnvilLoader loader = instances.loaderFor(TRACK);

        instances.close();

        assertThatThrownBy(() -> loader.loadChunk(instance, 0, 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("is closed");
    }

    @Test
    void healthReportsWhatTheLoaderActuallyRead(Env env) throws IOException {
        writeWorld(env, TRACK, TRACK_BLOCKS);

        try (MapInstances instances = new MapInstances(env.process().instance(), worldsRoot())) {
            Instance instance = instances.forWorld(TRACK);
            assertThat(instances.healthOf(TRACK).chunksLoaded())
                    .describedAs("nothing has been asked for yet")
                    .isZero();
            assertThat(instances.healthOf(TRACK).isSound()).isFalse();

            loadChunksOf(instance, TRACK_BLOCKS);
            WorldHealth health = instances.healthOf(TRACK);

            // Three blocks in three distinct chunks, all of which exist on disk: every one is a read
            // rather than a miss, so a report that counted misses as reads would not show three.
            assertThat(health.world()).isEqualTo(TRACK);
            assertThat(health.chunksLoaded()).isEqualTo(TRACK_BLOCKS.size());
            assertThat(health.chunksSkipped()).isZero();
            assertThat(health.unknownBlocks()).isZero();
            assertThat(health.errors()).isZero();
            assertThat(health.isSound()).isTrue();
        }
    }

    @Test
    void healthCountsChunksAskedForThatNoDataBacksForEitherReason(Env env) throws IOException {
        writeWorld(env, TRACK, TRACK_BLOCKS);

        try (MapInstances instances = new MapInstances(env.process().instance(), worldsRoot())) {
            Instance instance = instances.forWorld(TRACK);
            // Two chunks far outside anything that was ever written, so no region file exists for
            // them at all. This is what flying off the edge of a finite racetrack does, and it has
            // to be counted without being called a failure.
            instance.loadChunk(600, -400).join();
            instance.loadChunk(-600, 400).join();
            // And one chunk inside r.0.0.mca, which the write above created, but which was never
            // written into it. Falco counts that separately from a missing file on purpose: one
            // says the loader is reading the wrong directory, the other says the world has holes.
            // Two against one rather than one each, so a report that added the same counter twice
            // reads four and a report that dropped either term reads two.
            instance.loadChunk(20, 20).join();
            AnvilDiagnostics diagnostics = instances.diagnosticsFor(TRACK);
            WorldHealth health = instances.healthOf(TRACK);

            assertThat(diagnostics.chunksSkippedWithoutRegionFile()).isEqualTo(2L);
            assertThat(diagnostics.chunksSkippedWithoutEntry()).isEqualTo(1L);
            assertThat(health.chunksSkipped()).isEqualTo(3L);
            assertThat(health.chunksLoaded()).isZero();
        }
    }

    @Test
    void diagnosticsForHandsOutTheLoadersOwnCounters(Env env) throws IOException {
        writeWorld(env, TRACK, TRACK_BLOCKS);

        try (MapInstances instances = new MapInstances(env.process().instance(), worldsRoot())) {
            Instance instance = instances.forWorld(TRACK);
            AnvilDiagnostics diagnostics = instances.diagnosticsFor(TRACK);
            assertThat(diagnostics.chunksLoaded()).isZero();

            loadChunksOf(instance, TRACK_BLOCKS);

            // The same object the loader writes into, not a copy taken when it was handed over.
            assertThat(diagnostics.chunksLoaded()).isEqualTo(TRACK_BLOCKS.size());
        }
    }

    @Test
    void reportReadsEachCounterFromTheOneFalcoKeepsItIn() {
        // Every count is different from every other one, so a report that read the wrong counter, or
        // the same counter twice, produces a different number somewhere. Partially generated chunks
        // are the reason this goes through hand-filled diagnostics rather than a real world: nothing
        // a loader can be made to read off disk here produces one, and without a case that has them
        // Falco's own chunksSkipped() and the two-term sum below are indistinguishable.
        AnvilDiagnostics diagnostics = new AnvilDiagnostics();
        for (int i = 0; i < 7; i++) {
            diagnostics.countChunkLoaded();
        }
        for (int i = 0; i < 2; i++) {
            diagnostics.reportMissingRegionFile();
        }
        diagnostics.reportMissingChunkEntry();
        for (int i = 0; i < 5; i++) {
            diagnostics.reportPartialChunk("minecraft:surface");
        }
        for (String name : List.of("voyager:ring_marker", "voyager:boost_pad", "voyager:banner_a", "voyager:banner_b")) {
            diagnostics.reportUnknownBlock(name);
        }
        for (int i = 0; i < 6; i++) {
            diagnostics.countError();
        }

        WorldHealth health = MapInstances.report(TRACK, diagnostics, new AtomicBoolean());

        assertThat(health.world()).isEqualTo(TRACK);
        assertThat(health.chunksLoaded()).isEqualTo(7L);
        // 2 + 1, not the 8 that Falco's own chunksSkipped() would report by counting the five
        // partially generated chunks as missing data too.
        assertThat(health.chunksSkipped()).isEqualTo(3L);
        assertThat(health.unknownBlocks()).isEqualTo(4);
        assertThat(health.errors()).isEqualTo(6L);
    }

    @Test
    void reportWarnsAboutUnknownBlockNamesOnceAndOnlyWhenThereAreAny() {
        // The warning is the whole of what the default unknown-block policy leaves behind: an
        // unknown name becomes air, the chunk loads, and the hole in the racetrack is otherwise
        // silent. The flag is the only part of a log line a test can read, so it is what pins it.
        AnvilDiagnostics clean = new AnvilDiagnostics();
        clean.countChunkLoaded();
        AtomicBoolean cleanReported = new AtomicBoolean();

        MapInstances.report(TRACK, clean, cleanReported);

        assertThat(cleanReported).isFalse();

        AnvilDiagnostics withUnknownBlocks = new AnvilDiagnostics();
        withUnknownBlocks.countChunkLoaded();
        withUnknownBlocks.reportUnknownBlock("voyager:ring_marker");
        AtomicBoolean reported = new AtomicBoolean();

        MapInstances.report(TRACK, withUnknownBlocks, reported);

        assertThat(reported).isTrue();

        // Second call, same flag: the count only grows, so warning again on every call would turn a
        // report into noise. The flag staying set is what says the guard is still doing that.
        withUnknownBlocks.reportUnknownBlock("voyager:boost_pad");
        MapInstances.report(TRACK, withUnknownBlocks, reported);

        assertThat(reported).isTrue();
    }

    /**
     * Writes a world to disk through a Falco loader of its own, so the round trip proves the format
     * rather than a fixture that was committed alongside the code that reads it.
     */
    private void writeWorld(Env env, String world, List<PlacedBlock> blocks) throws IOException {
        InstanceManager instanceManager = env.process().instance();
        FalcoAnvilLoader writer = FalcoAnvilLoader.builder()
                .build(worldsRoot().resolve(world), DimensionType.OVERWORLD.key());
        InstanceContainer instance = instanceManager.createInstanceContainer(DimensionType.OVERWORLD, writer);

        try {
            for (PlacedBlock placed : blocks) {
                instance.loadChunk(placed.chunkX(), placed.chunkZ()).join();
                instance.setBlock(placed.x(), placed.y(), placed.z(), placed.block());
            }
            instance.saveChunksToStorage().join();
        } finally {
            instanceManager.unregisterInstance(instance);
            writer.close();
        }
    }

    private static void loadChunksOf(Instance instance, List<PlacedBlock> blocks) {
        for (PlacedBlock placed : blocks) {
            instance.loadChunk(placed.chunkX(), placed.chunkZ()).join();
        }
    }

    private static void assertBlocks(Instance instance, List<PlacedBlock> blocks) {
        loadChunksOf(instance, blocks);
        for (PlacedBlock placed : blocks) {
            assertThat(instance.getBlock(placed.x(), placed.y(), placed.z()))
                    .describedAs("block at (%d, %d, %d)", placed.x(), placed.y(), placed.z())
                    .isEqualTo(placed.block());
        }
    }

    private record PlacedBlock(int x, int y, int z, Block block) {

        int chunkX() {
            return x >> 4;
        }

        int chunkZ() {
            return z >> 4;
        }
    }
}
