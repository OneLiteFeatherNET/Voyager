package net.elytrarace.voyager.platform.world;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WorldFoldersTest {

    @TempDir
    Path worldsRoot;

    @Test
    void reportsAWorldWhoseFolderDoesNotExistAsMissing() {
        assertThat(WorldFolders.check(worldsRoot, "ElytraraceMissing"))
                .isEqualTo(WorldFolders.State.MISSING);
    }

    @Test
    void reportsAnExistingFolderWithNoRegionDataAsNoRegionData() throws IOException {
        Files.createDirectories(worldsRoot.resolve("Empty"));

        assertThat(WorldFolders.check(worldsRoot, "Empty")).isEqualTo(WorldFolders.State.NO_REGION_DATA);
    }

    @Test
    void reportsALegacyRegionDirectoryWithARegionFileAsPresent() throws IOException {
        Files.createDirectories(worldsRoot.resolve("Legacy/region"));
        Files.writeString(worldsRoot.resolve("Legacy/region/r.0.0.mca"), "");

        assertThat(WorldFolders.check(worldsRoot, "Legacy")).isEqualTo(WorldFolders.State.PRESENT);
    }

    @Test
    void acceptsTheDimensionsLayoutWhenItHoldsRegionDataAndNoTopLevelRegionDirectory() throws IOException {
        Path region = worldsRoot.resolve("Modern/dimensions/minecraft/overworld/region");
        Files.createDirectories(region);
        Files.writeString(region.resolve("r.0.0.mca"), "");

        assertThat(WorldFolders.check(worldsRoot, "Modern")).isEqualTo(WorldFolders.State.PRESENT);
    }

    @Test
    void doesNotCountAFileNamedLikeARegionAsRegionData() throws IOException {
        Path region = worldsRoot.resolve("Stray/region");
        Files.createDirectories(region);
        Files.writeString(region.resolve("readme.txt"), "not a region file");

        assertThat(WorldFolders.check(worldsRoot, "Stray")).isEqualTo(WorldFolders.State.NO_REGION_DATA);
    }

    @Test
    void holdsRegionDataIsFalseForADirectoryThatIsNotThere() {
        assertThat(WorldFolders.holdsRegionData(worldsRoot.resolve("nothing-here"))).isFalse();
    }
}
