package net.elytrarace.voyager.platform.world;

import net.kyori.adventure.nbt.BinaryTagIO;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.StringBinaryTag;

import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

/**
 * Writes tiny region files and {@code level.dat} files for the tests. Every file is built in memory and written into a
 * test's own temporary folder; no fixture reads a real world.
 */
public final class WorldFixtures {

    private static final int SECTOR = 4096;
    private static final int HEADER = 8192;

    private WorldFixtures() {
    }

    /**
     * One chunk record: the four-byte length, the compression byte and the data.
     *
     * @param compression the compression byte, 1 gzip, 2 zlib, 3 none
     * @param data        the bytes after the header
     * @return the record as a region file holds it
     */
    public static byte[] record(int compression, byte[] data) {
        return ByteBuffer.allocate(5 + data.length).putInt(data.length + 1).put((byte) compression).put(data).array();
    }

    /**
     * A zlib-compressed chunk record of the given chunk root.
     *
     * @param root the chunk's root compound
     * @return the record
     */
    public static byte[] chunk(CompoundBinaryTag root) {
        return record(2, compress(root, BinaryTagIO.Compression.ZLIB));
    }

    /**
     * A record whose data is not a valid compressed stream, so it cannot be read.
     *
     * @return the record
     */
    public static byte[] unreadableChunk() {
        return record(2, "not a zlib stream".getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * The root of a chunk record: a status, when one is given, and a data version.
     *
     * @param status      the status, such as {@code full}, or {@code null} for a chunk that states none
     * @param dataVersion the data version
     * @return the root compound
     */
    public static CompoundBinaryTag chunkTag(@Nullable String status, int dataVersion) {
        CompoundBinaryTag.Builder builder = CompoundBinaryTag.builder().putInt("DataVersion", dataVersion);
        if (status != null) {
            builder.put("Status", StringBinaryTag.stringBinaryTag(status));
        }
        return builder.build();
    }

    /**
     * Writes a region file. Each record goes into the slot of its index, {@code z * 32 + x} of the chunk in the region.
     *
     * @param file    the file to write
     * @param records the records by slot index
     * @throws IOException if the file cannot be written
     */
    public static void writeRegion(Path file, Map<Integer, byte[]> records) throws IOException {
        byte[] header = new byte[HEADER];
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        int nextSector = 2;
        for (Map.Entry<Integer, byte[]> entry : new TreeMap<>(records).entrySet()) {
            byte[] record = entry.getValue();
            int sectors = (record.length + SECTOR - 1) / SECTOR;
            ByteBuffer.wrap(header).putInt(entry.getKey() * 4, (nextSector << 8) | sectors);
            byte[] padded = new byte[sectors * SECTOR];
            System.arraycopy(record, 0, padded, 0, record.length);
            body.write(padded);
            nextSector += sectors;
        }
        Files.createDirectories(file.getParent());
        ByteArrayOutputStream whole = new ByteArrayOutputStream();
        whole.write(header);
        body.writeTo(whole);
        Files.write(file, whole.toByteArray());
    }

    /**
     * Writes a {@code level.dat}: a gzip NBT file whose root holds the {@code Data} compound.
     *
     * @param file the file to write
     * @param data the content of {@code Data}
     * @throws IOException if the file cannot be written
     */
    public static void writeLevelDat(Path file, CompoundBinaryTag data) throws IOException {
        CompoundBinaryTag root = CompoundBinaryTag.builder().put("Data", data).build();
        Files.createDirectories(file.getParent());
        try (OutputStream out = Files.newOutputStream(file)) {
            BinaryTagIO.writer().write(root, out, BinaryTagIO.Compression.GZIP);
        }
    }

    private static byte[] compress(CompoundBinaryTag root, BinaryTagIO.Compression compression) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            BinaryTagIO.writer().write(root, out, compression);
            return out.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("the fixture could not be compressed", exception);
        }
    }
}
