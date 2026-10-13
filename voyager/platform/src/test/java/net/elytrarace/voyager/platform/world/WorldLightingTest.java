package net.elytrarace.voyager.platform.world;

import net.kyori.adventure.nbt.ByteArrayBinaryTag;
import net.kyori.adventure.nbt.BinaryTag;
import net.kyori.adventure.nbt.BinaryTagIO;
import net.kyori.adventure.nbt.BinaryTagTypes;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.instance.Section;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.light.Light;
import net.minestom.server.network.ConnectionState;
import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.play.ChunkDataPacket;
import net.minestom.server.world.DimensionType;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import net.onelitefeather.falco.anvil.ChunkCompression;
import net.onelitefeather.falco.anvil.FalcoAnvilLoader;
import net.onelitefeather.falco.anvil.RegionFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What {@link MapInstances} does with the light a chunk was stored with.
 *
 * <p>Every world here is written through Falco into a temporary directory, and the light arrays are written into the
 * stored NBT afterwards, so a test controls exactly which sections carry a {@code SkyLight} or {@code BlockLight}
 * array. A glowstone at chunk-local (1, 1, 1) gives the relight a block whose light is known: 15.
 */
@EnvTest
class WorldLightingTest {

    private static final String WORLD = "goldrush";
    private static final int CHUNK_X = 0;
    private static final int CHUNK_Z = 0;
    /** Section index of the block y 0..15, which is the first section of the overworld. */
    private static final int SECTION = 0;
    private static final int GLOWSTONE_X = 1;
    private static final int GLOWSTONE_Y = 1;
    private static final int GLOWSTONE_Z = 1;
    private static final int GLOWSTONE_LIGHT = 15;
    private static final int LIGHT_LENGTH = 2048;

    @TempDir
    Path tempDir;

    private Path worldsRoot() {
        return tempDir.resolve("worlds");
    }

    @Test
    void aLoadedChunkKeepsItsStoredBlockLight(Env env) throws Exception {
        byte[] stored = filled((byte) 0x77);
        writeWorld(env);
        storeLight(WORLD, "BlockLight", stored);

        try (MapInstances instances = new MapInstances(env.process().instance(), worldsRoot())) {
            Light light = loadSection(instances.forWorld(WORLD)).blockLight();

            assertThat(light.requiresUpdate())
                    .as("a block light that was stored is not marked for relight")
                    .isFalse();
            assertThat(light.array())
                    .as("the stored block light is the one the region file holds")
                    .isEqualTo(stored);
        }
    }

    @Test
    void aSectionWithoutStoredLightIsMarkedForRelight(Env env) throws Exception {
        writeWorld(env);

        try (MapInstances instances = new MapInstances(env.process().instance(), worldsRoot())) {
            Chunk chunk = instances.forWorld(WORLD).loadChunk(CHUNK_X, CHUNK_Z).join();

            assertThat(chunk.getSection(SECTION).blockLight().requiresUpdate())
                    .as("a section with no stored block light is marked for relight")
                    .isTrue();
            assertThat(chunk.getSection(SECTION).skyLight().requiresUpdate())
                    .as("a section with no stored sky light is marked for relight")
                    .isTrue();
        }
    }

    @Test
    void aSectionWithAnAllZeroStoredArrayIsNotMarked(Env env) throws Exception {
        // Minestom reads an all-zero array back as empty, so an array length cannot tell it from an absent one.
        writeWorld(env);
        storeLight(WORLD, "BlockLight", new byte[LIGHT_LENGTH]);

        try (MapInstances instances = new MapInstances(env.process().instance(), worldsRoot())) {
            Light light = loadSection(instances.forWorld(WORLD)).blockLight();

            assertThat(light.requiresUpdate())
                    .as("an all-zero stored block light is stored light, so it is not marked for relight")
                    .isFalse();
        }
    }

    @Test
    void aSectionWithStoredSkyLightIsMarkedOnlyForBlockLight(Env env) throws Exception {
        writeWorld(env);
        storeLight(WORLD, "SkyLight", filled((byte) 0xFF));

        try (MapInstances instances = new MapInstances(env.process().instance(), worldsRoot())) {
            Section section = loadSection(instances.forWorld(WORLD));

            assertThat(section.skyLight().requiresUpdate())
                    .as("the stored sky light is kept")
                    .isFalse();
            assertThat(section.blockLight().requiresUpdate())
                    .as("the block light, which has no stored array, is marked on its own")
                    .isTrue();
        }
    }

    @Test
    void loadingAChunkWithoutSendingItComputesNoLight(Env env) throws Exception {
        // The configuration check reads chunks this way: it loads them and never sends one, so no light is computed.
        writeWorld(env);

        try (MapInstances instances = new MapInstances(env.process().instance(), worldsRoot())) {
            Section section = loadSection(instances.forWorld(WORLD));

            assertThat(section.blockLight().requiresUpdate())
                    .as("a loaded chunk that was never sent still needs its block light, so no relight ran")
                    .isTrue();
        }
    }

    @Test
    void aViewerSentALightLessChunkReceivesComputedBlockLight(Env env) throws Exception {
        writeWorld(env);

        try (MapInstances instances = new MapInstances(env.process().instance(), worldsRoot())) {
            Chunk chunk = instances.forWorld(WORLD).loadChunk(CHUNK_X, CHUNK_Z).join();
            ChunkDataPacket sent = fullDataPacket(chunk);

            assertThat(sent.lightData().blockMask().isEmpty())
                    .as("the chunk packet carries block light for the section that had none stored")
                    .isFalse();
            assertThat(chunk.getSection(SECTION).blockLight().getLevel(GLOWSTONE_X, GLOWSTONE_Y, GLOWSTONE_Z))
                    .as("the glowstone in the section lights its own block")
                    .isEqualTo(GLOWSTONE_LIGHT);
        }
    }

    @Test
    void aRelitChunkLeavesTheRegionFileByteIdentical(Env env) throws Exception {
        writeWorld(env);
        Path region = regionFile();
        byte[] before = Files.readAllBytes(region);

        try (MapInstances instances = new MapInstances(env.process().instance(), worldsRoot())) {
            Chunk chunk = instances.forWorld(WORLD).loadChunk(CHUNK_X, CHUNK_Z).join();
            fullDataPacket(chunk);
        }

        assertThat(Files.readAllBytes(region))
                .as("computed light is never written back to the region file")
                .isEqualTo(before);
    }

    private static ChunkDataPacket fullDataPacket(Chunk chunk) {
        ServerPacket packet = SendablePacket.extractServerPacket(ConnectionState.PLAY, chunk.getFullDataPacket());
        assertThat(packet).isInstanceOf(ChunkDataPacket.class);
        return (ChunkDataPacket) packet;
    }

    private static Section loadSection(Instance instance) {
        Chunk chunk = instance.loadChunk(CHUNK_X, CHUNK_Z).join();
        return chunk.getSection(SECTION);
    }

    private static byte[] filled(byte value) {
        byte[] array = new byte[LIGHT_LENGTH];
        Arrays.fill(array, value);
        return array;
    }

    private Path regionFile() {
        return worldsRoot().resolve(WORLD)
                .resolve("dimensions").resolve("minecraft").resolve("overworld").resolve("region")
                .resolve("r.0.0.mca");
    }

    /** Writes a world with one saved chunk, which holds a glowstone and no stored light. */
    private void writeWorld(Env env) throws Exception {
        InstanceManager instanceManager = env.process().instance();
        FalcoAnvilLoader writer = FalcoAnvilLoader.builder()
                .build(worldsRoot().resolve(WORLD), DimensionType.OVERWORLD.key());
        InstanceContainer instance = instanceManager.createInstanceContainer(DimensionType.OVERWORLD, writer);

        try {
            instance.loadChunk(CHUNK_X, CHUNK_Z).join();
            instance.setBlock(GLOWSTONE_X, GLOWSTONE_Y, GLOWSTONE_Z, Block.GLOWSTONE);
            instance.saveChunksToStorage().join();
        } finally {
            instanceManager.unregisterInstance(instance);
            writer.close();
        }
    }

    /** Replaces one light array of the saved chunk's section 0, through the region file, as {@code patchStoredChunk} does. */
    private void storeLight(String world, String key, byte[] light) throws Exception {
        patchStoredChunk(world, child -> {
            ListBinaryTag.Builder<CompoundBinaryTag> sections = ListBinaryTag.builder(BinaryTagTypes.COMPOUND);
            for (BinaryTag rawSection : child.getList("sections")) {
                CompoundBinaryTag section = (CompoundBinaryTag) rawSection;
                sections.add(section.getByte("Y") == SECTION
                        ? section.put(key, ByteArrayBinaryTag.byteArrayBinaryTag(light))
                        : section);
            }
            return child.put("sections", sections.build());
        });
    }

    /**
     * Rewrites the stored NBT of the saved chunk through Falco's own region file, so the test does not re-implement the
     * format it is testing against.
     */
    private void patchStoredChunk(String world, UnaryOperator<CompoundBinaryTag> change) throws Exception {
        Path regionFile = worldsRoot().resolve(world)
                .resolve("dimensions").resolve("minecraft").resolve("overworld").resolve("region")
                .resolve("r.0.0.mca");
        try (RegionFile region = RegionFile.open(regionFile)) {
            RegionFile.RawChunk raw = region.readRaw(CHUNK_X, CHUNK_Z);
            CompoundBinaryTag stored = BinaryTagIO.unlimitedReader()
                    .read(new ByteArrayInputStream(raw.decompress()), BinaryTagIO.Compression.NONE);
            ByteArrayOutputStream patched = new ByteArrayOutputStream();
            BinaryTagIO.writer().writeNamed(Map.entry("", change.apply(stored)), patched,
                    BinaryTagIO.Compression.NONE);
            region.writeRaw(CHUNK_X, CHUNK_Z, ChunkCompression.ZLIB,
                    ChunkCompression.ZLIB.compress(patched.toByteArray()));
        }
    }
}
