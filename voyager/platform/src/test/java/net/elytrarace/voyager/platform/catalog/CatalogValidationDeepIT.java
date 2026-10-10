package net.elytrarace.voyager.platform.catalog;

import net.elytrarace.voyager.api.config.ConfigProblem;
import net.elytrarace.voyager.platform.world.MapInstances;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Stream;

import static net.elytrarace.voyager.platform.catalog.CatalogFixtures.cup;
import static net.elytrarace.voyager.platform.catalog.CatalogFixtures.map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The deep check against the real world the repository ships, read through Falco.
 *
 * <p>Integration, not unit: it reads a world from disk and starts Minestom, so it is excluded from
 * {@code :voyager:platform:test} and runs in {@code :voyager:platform:integrationTest}. The world is not
 * in git. The build passes its location through the {@code voyager.it.world} system property (the
 * untracked {@code run/run/worlds/ElytraraceBlueAndRed} under the root project). When that folder is
 * absent the test is skipped with an assumption message, so a checkout without the world stays green.
 *
 * <p>The world is copied into a {@link TempDir} before anything runs against it; the original is never
 * opened for write and never corrupted. The copy is the only thing the corruption step touches.
 */
@Tag("integration")
@EnvTest
class CatalogValidationDeepIT {

    static final String WORLD_PROPERTY = "voyager.it.world";
    static final String WORLD = "ElytraraceBlueAndRed";
    private static final String CORRUPTED_REGION = "r.0.0.mca";

    @TempDir
    Path root;

    @Test
    void theShippedWorldPassesTheDeepCheck(Env env) throws IOException {
        Path shipped = shippedWorld();
        Path worlds = copyWorld(shipped);

        List<ConfigProblem> problems = deepCheck(env, worlds);

        assertThat(problems)
                .as("the shipped world %s must pass the deep check", shipped)
                .isEmpty();
    }

    @Test
    void aTruncatedRegionFileInTheCopyFailsTheDeepCheckAndNamesTheFile(Env env) throws IOException {
        Path worlds = copyWorld(shippedWorld());
        Path region = worlds.resolve(WORLD).resolve("region").resolve(CORRUPTED_REGION);
        truncate(region, 1000);

        List<ConfigProblem> problems = deepCheck(env, worlds);

        assertThat(problems)
                .as("a region file cut to 1000 bytes must be reported by the deep check")
                .isNotEmpty();
        assertThat(problems)
                .as("the report must name the corrupted region file %s", region.toAbsolutePath())
                .anySatisfy(problem -> assertThat(problem.message() + " " + problem.source())
                        .contains(CORRUPTED_REGION));
    }

    /** Runs the catalogue check's deep step, through the same real health source the server uses. */
    private List<ConfigProblem> deepCheck(Env env, Path worlds) throws IOException {
        Path data = root.resolve("data");
        map(data.resolve("maps"), "shipped.json", "shipped", WORLD);
        cup(data.resolve("cups"), "race.json", "race", "RACE", "shipped");
        try (MapInstances instances = new MapInstances(env.process().instance(), worlds)) {
            return CatalogValidation.validate(data, worlds, world -> {
                instances.readEveryChunk(world);
                return instances.healthOf(world);
            });
        }
    }

    /** The shipped world, or a skipped test when it is not on this checkout. */
    private static Path shippedWorld() {
        String configured = System.getProperty(WORLD_PROPERTY);
        assumeTrue(configured != null && !configured.isBlank(),
                () -> "system property %s is not set; the build points it at run/run/worlds/%s".formatted(WORLD_PROPERTY, WORLD));
        Path shipped = Path.of(configured);
        assumeTrue(Files.isDirectory(shipped.resolve("region")),
                () -> "the shipped world is not on this checkout: %s has no region folder".formatted(shipped.toAbsolutePath()));
        return shipped;
    }

    /** Copies the world folder into this test's own temp directory and returns the worlds root above it. */
    private Path copyWorld(Path source) throws IOException {
        Path worlds = root.resolve("worlds");
        Path target = worlds.resolve(WORLD);
        try (Stream<Path> files = Files.walk(source)) {
            for (Path file : files.toList()) {
                Path destination = target.resolve(source.relativize(file).toString());
                if (Files.isDirectory(file)) {
                    Files.createDirectories(destination);
                } else {
                    Files.copy(file, destination);
                }
            }
        }
        return worlds;
    }

    private static void truncate(Path file, long bytes) {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.truncate(bytes);
        } catch (IOException exception) {
            throw new UncheckedIOException("cannot truncate %s".formatted(file), exception);
        }
    }
}
