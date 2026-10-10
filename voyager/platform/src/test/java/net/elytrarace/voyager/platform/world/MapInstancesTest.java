package net.elytrarace.voyager.platform.world;

import net.elytrarace.voyager.platform.world.exception.UnknownWorldException;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.instance.block.Block;
import net.minestom.server.world.DimensionType;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import net.kyori.adventure.nbt.BinaryTag;
import net.kyori.adventure.nbt.BinaryTagIO;
import net.kyori.adventure.nbt.BinaryTagTypes;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;
import net.minestom.server.coordinate.Pos;
import net.onelitefeather.falco.anvil.AnvilDiagnostics;
import net.onelitefeather.falco.anvil.ChunkCompression;
import net.onelitefeather.falco.anvil.FalcoAnvilLoader;
import net.onelitefeather.falco.anvil.RegionFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnvTest
class MapInstancesTest {

    private static final String TRACK = "goldrush";
    private static final String OTHER_TRACK = "bluecanyon";

    /**
     * Below both the migrator's floor and the loader's minimum, so the chunk is neither translated
     * nor read: the version policy refuses it. A version between the two would be migrated up and
     * never refused at all.
     */
    private static final int UNREADABLE_DATA_VERSION = 1400;

    /** A name no Minecraft version ever had, so the running server cannot resolve it. */
    private static final String UNKNOWN_BLOCK_NAME = "voyager:missing_ring_marker";

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
            // A racetrack is flown across, so chunks have to arrive as the player reaches them.
            // Minestom already defaults this to true, so the assertion catches an edit that turns it
            // off rather than one that drops the call - which is the edit worth catching.
            assertThat(instance.hasEnabledAutoChunkLoad()).isTrue();
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
    void throwsNamingTheWorldThePathAndTheLayoutWhenTheDirectoryDoesNotExist(Env env) {
        // The message is the entire product of this class, so all three of its parts are asserted.
        // The path in particular: the loader resolves it rather than being given it, so "looked in
        // the wrong place" and "the place is empty" are different reports and only the path tells
        // them apart. Nothing exists under the world root here, so the dimension layout is taken.
        Path expectedRegionDirectory = worldsRoot().resolve(TRACK)
                .resolve("dimensions").resolve("minecraft").resolve("overworld").resolve("region");

        try (MapInstances instances = new MapInstances(env.process().instance(), worldsRoot())) {
            assertThatThrownBy(() -> instances.forWorld(TRACK))
                    .isInstanceOf(UnknownWorldException.class)
                    .hasMessage("world '%s' holds no region data; the loader resolved %s using the dimension layout",
                            TRACK, expectedRegionDirectory);
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
                    .hasMessage("world '%s' holds no region data; the loader resolved %s using the dimension layout",
                            TRACK, regionDirectory);
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

            // A literal, not TRACK_BLOCKS.size(): the two agree only because each fixture block
            // happens to sit in its own chunk, and a suite that leaned on that coincidence could not
            // tell a count of chunks from a count of blocks. The three are (0,0), (2,4) and (-1,-1).
            assertThat(health.world()).isEqualTo(TRACK);
            assertThat(health.chunksLoaded()).isEqualTo(3L);
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

    /**
     * The deep check reads every chunk the region files cover, so a world nothing has asked for yet
     * reports as read after it, without the test naming a chunk. The fixture writes three chunks in
     * two region files; the count is the three chunks with data, not the 2048 the two files span.
     */
    @Test
    void readEveryChunkReadsEveryChunkTheRegionFilesCover(Env env) throws IOException {
        writeWorld(env, TRACK, TRACK_BLOCKS);

        try (MapInstances instances = new MapInstances(env.process().instance(), worldsRoot())) {
            instances.forWorld(TRACK);
            assertThat(instances.healthOf(TRACK).isSound()).isFalse();

            instances.readEveryChunk(TRACK);
            WorldHealth health = instances.healthOf(TRACK);

            assertThat(health.chunksLoaded()).isEqualTo(3L);
            assertThat(health.isSound()).isTrue();
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
            // A literal for the same reason as in healthReportsWhatTheLoaderActuallyRead.
            assertThat(diagnostics.chunksLoaded()).isEqualTo(3L);
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

    @ParameterizedTest(name = "status \"{0}\"")
    @ValueSource(strings = {"full", "minecraft:full"})
    void readsAFullyGeneratedChunkUnderEitherFormOfItsStatus(String status, Env env) throws IOException {
        // Minecraft namespaced the chunk status in 1.20.2, so a world older than that stores the
        // bare "full". Falco 2.x compared the stored value to the literal "minecraft:full" and
        // reported every such chunk as not fully generated — 3464 of the shipped world's 9429
        // chunks, silently returned as air. 3.0.0 parses it as a key, so the bare form takes the
        // default namespace. Both forms are exercised rather than only the odd one, so the test
        // says "either form reads" instead of leaving the ordinary case to a round trip that can
        // only ever produce the namespaced one.
        PlacedBlock block = TRACK_BLOCKS.getFirst();
        writeWorld(env, TRACK, List.of(block));
        // Only the status is touched. The stored DataVersion is left exactly as written, so nothing
        // but the status can be what makes this chunk readable or not.
        patchStoredChunk(TRACK, block, chunk -> chunk.putString("Status", status));

        try (MapInstances instances = new MapInstances(env.process().instance(), worldsRoot())) {
            Instance instance = instances.forWorld(TRACK);

            assertBlocks(instance, List.of(block));

            WorldHealth health = instances.healthOf(TRACK);
            assertThat(health.chunksLoaded()).isEqualTo(1L);
            assertThat(health.isSound()).isTrue();
            // The counter that carried the defect: a rejected status shows up here and nowhere else.
            assertThat(instances.diagnosticsFor(TRACK).chunksSkippedAsPartial()).isZero();
        }
    }

    @Test
    void reportsChunksTheVersionPolicyRefusedWithTheVersionTheyCameFrom(Env env) throws IOException {
        PlacedBlock block = TRACK_BLOCKS.getFirst();
        writeWorld(env, TRACK, List.of(block));
        patchStoredChunk(TRACK, block, MapInstancesTest::agedBeyondWhatTheLoaderReads);

        try (MapInstances instances = new MapInstances(env.process().instance(), worldsRoot())) {
            Instance instance = instances.forWorld(TRACK);
            try {
                instance.loadChunk(block.chunkX(), block.chunkZ()).join();
            } catch (RuntimeException expected) {
                // The refusal is the subject. Falco propagates it rather than reporting the chunk as
                // absent, precisely so the server cannot generate a replacement over the real one.
            }

            WorldHealth health = instances.healthOf(TRACK);

            assertThat(health.chunksRefused()).isEqualTo(1L);
            assertThat(health.refusedVersions()).containsExactly(Map.entry("1400", 1L));
            assertThat(health.isSound()).isFalse();
            assertThat(health.describe()).contains("1 chunk(s) refused for their data version (1400 x 1)");
        }
    }

    @Test
    void warnsOncePerWorldAboutBlockNamesTheServerDoesNotKnow(Env env) throws IOException {
        // The default policy turns an unknown name into air, which is a hole in the course that
        // nothing else reports. Both worlds get one, so the latch can be shown to be per world.
        PlacedBlock block = TRACK_BLOCKS.getFirst();
        writeWorld(env, TRACK, List.of(block));
        writeWorld(env, OTHER_TRACK, List.of(block));
        patchStoredChunk(TRACK, block, MapInstancesTest::withAnUnknownBlockName);
        patchStoredChunk(OTHER_TRACK, block, MapInstancesTest::withAnUnknownBlockName);

        try (MapInstances instances = new MapInstances(env.process().instance(), worldsRoot())) {
            Instance track = instances.forWorld(TRACK);
            track.loadChunk(block.chunkX(), block.chunkZ()).join();

            assertThat(instances.hasWarnedAboutUnknownBlocks(TRACK))
                    .describedAs("nothing has asked for a report yet")
                    .isFalse();

            WorldHealth health = instances.healthOf(TRACK);

            assertThat(health.unknownBlocks()).isEqualTo(1);
            assertThat(health.isSound()).isFalse();
            assertThat(track.getBlock(block.x(), block.y(), block.z()))
                    .describedAs("the unknown name became air, which is the hole being warned about")
                    .isEqualTo(Block.AIR);
            assertThat(instances.hasWarnedAboutUnknownBlocks(TRACK)).isTrue();
            // The second world has an unknown block of its own and has not been reported on, so a
            // latch shared between worlds would already read true here.
            assertThat(instances.hasWarnedAboutUnknownBlocks(OTHER_TRACK))
                    .describedAs("the latch belongs to a world, not to the loader set")
                    .isFalse();

            Instance otherTrack = instances.forWorld(OTHER_TRACK);
            otherTrack.loadChunk(block.chunkX(), block.chunkZ()).join();

            assertThat(instances.healthOf(OTHER_TRACK).unknownBlocks()).isEqualTo(1);
            assertThat(instances.hasWarnedAboutUnknownBlocks(OTHER_TRACK)).isTrue();
            assertThat(instances.hasWarnedAboutUnknownBlocks(TRACK))
                    .describedAs("still set, and set only once")
                    .isTrue();
        }
    }

    @Test
    void closeClosesEveryLoaderEvenWhenOneWorldRefusesToBeUnregistered(Env env) throws IOException {
        // InstanceManager refuses an instance that still has a player in it, and a shutdown is
        // exactly when one still does. A close that stopped there would leave every loader after it
        // in iteration order holding its region files open.
        writeWorld(env, TRACK, TRACK_BLOCKS);
        writeWorld(env, OTHER_TRACK, OTHER_TRACK_BLOCKS);
        MapInstances instances = new MapInstances(env.process().instance(), worldsRoot());
        Instance track = instances.forWorld(TRACK);
        Instance otherTrack = instances.forWorld(OTHER_TRACK);
        FalcoAnvilLoader trackLoader = instances.loaderFor(TRACK);
        FalcoAnvilLoader otherTrackLoader = instances.loaderFor(OTHER_TRACK);
        track.loadChunk(0, 0).join();
        env.createPlayer(track, new Pos(3, 65, 5));

        assertThatThrownBy(instances::close).isInstanceOf(IllegalStateException.class);

        assertThat(catchThrowable(() -> trackLoader.loadChunk(track, 0, 0)))
                .describedAs("the loader of the world that refused to unregister")
                .isInstanceOf(IllegalStateException.class);
        assertThat(catchThrowable(() -> otherTrackLoader.loadChunk(otherTrack, 0, 0)))
                .describedAs("the loader of every world after it")
                .isInstanceOf(IllegalStateException.class);
        // Cleared too, so a later forWorld builds afresh instead of handing out a closed loader.
        assertThat(instances.forWorld(OTHER_TRACK)).isNotSameAs(otherTrack);
    }

    /**
     * Rewrites the stored NBT of the one chunk a block sits in, through Falco's own region file so
     * the test does not re-implement the format it is testing against.
     */
    private void patchStoredChunk(String world, PlacedBlock block, UnaryOperator<CompoundBinaryTag> change)
            throws IOException {
        Path regionFile = worldsRoot().resolve(world)
                .resolve("dimensions").resolve("minecraft").resolve("overworld").resolve("region")
                .resolve("r.%d.%d.mca".formatted(block.chunkX() >> 5, block.chunkZ() >> 5));

        try (RegionFile region = RegionFile.open(regionFile)) {
            RegionFile.RawChunk raw = region.readRaw(block.chunkX(), block.chunkZ());
            assertThat(raw).describedAs("the chunk to patch has to be on disk already").isNotNull();
            CompoundBinaryTag stored = BinaryTagIO.unlimitedReader()
                    .read(new ByteArrayInputStream(raw.decompress()), BinaryTagIO.Compression.NONE);
            ByteArrayOutputStream patched = new ByteArrayOutputStream();
            BinaryTagIO.writer().writeNamed(Map.entry("", change.apply(stored)), patched,
                    BinaryTagIO.Compression.NONE);
            region.writeRaw(block.chunkX(), block.chunkZ(), ChunkCompression.ZLIB,
                    ChunkCompression.ZLIB.compress(patched.toByteArray()));
        } catch (Exception exception) {
            throw new IOException("could not patch the stored chunk of world '%s'".formatted(world), exception);
        }
    }

    private static CompoundBinaryTag agedBeyondWhatTheLoaderReads(CompoundBinaryTag chunk) {
        return chunk.putInt("DataVersion", UNREADABLE_DATA_VERSION);
    }

    private static CompoundBinaryTag withAnUnknownBlockName(CompoundBinaryTag chunk) {
        ListBinaryTag.Builder<CompoundBinaryTag> sections = ListBinaryTag.builder(BinaryTagTypes.COMPOUND);
        boolean renamed = false;

        for (BinaryTag rawSection : chunk.getList("sections")) {
            CompoundBinaryTag section = (CompoundBinaryTag) rawSection;

            if (!(section.get("block_states") instanceof CompoundBinaryTag blockStates)) {
                sections.add(section);
                continue;
            }
            ListBinaryTag.Builder<CompoundBinaryTag> palette = ListBinaryTag.builder(BinaryTagTypes.COMPOUND);

            for (BinaryTag rawEntry : blockStates.getList("palette")) {
                CompoundBinaryTag entry = (CompoundBinaryTag) rawEntry;

                if (!"minecraft:air".equals(entry.getString("Name"))) {
                    entry = entry.putString("Name", UNKNOWN_BLOCK_NAME);
                    renamed = true;
                }
                palette.add(entry);
            }
            sections.add(section.put("block_states", blockStates.put("palette", palette.build())));
        }
        if (!renamed) {
            throw new IllegalStateException("the fixture chunk holds nothing but air, so nothing could be renamed");
        }
        return chunk.put("sections", sections.build());
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
