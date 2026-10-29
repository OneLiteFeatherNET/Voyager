package net.elytrarace.voyager.platform.world;

import net.kyori.adventure.nbt.BinaryTagIO;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.IntBinaryTag;
import net.kyori.adventure.nbt.StringBinaryTag;

import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * Counts the chunk statuses of one region file: the full, partial, status-less and unreadable records, and the highest
 * data version a readable chunk states.
 *
 * <p>It reads the region's header and each record itself, because Falco's report leaves out the partially generated
 * chunks that this count must show (see {@code MapInstances#report}). One region is read at a time, so the memory held
 * is one region file.
 */
public final class RegionCensus {

    /** The highest data version this server reads (the greenfield design's setup section). */
    public static final int HIGHEST_DATA_VERSION = 4903;

    /**
     * The largest decoded chunk the census reads. Adventure's default reader stops well below a real chunk (about
     * 160 KB in the team-build worlds), so the bound is set here, high enough for any chunk and low enough to stop a
     * record that inflates without end.
     */
    private static final long MAX_CHUNK_BYTES = 16L * 1024 * 1024;
    private static final BinaryTagIO.Reader CHUNK_READER = BinaryTagIO.reader(MAX_CHUNK_BYTES);

    private static final int SLOTS = 1024;
    private static final int SECTOR = 4096;
    private static final int HEADER = SLOTS * 4 * 2;
    private static final int RECORD_PREFIX = 5;
    private static final int GZIP = 1;
    private static final int ZLIB = 2;
    private static final int UNCOMPRESSED = 3;
    private static final String FULL = "full";
    private static final String NAMESPACED_FULL = "minecraft:full";

    private RegionCensus() {
    }

    /**
     * The counts of one or more region files.
     *
     * @param full              chunks whose status is {@code full} or {@code minecraft:full}
     * @param partial           chunks with any other status
     * @param withoutStatus     chunks that state no status
     * @param unreadable        records that could not be read or decoded
     * @param highestDataVersion the highest data version of a readable chunk, 0 when none states one
     */
    public record Totals(int full, int partial, int withoutStatus, int unreadable, int highestDataVersion) {

        /** Nothing counted. */
        public static final Totals NONE = new Totals(0, 0, 0, 0, 0);

        /** @return every record counted, unreadable ones included */
        public int records() {
            return full + partial + withoutStatus + unreadable;
        }

        /**
         * The sum of two counts; the highest data version is the larger of the two.
         *
         * @param other the counts to add
         * @return the sum
         */
        public Totals plus(Totals other) {
            return new Totals(full + other.full, partial + other.partial, withoutStatus + other.withoutStatus,
                    unreadable + other.unreadable, Math.max(highestDataVersion, other.highestDataVersion));
        }
    }

    /**
     * Counts the records of one region file.
     *
     * @param regionFile the {@code r.X.Z.mca} file to read
     * @return the counts; a file too short for its header counts as one unreadable record
     * @throws UncheckedIOException if the file cannot be read
     */
    public static Totals count(Path regionFile) {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(regionFile);
        } catch (IOException exception) {
            throw new UncheckedIOException("the region file %s cannot be read".formatted(regionFile), exception);
        }
        if (bytes.length < HEADER) {
            return new Totals(0, 0, 0, 1, 0);
        }
        ByteBuffer header = ByteBuffer.wrap(bytes, 0, HEADER);
        Totals total = Totals.NONE;
        for (int slot = 0; slot < SLOTS; slot++) {
            int entry = header.getInt(slot * 4);
            int offset = entry >>> 8;
            int sectors = entry & 0xFF;
            if (offset == 0) {
                continue;
            }
            total = total.plus(record(bytes, offset, sectors));
        }
        return total;
    }

    private static Totals record(byte[] file, int offset, int sectors) {
        long start = (long) offset * SECTOR;
        if (sectors == 0 || start + RECORD_PREFIX > file.length) {
            return unreadable();
        }
        ByteBuffer prefix = ByteBuffer.wrap(file, (int) start, RECORD_PREFIX);
        int length = prefix.getInt();
        int compression = prefix.get() & 0xFF;
        if (length < 1 || length - 1 > sectors * SECTOR - RECORD_PREFIX || start + 4 + length > file.length) {
            return unreadable();
        }
        byte[] data = new byte[length - 1];
        System.arraycopy(file, (int) start + RECORD_PREFIX, data, 0, data.length);
        CompoundBinaryTag root = decode(compression, data);
        if (root == null) {
            return unreadable();
        }
        return classify(root);
    }

    private static @Nullable CompoundBinaryTag decode(int compression, byte[] data) {
        try (InputStream raw = new ByteArrayInputStream(data)) {
            InputStream inflated = switch (compression) {
                case GZIP -> new GZIPInputStream(raw);
                case ZLIB -> new InflaterInputStream(raw);
                case UNCOMPRESSED -> raw;
                default -> null;
            };
            if (inflated == null) {
                return null;
            }
            return CHUNK_READER.read(inflated, BinaryTagIO.Compression.NONE);
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    private static Totals classify(CompoundBinaryTag root) {
        String status = root.get("Status") instanceof StringBinaryTag tag ? tag.value() : null;
        int dataVersion = root.get("DataVersion") instanceof IntBinaryTag tag ? tag.value() : 0;
        int full = 0;
        int partial = 0;
        int withoutStatus = 0;
        if (status == null) {
            withoutStatus = 1;
        } else if (FULL.equals(status) || NAMESPACED_FULL.equals(status)) {
            full = 1;
        } else {
            partial = 1;
        }
        return new Totals(full, partial, withoutStatus, 0, dataVersion);
    }

    private static Totals unreadable() {
        return new Totals(0, 0, 0, 1, 0);
    }
}
