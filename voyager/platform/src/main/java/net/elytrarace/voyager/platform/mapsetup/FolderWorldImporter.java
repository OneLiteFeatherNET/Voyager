package net.elytrarace.voyager.platform.mapsetup;

import net.elytrarace.voyager.api.mapsetup.Census;
import net.elytrarace.voyager.api.mapsetup.ImportReport;
import net.elytrarace.voyager.api.mapsetup.MapId;
import net.elytrarace.voyager.api.mapsetup.StatedSpawn;
import net.elytrarace.voyager.api.mapsetup.WorldFolderName;
import net.elytrarace.voyager.api.mapsetup.WorldImporter;
import net.elytrarace.voyager.api.mapsetup.WorldLayout;
import net.elytrarace.voyager.api.mapsetup.exception.WorldImportRefusedException;
import net.elytrarace.voyager.api.mapsetup.exception.WorldImportRefusedException.Reason;
import net.elytrarace.voyager.platform.world.RegionCensus;
import net.elytrarace.voyager.platform.world.SpawnReader;
import net.elytrarace.voyager.platform.world.VoidWorldTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * The file-system implementation of {@link WorldImporter}: it copies the region files of a world folder under the import
 * root into {@code worlds/.<id>.importing}, surveys the copy, and renames it to {@code worlds/<id>} only when the survey
 * does not refuse it.
 *
 * <p>Every check that can refuse runs before the first byte is copied, except the survey of the copy, which can only
 * see the region data once it is there. A refusal after the copy removes the staging folder. The source is read for its
 * layout, its limits and its {@code level.dat}; the census reads the copy alone.
 */
public final class FolderWorldImporter implements WorldImporter {

    private static final String STAGING_PREFIX = ".";
    private static final String STAGING_SUFFIX = ".importing";
    private static final String LIMIT_BYTES = "VOYAGER_IMPORT_MAX_BYTES";
    private static final String LIMIT_REGIONS = "VOYAGER_IMPORT_MAX_REGIONS";

    private final Path importRoot;
    private final Path worlds;
    private final long maxBytes;
    private final int maxRegions;

    /**
     * @param importRoot the folder that holds the world folders to import; never created here
     * @param worlds     the folder that holds one world folder per map
     * @param maxBytes   the largest total of region bytes one import may copy
     * @param maxRegions the largest number of region files one import may copy
     */
    public FolderWorldImporter(Path importRoot, Path worlds, long maxBytes, int maxRegions) {
        this.importRoot = importRoot;
        this.worlds = worlds;
        this.maxBytes = maxBytes;
        this.maxRegions = maxRegions;
    }

    @Override
    public ImportReport importWorld(WorldFolderName folder, MapId id) {
        if (!Files.isDirectory(importRoot)) {
            throw WorldImportRefusedException.because(Reason.ROOT_MISSING, importRoot.toAbsolutePath().toString());
        }
        Path source = source(folder);
        Path world = worlds.resolve(id.value());
        if (Files.exists(world)) {
            throw WorldImportRefusedException.because(Reason.WORLD_EXISTS, id.value());
        }
        WorldLayout layout = WorldLayouts.detect(source);
        Path regionDirectory = WorldLayouts.regionDirectory(source, layout);
        List<Path> regions = WorldLayouts.regionFiles(regionDirectory);
        refuseLinks(regionDirectory, regions);
        long bytes = totalBytes(regions);
        refuseOverLimits(regions.size(), bytes);
        List<String> ignored = ignoredDimensions(source);
        SpawnReader.LevelData level = levelData(source);
        refuseNewerData(level.dataVersion());

        Path staging = worlds.resolve(STAGING_PREFIX + id.value() + STAGING_SUFFIX);
        VoidWorldTemplate.remove(staging);
        Census census;
        try {
            census = copyAndSurvey(regions, staging, world);
        } catch (IOException exception) {
            discardStaging(staging, exception);
            throw new UncheckedIOException("the world %s could not be imported".formatted(folder.value()), exception);
        } catch (RuntimeException failure) {
            discardStaging(staging, failure);
            throw failure;
        }
        StatedSpawn spawn = level.spawn() == null ? null
                : new StatedSpawn(level.spawn().x(), level.spawn().y(), level.spawn().z(),
                        level.spawn().modern() ? StatedSpawn.Source.MODERN : StatedSpawn.Source.LEGACY);
        return new ImportReport(layout, regions.size(), bytes, ignored, spawn, census);
    }

    @Override
    public void discard(MapId id) {
        VoidWorldTemplate.remove(worlds.resolve(id.value()));
    }

    /** Resolves the world folder and refuses one whose real path leaves the import root. */
    private Path source(WorldFolderName folder) {
        Path candidate = importRoot.resolve(folder.value());
        if (!Files.isDirectory(candidate)) {
            throw WorldImportRefusedException.because(Reason.FOLDER_MISSING, candidate.toAbsolutePath().toString());
        }
        try {
            Path real = candidate.toRealPath();
            if (!real.startsWith(importRoot.toRealPath())) {
                throw WorldImportRefusedException.because(Reason.FOLDER_INVALID, folder.value());
            }
            return real;
        } catch (IOException exception) {
            throw new UncheckedIOException("the world folder %s cannot be resolved".formatted(folder.value()), exception);
        }
    }

    private static void refuseLinks(Path regionDirectory, List<Path> regions) {
        if (Files.isSymbolicLink(regionDirectory)) {
            throw WorldImportRefusedException.because(Reason.LINK, regionDirectory.getFileName().toString());
        }
        for (Path region : regions) {
            if (Files.isSymbolicLink(region)) {
                throw WorldImportRefusedException.because(Reason.LINK, region.getFileName().toString());
            }
        }
    }

    private static long totalBytes(List<Path> regions) {
        long total = 0;
        for (Path region : regions) {
            try {
                total += Files.size(region);
            } catch (IOException exception) {
                throw new UncheckedIOException("the region file %s cannot be measured".formatted(region), exception);
            }
        }
        return total;
    }

    private void refuseOverLimits(int regionCount, long bytes) {
        if (bytes > maxBytes) {
            throw WorldImportRefusedException.because(Reason.TOO_LARGE,
                    Long.toString(bytes), Long.toString(maxBytes), LIMIT_BYTES);
        }
        if (regionCount > maxRegions) {
            throw WorldImportRefusedException.because(Reason.TOO_LARGE,
                    Integer.toString(regionCount), Integer.toString(maxRegions), LIMIT_REGIONS);
        }
    }

    private static List<String> ignoredDimensions(Path source) {
        List<String> ignored = new ArrayList<>(2);
        if (hasRegions(source, "DIM-1/region", "dimensions/minecraft/the_nether/region")) {
            ignored.add("nether");
        }
        if (hasRegions(source, "DIM1/region", "dimensions/minecraft/the_end/region")) {
            ignored.add("end");
        }
        return List.copyOf(ignored);
    }

    private static boolean hasRegions(Path source, String... folders) {
        for (String folder : folders) {
            if (!WorldLayouts.regionFiles(source.resolve(folder)).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static SpawnReader.LevelData levelData(Path source) {
        Path levelDat = source.resolve("level.dat");
        return Files.isRegularFile(levelDat) ? SpawnReader.read(levelDat) : new SpawnReader.LevelData(-1, null);
    }

    private static void refuseNewerData(int dataVersion) {
        if (dataVersion > RegionCensus.HIGHEST_DATA_VERSION) {
            throw WorldImportRefusedException.because(Reason.DATA_VERSION_NEWER,
                    Integer.toString(dataVersion), Integer.toString(RegionCensus.HIGHEST_DATA_VERSION));
        }
    }

    /** Copies the regions into the staging folder, surveys them, and renames the folder into place. */
    private Census copyAndSurvey(List<Path> regions, Path staging, Path world) throws IOException {
        Path stagedRegion = Files.createDirectories(staging.resolve("region"));
        for (Path region : regions) {
            Files.copy(region, stagedRegion.resolve(region.getFileName().toString()));
        }
        RegionCensus.Totals totals = RegionCensus.Totals.NONE;
        try (var copies = Files.list(stagedRegion)) {
            for (Path copy : copies.sorted().toList()) {
                totals = totals.plus(RegionCensus.count(copy));
            }
        }
        refuseNewerData(totals.highestDataVersion());
        try {
            Files.move(staging, world, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(staging, world);
        }
        return new Census(totals.full(), totals.partial(), totals.withoutStatus(), totals.unreadable());
    }

    private static void discardStaging(Path staging, Throwable cause) {
        try {
            VoidWorldTemplate.remove(staging);
        } catch (UncheckedIOException cleanup) {
            cause.addSuppressed(cleanup);
        }
    }
}
