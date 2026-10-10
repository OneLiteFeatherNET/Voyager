package net.elytrarace.voyager.platform.world;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Whether a world directory is on disk with region data behind it, judged by the same layout rule
 * {@link MapInstances} reads.
 *
 * <p>Path rule only: no Minestom, no Falco, no instance. The configuration check asks this before it
 * opens a world, and {@link MapInstances} asks the same question of the directory Falco resolved, so
 * both answer from one definition of "holds region data".
 */
public final class WorldFolders {

    private static final String REGION_FILE_SUFFIX = ".mca";
    private static final String LEGACY_REGION = "region";
    private static final Path DIMENSION_REGION = Path.of("dimensions", "minecraft", "overworld", "region");

    private WorldFolders() {
    }

    /** What the check found for one world directory. */
    public enum State {
        /** No folder of that name exists under the worlds directory. */
        MISSING,
        /** The folder exists but holds no region file in either layout the server reads. */
        NO_REGION_DATA,
        /** The folder holds region data in the top-level {@code region/} or the {@code dimensions/} layout. */
        PRESENT
    }

    /**
     * Looks at one world directory and says whether it is usable.
     *
     * @param worldsRoot the directory the world directories sit in
     * @param world      the world directory's name, as a map declares it
     * @return {@link State#MISSING} when the folder is absent, {@link State#NO_REGION_DATA} when it is
     *     present without region files, {@link State#PRESENT} otherwise
     */
    public static State check(Path worldsRoot, String world) {
        Path worldRoot = worldsRoot.resolve(world);
        if (!Files.isDirectory(worldRoot)) {
            return State.MISSING;
        }
        if (holdsRegionData(worldRoot.resolve(LEGACY_REGION)) || holdsRegionData(worldRoot.resolve(DIMENSION_REGION))) {
            return State.PRESENT;
        }
        return State.NO_REGION_DATA;
    }

    /**
     * Whether a region directory holds at least one {@code .mca} file.
     *
     * @param regionDirectory the directory the loader reads region files from
     * @return false when the directory is absent, empty, or holds only files of other names
     */
    public static boolean holdsRegionData(Path regionDirectory) {
        if (!Files.isDirectory(regionDirectory)) {
            return false;
        }
        try (Stream<Path> entries = Files.list(regionDirectory)) {
            return entries.anyMatch(entry -> entry.getFileName().toString().endsWith(REGION_FILE_SUFFIX));
        } catch (IOException exception) {
            throw new UncheckedIOException("cannot list the region directory %s".formatted(regionDirectory), exception);
        }
    }
}
