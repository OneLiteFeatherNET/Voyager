package net.elytrarace.voyager.platform.world;

import net.elytrarace.voyager.platform.world.exception.WorldAlreadyExistsException;
import org.jetbrains.annotations.ApiStatus;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
            throw new UncheckedIOException("the void world could not be written to %s".formatted(world), exception);
        }
    }
}
