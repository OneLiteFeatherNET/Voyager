package net.elytrarace.voyager.platform.mapsetup;

import net.elytrarace.voyager.api.mapsetup.WorldLayout;
import net.elytrarace.voyager.api.mapsetup.exception.WorldImportRefusedException;
import net.elytrarace.voyager.api.mapsetup.exception.WorldImportRefusedException.Reason;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The layout rule of an importable world: its overworld region files are in {@code region/} at the root, or in
 * {@code dimensions/minecraft/overworld/region/}, and never in both. A region file is a {@code r.X.Z.mca} name.
 */
public final class WorldLayouts {

    private static final Pattern REGION_FILE = Pattern.compile("r\\.-?\\d+\\.-?\\d+\\.mca");

    private WorldLayouts() {
    }

    /**
     * Says which accepted layout a world folder uses.
     *
     * @param world the world folder under the import root, after its real path has been checked
     * @return the layout that holds the region files
     * @throws WorldImportRefusedException with {@link Reason#LAYOUT_UNSUPPORTED} when neither layout holds a region file,
     *     or {@link Reason#LAYOUT_AMBIGUOUS} when both do
     */
    public static WorldLayout detect(Path world) {
        boolean atRoot = !regionFiles(world.resolve(WorldLayout.REGION_AT_ROOT.folder())).isEmpty();
        boolean dimension = !regionFiles(world.resolve(WorldLayout.DIMENSION_OVERWORLD.folder())).isEmpty();
        if (atRoot && dimension) {
            throw WorldImportRefusedException.because(Reason.LAYOUT_AMBIGUOUS,
                    WorldLayout.REGION_AT_ROOT.folder(), WorldLayout.DIMENSION_OVERWORLD.folder());
        }
        if (atRoot) {
            return WorldLayout.REGION_AT_ROOT;
        }
        if (dimension) {
            return WorldLayout.DIMENSION_OVERWORLD;
        }
        throw WorldImportRefusedException.because(Reason.LAYOUT_UNSUPPORTED);
    }

    /**
     * The region folder of a layout inside a world folder.
     *
     * @param world  the world folder
     * @param layout the layout
     * @return the folder that holds the region files
     */
    public static Path regionDirectory(Path world, WorldLayout layout) {
        return world.resolve(layout.folder());
    }

    /**
     * The region files of a folder, sorted by name. A name is a region file when it is {@code r.X.Z.mca}; a folder of
     * that name is not, and a symbolic link of that name is, so the import can refuse it.
     *
     * @param regionDirectory the folder to list; an absent folder has none
     * @return the region files, sorted by name
     * @throws UncheckedIOException if the folder cannot be listed
     */
    public static List<Path> regionFiles(Path regionDirectory) {
        if (!Files.isDirectory(regionDirectory)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(regionDirectory)) {
            return entries
                    .filter(entry -> REGION_FILE.matcher(entry.getFileName().toString()).matches())
                    .filter(entry -> !Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS))
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            throw new UncheckedIOException("the region folder %s cannot be listed".formatted(regionDirectory), exception);
        }
    }
}
