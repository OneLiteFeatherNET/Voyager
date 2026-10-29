package net.elytrarace.voyager.platform.world;

import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.IntArrayBinaryTag;
import net.kyori.adventure.nbt.IntBinaryTag;
import net.kyori.adventure.nbt.StringBinaryTag;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static net.elytrarace.voyager.platform.world.WorldFixtures.writeLevelDat;
import static org.assertj.core.api.Assertions.assertThat;

class SpawnReaderTest {

    @TempDir
    Path folder;

    @Test
    void aModernOverworldSpawnGivesTheStatedBlock() throws IOException {
        Path levelDat = folder.resolve("level.dat");
        writeLevelDat(levelDat, CompoundBinaryTag.builder()
                .putInt("DataVersion", 4438)
                .put("spawn", CompoundBinaryTag.builder()
                        .put("dimension", StringBinaryTag.stringBinaryTag("minecraft:overworld"))
                        .put("pos", IntArrayBinaryTag.intArrayBinaryTag(120, 70, -40))
                        .build())
                .build());

        SpawnReader.LevelData data = SpawnReader.read(levelDat);

        assertThat(data.spawn()).isNotNull();
        assertThat(data.spawn().x()).isEqualTo(120);
        assertThat(data.spawn().y()).isEqualTo(70);
        assertThat(data.spawn().z()).isEqualTo(-40);
        assertThat(data.spawn().modern()).isTrue();
        assertThat(data.dataVersion()).isEqualTo(4438);
    }

    @Test
    void aLegacySpawnGivesTheStatedBlockOfTheLegacyKeys() throws IOException {
        Path levelDat = folder.resolve("level.dat");
        writeLevelDat(levelDat, CompoundBinaryTag.builder()
                .putInt("SpawnX", 0)
                .putInt("SpawnY", 65)
                .putInt("SpawnZ", 0)
                .build());

        SpawnReader.LevelData data = SpawnReader.read(levelDat);

        assertThat(data.spawn()).isNotNull();
        assertThat(data.spawn().x()).isZero();
        assertThat(data.spawn().y()).isEqualTo(65);
        assertThat(data.spawn().z()).isZero();
        assertThat(data.spawn().modern()).isFalse();
    }

    @Test
    void aModernSpawnOutsideTheOverworldFallsBackToTheLegacyKeys() throws IOException {
        Path levelDat = folder.resolve("level.dat");
        writeLevelDat(levelDat, CompoundBinaryTag.builder()
                .put("spawn", CompoundBinaryTag.builder()
                        .put("dimension", StringBinaryTag.stringBinaryTag("minecraft:the_nether"))
                        .put("pos", IntArrayBinaryTag.intArrayBinaryTag(1, 2, 3))
                        .build())
                .putInt("SpawnX", 5)
                .putInt("SpawnY", 6)
                .putInt("SpawnZ", 7)
                .build());

        SpawnReader.LevelData data = SpawnReader.read(levelDat);

        assertThat(data.spawn()).isNotNull();
        assertThat(data.spawn().x()).isEqualTo(5);
        assertThat(data.spawn().modern()).isFalse();
    }

    @Test
    void aLevelDatWithNeitherSpawnLeavesTheSpawnUnset() throws IOException {
        Path levelDat = folder.resolve("level.dat");
        writeLevelDat(levelDat, CompoundBinaryTag.builder().put("DataVersion", IntBinaryTag.intBinaryTag(4438)).build());

        SpawnReader.LevelData data = SpawnReader.read(levelDat);

        assertThat(data.spawn()).as("no spawn key is stated").isNull();
        assertThat(data.dataVersion()).isEqualTo(4438);
    }
}
