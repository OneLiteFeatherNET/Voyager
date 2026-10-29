package net.elytrarace.voyager.platform.world;

import net.kyori.adventure.nbt.CompoundBinaryTag;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static net.elytrarace.voyager.platform.world.WorldFixtures.chunk;
import static net.elytrarace.voyager.platform.world.WorldFixtures.chunkTag;
import static net.elytrarace.voyager.platform.world.WorldFixtures.unreadableChunk;
import static net.elytrarace.voyager.platform.world.WorldFixtures.writeRegion;
import static org.assertj.core.api.Assertions.assertThat;

class RegionCensusTest {

    @TempDir
    Path folder;

    @Test
    void aNamespacedFullAndABareFullChunkBothCountAsFull() throws IOException {
        Path region = folder.resolve("r.0.0.mca");
        writeRegion(region, Map.of(
                0, chunk(chunkTag("minecraft:full", 4438)),
                1, chunk(chunkTag("full", 4438))));

        RegionCensus.Totals totals = RegionCensus.count(region);

        assertThat(totals.full()).as("namespaced and bare full chunks").isEqualTo(2);
        assertThat(totals.partial()).isZero();
    }

    @Test
    void aChunkWithAnotherStatusCountsAsPartial() throws IOException {
        Path region = folder.resolve("r.0.0.mca");
        writeRegion(region, Map.of(2, chunk(chunkTag("features", 4438))));

        assertThat(RegionCensus.count(region).partial()).isEqualTo(1);
    }

    @Test
    void aChunkThatStatesNoStatusIsCountedApart() throws IOException {
        Path region = folder.resolve("r.0.0.mca");
        writeRegion(region, Map.of(3, chunk(chunkTag(null, 4438))));

        assertThat(RegionCensus.count(region).withoutStatus()).isEqualTo(1);
    }

    @Test
    void aRecordThatCannotBeReadIsCountedAsUnreadable() throws IOException {
        Path region = folder.resolve("r.0.0.mca");
        writeRegion(region, Map.of(4, unreadableChunk()));

        assertThat(RegionCensus.count(region).unreadable()).isEqualTo(1);
    }

    @Test
    void theMixedRegionGivesTheCountsOfTheSpec() throws IOException {
        Path region = folder.resolve("r.0.0.mca");
        writeRegion(region, Map.of(
                0, chunk(chunkTag("minecraft:full", 4438)),
                1, chunk(chunkTag("full", 4438)),
                2, chunk(chunkTag("features", 4438)),
                3, chunk(chunkTag(null, 4438)),
                4, unreadableChunk()));

        RegionCensus.Totals totals = RegionCensus.count(region);

        assertThat(totals.full()).isEqualTo(2);
        assertThat(totals.partial()).isEqualTo(1);
        assertThat(totals.withoutStatus()).isEqualTo(1);
        assertThat(totals.unreadable()).isEqualTo(1);
    }

    @Test
    void anEmptyRegionCountsNothing() throws IOException {
        Path region = folder.resolve("r.0.0.mca");
        writeRegion(region, Map.of());

        RegionCensus.Totals totals = RegionCensus.count(region);

        assertThat(totals.records()).isZero();
        assertThat(totals.highestDataVersion()).isZero();
    }

    @Test
    void aChunkLargerThanTheDefaultNbtLimitIsStillCountedAsReadable() throws IOException {
        Path region = folder.resolve("r.0.0.mca");
        CompoundBinaryTag bigChunk = CompoundBinaryTag.builder()
                .putInt("DataVersion", 4438)
                .putString("Status", "full")
                .putByteArray("Blob", new byte[200_000])
                .build();
        writeRegion(region, Map.of(0, WorldFixtures.chunk(bigChunk)));

        RegionCensus.Totals totals = RegionCensus.count(region);

        assertThat(totals.full()).as("a 200 KB chunk of a real world is a full chunk").isEqualTo(1);
        assertThat(totals.unreadable()).isZero();
    }

    @Test
    void theHighestDataVersionOfTheReadableChunksIsReported() throws IOException {
        Path region = folder.resolve("r.0.0.mca");
        writeRegion(region, Map.of(
                0, chunk(chunkTag("full", 4438)),
                1, chunk(chunkTag("full", 4904))));

        assertThat(RegionCensus.count(region).highestDataVersion()).isEqualTo(4904);
    }
}
