package net.elytrarace.voyager.platform.mapsetup;

import net.elytrarace.voyager.api.mapsetup.Census;
import net.elytrarace.voyager.api.mapsetup.ImportReport;
import net.elytrarace.voyager.api.mapsetup.MapId;
import net.elytrarace.voyager.api.mapsetup.StatedSpawn;
import net.elytrarace.voyager.api.mapsetup.WorldFolderName;
import net.elytrarace.voyager.api.mapsetup.WorldLayout;
import net.elytrarace.voyager.api.mapsetup.exception.WorldImportRefusedException;
import net.elytrarace.voyager.api.mapsetup.exception.WorldImportRefusedException.Reason;
import net.kyori.adventure.nbt.CompoundBinaryTag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static net.elytrarace.voyager.platform.world.WorldFixtures.chunk;
import static net.elytrarace.voyager.platform.world.WorldFixtures.chunkTag;
import static net.elytrarace.voyager.platform.world.WorldFixtures.writeLevelDat;
import static net.elytrarace.voyager.platform.world.WorldFixtures.writeRegion;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The import of a world folder into a map's world folder. Every test builds its own import root and worlds folder in
 * its temporary folder, and the limits are injected small, so no test writes 256 MiB.
 */
class FolderWorldImporterTest {

    private static final long GENEROUS_BYTES = 1L << 30;
    private static final int GENEROUS_REGIONS = 1024;

    @TempDir
    Path root;

    private Path imports;
    private Path worlds;
    private final MapId skycube = new MapId("skycube");
    private final WorldFolderName cube = new WorldFolderName("elytrarace-cube");

    @BeforeEach
    void folders() throws IOException {
        imports = Files.createDirectories(root.resolve("imports"));
        worlds = Files.createDirectories(root.resolve("worlds"));
    }

    private FolderWorldImporter importer(long maxBytes, int maxRegions) {
        return new FolderWorldImporter(imports, worlds, maxBytes, maxRegions);
    }

    private FolderWorldImporter importer() {
        return importer(GENEROUS_BYTES, GENEROUS_REGIONS);
    }

    private Path cubeFolder() {
        return imports.resolve("elytrarace-cube");
    }

    private void rootRegion(String name, Map<Integer, byte[]> records) throws IOException {
        writeRegion(cubeFolder().resolve("region").resolve(name), records);
    }

    private static Map<Integer, byte[]> fullChunks(int dataVersion) {
        return Map.of(0, chunk(chunkTag("minecraft:full", dataVersion)));
    }

    private static List<String> namesIn(Path folder) throws IOException {
        try (Stream<Path> entries = Files.list(folder)) {
            return entries.map(entry -> entry.getFileName().toString()).sorted().toList();
        }
    }

    @Test
    void theRegionFilesOfTheOverworldAreCopiedAndNothingElse() throws IOException {
        rootRegion("r.0.0.mca", fullChunks(4438));
        rootRegion("r.0.-1.mca", fullChunks(4438));
        writeLevelDat(cubeFolder().resolve("level.dat"), CompoundBinaryTag.builder().build());
        Files.writeString(cubeFolder().resolve("uid.dat"), "uid");
        writeRegion(cubeFolder().resolve("entities").resolve("r.0.0.mca"), fullChunks(4438));
        Files.createDirectories(cubeFolder().resolve("poi"));
        Files.createDirectories(cubeFolder().resolve("data"));
        writeRegion(cubeFolder().resolve("DIM-1").resolve("region").resolve("r.0.0.mca"), fullChunks(4438));

        importer().importWorld(cube, skycube);

        assertThat(namesIn(worlds.resolve("skycube"))).containsExactly("region");
        assertThat(namesIn(worlds.resolve("skycube").resolve("region"))).containsExactly("r.0.-1.mca", "r.0.0.mca");
    }

    @Test
    void theNetherFolderIsReportedAsIgnored() throws IOException {
        rootRegion("r.0.0.mca", fullChunks(4438));
        writeRegion(cubeFolder().resolve("DIM-1").resolve("region").resolve("r.0.0.mca"), fullChunks(4438));

        ImportReport report = importer().importWorld(cube, skycube);

        assertThat(report.ignoredDimensions()).containsExactly("nether");
    }

    @Test
    void theDimensionLayoutIsCopiedIntoTheRegionFolderOfTheMap() throws IOException {
        writeRegion(cubeFolder().resolve("dimensions/minecraft/overworld/region/r.0.0.mca"), fullChunks(4438));

        ImportReport report = importer().importWorld(cube, skycube);

        assertThat(report.layout()).isEqualTo(WorldLayout.DIMENSION_OVERWORLD);
        assertThat(worlds.resolve("skycube").resolve("region").resolve("r.0.0.mca")).exists();
    }

    @Test
    void aRegionFileThatIsASymbolicLinkIsRefusedAndNothingIsCopied() throws IOException {
        Path outside = Files.writeString(root.resolve("outside.mca"), "not a region of the import");
        Files.createDirectories(cubeFolder().resolve("region"));
        Files.createSymbolicLink(cubeFolder().resolve("region").resolve("r.0.0.mca"), outside);

        assertThatThrownBy(() -> importer().importWorld(cube, skycube))
                .isInstanceOfSatisfying(WorldImportRefusedException.class, refusal ->
                        assertThat(refusal.reason()).isEqualTo(Reason.LINK));
        assertThat(worlds.resolve("skycube")).doesNotExist();
        assertThat(worlds.resolve(".skycube.importing")).doesNotExist();
    }

    @Test
    void aFolderWhoseRealPathLeavesTheImportRootIsRefused() throws IOException {
        Path outside = Files.createDirectories(root.resolve("outside"));
        writeRegion(outside.resolve("region").resolve("r.0.0.mca"), fullChunks(4438));
        Files.createSymbolicLink(imports.resolve("escape"), outside);

        assertThatThrownBy(() -> importer().importWorld(new WorldFolderName("escape"), skycube))
                .isInstanceOfSatisfying(WorldImportRefusedException.class, refusal ->
                        assertThat(refusal.reason()).isEqualTo(Reason.FOLDER_INVALID));
        assertThat(worlds.resolve("skycube")).doesNotExist();
    }

    @Test
    void aRegionTotalAboveTheByteLimitIsRefusedBeforeAnyCopy() throws IOException {
        rootRegion("r.0.0.mca", fullChunks(4438));

        assertThatThrownBy(() -> importer(100, GENEROUS_REGIONS).importWorld(cube, skycube))
                .isInstanceOfSatisfying(WorldImportRefusedException.class, refusal -> {
                    assertThat(refusal.reason()).isEqualTo(Reason.TOO_LARGE);
                    assertThat(refusal.arguments()).contains("100", "VOYAGER_IMPORT_MAX_BYTES");
                });
        assertThat(namesIn(worlds)).isEmpty();
    }

    @Test
    void aRegionCountAboveTheLimitIsRefusedBeforeAnyCopy() throws IOException {
        rootRegion("r.0.0.mca", fullChunks(4438));
        rootRegion("r.0.1.mca", fullChunks(4438));

        assertThatThrownBy(() -> importer(GENEROUS_BYTES, 1).importWorld(cube, skycube))
                .isInstanceOfSatisfying(WorldImportRefusedException.class, refusal -> {
                    assertThat(refusal.reason()).isEqualTo(Reason.TOO_LARGE);
                    assertThat(refusal.arguments()).contains("2", "VOYAGER_IMPORT_MAX_REGIONS");
                });
        assertThat(namesIn(worlds)).isEmpty();
    }

    @Test
    void aPartialChunkIsImportedAndReportedWithoutARefusal() throws IOException {
        rootRegion("r.0.0.mca", Map.of(
                0, chunk(chunkTag("full", 4438)),
                1, chunk(chunkTag("features", 4438))));

        ImportReport report = importer().importWorld(cube, skycube);

        assertThat(report.census()).isEqualTo(new Census(1, 1, 0, 0));
        assertThat(report.census().partialPercent()).isEqualTo(50);
        assertThat(worlds.resolve("skycube")).exists();
    }

    @Test
    void aDataVersionAboveThisServerIsRefusedAndTheCopyIsRemoved() throws IOException {
        rootRegion("r.0.0.mca", fullChunks(4904));

        assertThatThrownBy(() -> importer().importWorld(cube, skycube))
                .isInstanceOfSatisfying(WorldImportRefusedException.class, refusal -> {
                    assertThat(refusal.reason()).isEqualTo(Reason.DATA_VERSION_NEWER);
                    assertThat(refusal.arguments()).containsExactly("4904", "4903");
                });
        assertThat(namesIn(worlds)).isEmpty();
    }

    @Test
    void aDataVersionOfTheRecordedWorldIsAccepted() throws IOException {
        rootRegion("r.0.0.mca", fullChunks(4438));

        assertThat(importer().importWorld(cube, skycube).census().full()).isEqualTo(1);
    }

    @Test
    void aMissingImportRootIsRefusedWithItsPathAndIsNotCreated() throws IOException {
        Path missing = root.resolve("no-such-imports");
        FolderWorldImporter importer = new FolderWorldImporter(missing, worlds, GENEROUS_BYTES, GENEROUS_REGIONS);

        assertThatThrownBy(() -> importer.importWorld(cube, skycube))
                .isInstanceOfSatisfying(WorldImportRefusedException.class, refusal -> {
                    assertThat(refusal.reason()).isEqualTo(Reason.ROOT_MISSING);
                    assertThat(refusal.arguments()).containsExactly(missing.toAbsolutePath().toString());
                });
        assertThat(missing).doesNotExist();
    }

    @Test
    void aMissingWorldFolderIsRefused() {
        assertThatThrownBy(() -> importer().importWorld(new WorldFolderName("nothing-here"), skycube))
                .isInstanceOfSatisfying(WorldImportRefusedException.class, refusal ->
                        assertThat(refusal.reason()).isEqualTo(Reason.FOLDER_MISSING));
    }

    @Test
    void aWorldFolderThatAlreadyExistsForTheIdIsRefusedAndUntouched() throws IOException {
        rootRegion("r.0.0.mca", fullChunks(4438));
        Files.createDirectories(worlds.resolve("skycube"));

        assertThatThrownBy(() -> importer().importWorld(cube, skycube))
                .isInstanceOfSatisfying(WorldImportRefusedException.class, refusal -> {
                    assertThat(refusal.reason()).isEqualTo(Reason.WORLD_EXISTS);
                    assertThat(refusal.arguments()).containsExactly("skycube");
                });
        assertThat(namesIn(worlds)).containsExactly("skycube");
    }

    @Test
    void theStatedSpawnOfTheLevelDatIsReportedAsItsBlock() throws IOException {
        rootRegion("r.0.0.mca", fullChunks(4438));
        writeLevelDat(cubeFolder().resolve("level.dat"), CompoundBinaryTag.builder()
                .putInt("SpawnX", 0).putInt("SpawnY", 65).putInt("SpawnZ", 0).build());

        ImportReport report = importer().importWorld(cube, skycube);

        assertThat(report.spawn()).isEqualTo(new StatedSpawn(0, 65, 0, StatedSpawn.Source.LEGACY));
    }

    @Test
    void aWorldWithoutAStatedSpawnReportsNone() throws IOException {
        rootRegion("r.0.0.mca", fullChunks(4438));

        assertThat(importer().importWorld(cube, skycube).spawn()).isNull();
    }

    @Test
    void discardRemovesTheWorldFolderOfTheMap() throws IOException {
        rootRegion("r.0.0.mca", fullChunks(4438));
        importer().importWorld(cube, skycube);

        importer().discard(skycube);

        assertThat(worlds.resolve("skycube")).doesNotExist();
    }
}
