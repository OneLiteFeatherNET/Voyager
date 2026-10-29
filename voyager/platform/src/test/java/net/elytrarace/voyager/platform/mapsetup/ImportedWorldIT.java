package net.elytrarace.voyager.platform.mapsetup;

import net.elytrarace.voyager.api.mapsetup.ImportReport;
import net.elytrarace.voyager.api.mapsetup.MapId;
import net.elytrarace.voyager.api.mapsetup.WorldFolderName;
import net.elytrarace.voyager.platform.world.MapInstances;
import net.elytrarace.voyager.platform.world.WorldHealth;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Imports a real finished world and loads the copy through {@link MapInstances}: the spike of the import change. The
 * world is named by the {@code voyager.it.world} system property (the integration task's {@code voyagerItWorld}); it is
 * read in place, never written, and the copy lands in a temporary folder. The test skips with a message when the
 * property names no folder, so a checkout without a world stays green.
 *
 * <p>The census and the load result are printed on the test's standard output, because the spike records them in its
 * pull request; nothing else depends on the print.
 */
@Tag("integration")
@EnvTest
class ImportedWorldIT {

    static final String WORLD_PROPERTY = "voyager.it.world";
    private static final MapId COPY = new MapId("integrationcopy");

    @TempDir
    Path root;

    @Test
    void theImportedCopyLoadsThroughMapInstances(Env env) throws IOException {
        String property = System.getProperty(WORLD_PROPERTY);
        assumeTrue(property != null && Files.isDirectory(Path.of(property)),
                "no world folder at " + WORLD_PROPERTY + "; the import integration test is skipped");
        Path shipped = Path.of(property).toAbsolutePath();
        Path worlds = Files.createDirectories(root.resolve("worlds"));

        ImportReport report = new FolderWorldImporter(shipped.getParent(), worlds, 1L << 32, 1024)
                .importWorld(new WorldFolderName(shipped.getFileName().toString()), COPY);

        System.out.println("IMPORT-REPORT layout=%s regionFiles=%d regionBytes=%d spawn=%s ignored=%s census=%s"
                .formatted(report.layout(), report.regionFiles(), report.regionBytes(), report.spawn(),
                        report.ignoredDimensions(), report.census()));
        WorldHealth health = new MapInstances(env.process().instance(), worlds).checkWorld(COPY.value());
        System.out.println("CHECK-WORLD " + health.describe());
        assertThat(report.regionFiles()).as("region files copied").isPositive();
        assertThat(worlds.resolve(COPY.value()).resolve("region")).isDirectory();
    }
}
