package net.elytrarace.voyager.platform.world;

import net.elytrarace.voyager.platform.world.exception.WorldAlreadyExistsException;
import org.jetbrains.annotations.ApiStatus;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Creates the world folder of a new map from the void template on the classpath.
 *
 * <p>The template is one region file of zeros, the smallest content that {@link MapInstances} accepts (research 006,
 * spike 1.3). Terrain is not part of the template: a builder builds it in an external editor, and the setup server
 * never saves a world.
 */
@ApiStatus.Internal
public abstract class VoidWorldTemplate {

    private static final String TEMPLATE = "/templates/void-world/";
    private static final String REGION = "region/r.0.0.mca";

    private VoidWorldTemplate() {
    }

    /**
     * Copies the template into a new world folder. A copy that fails removes what it wrote, so a retry is not refused
     * by a half-made folder.
     *
     * @param world the world folder to create, {@code <worldsPath>/<id>}
     * @throws WorldAlreadyExistsException if the folder already exists
     * @throws UncheckedIOException        if the folder cannot be written
     * @throws IllegalStateException       if the template is missing from the classpath
     */
    public static void copyTo(Path world) {
        if (Files.exists(world)) {
            throw WorldAlreadyExistsException.at(world);
        }
        Path region = world.resolve(REGION);
        try (InputStream template = VoidWorldTemplate.class.getResourceAsStream(TEMPLATE + REGION)) {
            if (template == null) {
                throw new IllegalStateException("the void world template %s%s is missing from the classpath"
                        .formatted(TEMPLATE, REGION));
            }
            Files.createDirectories(region.getParent());
            Files.copy(template, region);
        } catch (IOException exception) {
            UncheckedIOException failure =
                    new UncheckedIOException("the void world could not be written to %s".formatted(world), exception);
            try {
                remove(world);
            } catch (UncheckedIOException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    /**
     * Removes a world folder that this module created, with everything in it. Used to take back a copy whose draft
     * could not be written; it never removes a folder it did not create in the same call.
     *
     * @param world the world folder to remove; nothing happens when it does not exist
     * @throws UncheckedIOException if a file in it cannot be removed
     */
    public static void remove(Path world) {
        if (!Files.exists(world)) {
            return;
        }
        try (Stream<Path> tree = Files.walk(world)) {
            for (Path path : tree.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("the void world at %s could not be removed".formatted(world), exception);
        }
    }
}
