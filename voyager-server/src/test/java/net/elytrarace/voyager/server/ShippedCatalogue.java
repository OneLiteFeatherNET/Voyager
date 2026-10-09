package net.elytrarace.voyager.server;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Copies the committed map and cup catalogue into a test's own data directory.
 *
 * <p>The graph is built against the same files a run installs (the build's {@code prepareRunData}
 * task), so a test that passes here is passing against the data the server actually boots with.
 * Each test copies into its own {@code @TempDir}; nothing is shared between tests and the classpath
 * is only read.
 */
final class ShippedCatalogue {

    private static final String MAP = "elytraraceblueandred.json";
    private static final String CUP = "test_cup.json";

    private ShippedCatalogue() {
    }

    /** Copies the committed map definitions into {@code dataDirectory/maps}. */
    static void copyMapsInto(Path dataDirectory) throws IOException {
        copy("/maps/" + MAP, dataDirectory.resolve("maps").resolve(MAP));
    }

    /** Copies the committed cup definitions into {@code dataDirectory/cups}. */
    static void copyCupsInto(Path dataDirectory) throws IOException {
        copy("/cups/" + CUP, dataDirectory.resolve("cups").resolve(CUP));
    }

    private static void copy(String resource, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        try (InputStream in = ShippedCatalogue.class.getResourceAsStream(resource)) {
            assertThat(in).as("classpath resource %s", resource).isNotNull();
            Files.copy(in, target);
        }
    }
}
