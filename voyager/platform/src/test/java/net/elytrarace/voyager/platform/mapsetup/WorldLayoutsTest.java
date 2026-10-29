package net.elytrarace.voyager.platform.mapsetup;

import net.elytrarace.voyager.api.mapsetup.WorldLayout;
import net.elytrarace.voyager.api.mapsetup.exception.WorldImportRefusedException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static net.elytrarace.voyager.platform.world.WorldFixtures.chunk;
import static net.elytrarace.voyager.platform.world.WorldFixtures.chunkTag;
import static net.elytrarace.voyager.platform.world.WorldFixtures.writeRegion;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorldLayoutsTest {

    @TempDir
    Path world;

    @Test
    void aRegionFolderAtTheRootIsAccepted() throws IOException {
        writeRegion(world.resolve("region").resolve("r.0.0.mca"), java.util.Map.of(0, chunk(chunkTag("full", 4438))));

        assertThat(WorldLayouts.detect(world)).isEqualTo(WorldLayout.REGION_AT_ROOT);
    }

    @Test
    void theDimensionFolderOfTheOverworldIsAccepted() throws IOException {
        writeRegion(world.resolve("dimensions/minecraft/overworld/region/r.0.0.mca"),
                java.util.Map.of(0, chunk(chunkTag("full", 4438))));

        assertThat(WorldLayouts.detect(world)).isEqualTo(WorldLayout.DIMENSION_OVERWORLD);
    }

    @Test
    void bothLayoutsAtOnceAreRefusedAndTheMessageNamesBoth() throws IOException {
        writeRegion(world.resolve("region").resolve("r.0.0.mca"), java.util.Map.of(0, chunk(chunkTag("full", 4438))));
        writeRegion(world.resolve("dimensions/minecraft/overworld/region/r.0.0.mca"),
                java.util.Map.of(0, chunk(chunkTag("full", 4438))));

        assertThatThrownBy(() -> WorldLayouts.detect(world))
                .isInstanceOfSatisfying(WorldImportRefusedException.class, refusal -> {
                    assertThat(refusal.reason()).isEqualTo(WorldImportRefusedException.Reason.LAYOUT_AMBIGUOUS);
                    assertThat(refusal.arguments()).containsExactly(
                            "region/", WorldLayout.DIMENSION_OVERWORLD.folder());
                });
    }

    @Test
    void aFolderWithOnlyMcrFilesIsRefusedAsUnsupported() throws IOException {
        Files.createDirectories(world.resolve("region"));
        Files.writeString(world.resolve("region").resolve("r.0.0.mcr"), "old format");

        assertThatThrownBy(() -> WorldLayouts.detect(world))
                .isInstanceOfSatisfying(WorldImportRefusedException.class, refusal ->
                        assertThat(refusal.reason()).isEqualTo(WorldImportRefusedException.Reason.LAYOUT_UNSUPPORTED));
    }
}
