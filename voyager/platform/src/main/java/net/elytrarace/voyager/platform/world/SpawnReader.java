package net.elytrarace.voyager.platform.world;

import net.kyori.adventure.nbt.BinaryTagIO;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.IntArrayBinaryTag;
import net.kyori.adventure.nbt.IntBinaryTag;
import net.kyori.adventure.nbt.StringBinaryTag;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads the spawn and the data version of a world's {@code level.dat}. The modern {@code Data.spawn} compound of the
 * overworld is read first; a world without it falls back to the legacy {@code Data.SpawnX}, {@code SpawnY} and
 * {@code SpawnZ} keys.
 */
public final class SpawnReader {

    private static final String DATA = "Data";
    private static final String DATA_VERSION = "DataVersion";
    private static final String SPAWN = "spawn";
    private static final String DIMENSION = "dimension";
    private static final String POSITION = "pos";
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String LEGACY_X = "SpawnX";
    private static final String LEGACY_Y = "SpawnY";
    private static final String LEGACY_Z = "SpawnZ";

    private SpawnReader() {
    }

    /**
     * A block a world states as its spawn.
     *
     * @param x      the block's x coordinate
     * @param y      the block's y coordinate
     * @param z      the block's z coordinate
     * @param modern true when the world states it under {@code Data.spawn}, false for the legacy keys
     */
    public record BlockSpawn(int x, int y, int z, boolean modern) {
    }

    /**
     * What the reader found in a {@code level.dat}.
     *
     * @param dataVersion the {@code Data.DataVersion}, or -1 when the file states none
     * @param spawn       the stated spawn, or {@code null} when it states neither form
     */
    public record LevelData(int dataVersion, @Nullable BlockSpawn spawn) {
    }

    /**
     * Reads a gzip {@code level.dat}.
     *
     * @param levelDat the file to read
     * @return what the file states
     * @throws UncheckedIOException if the file cannot be read as gzip NBT
     */
    public static LevelData read(Path levelDat) {
        CompoundBinaryTag root;
        try (InputStream in = Files.newInputStream(levelDat)) {
            root = BinaryTagIO.reader().read(in, BinaryTagIO.Compression.GZIP);
        } catch (IOException exception) {
            throw new UncheckedIOException("the level.dat at %s cannot be read".formatted(levelDat), exception);
        }
        CompoundBinaryTag data = root.get(DATA) instanceof CompoundBinaryTag compound ? compound : CompoundBinaryTag.empty();
        int dataVersion = data.get(DATA_VERSION) instanceof IntBinaryTag version ? version.value() : -1;
        BlockSpawn spawn = modernSpawn(data);
        if (spawn == null) {
            spawn = legacySpawn(data);
        }
        return new LevelData(dataVersion, spawn);
    }

    private static @Nullable BlockSpawn modernSpawn(CompoundBinaryTag data) {
        if (!(data.get(SPAWN) instanceof CompoundBinaryTag spawn)) {
            return null;
        }
        if (!(spawn.get(DIMENSION) instanceof StringBinaryTag dimension) || !OVERWORLD.equals(dimension.value())) {
            return null;
        }
        if (!(spawn.get(POSITION) instanceof IntArrayBinaryTag position) || position.value().length != 3) {
            return null;
        }
        int[] block = position.value();
        return new BlockSpawn(block[0], block[1], block[2], true);
    }

    private static @Nullable BlockSpawn legacySpawn(CompoundBinaryTag data) {
        if (data.get(LEGACY_X) instanceof IntBinaryTag x
                && data.get(LEGACY_Y) instanceof IntBinaryTag y
                && data.get(LEGACY_Z) instanceof IntBinaryTag z) {
            return new BlockSpawn(x.value(), y.value(), z.value(), false);
        }
        return null;
    }
}
