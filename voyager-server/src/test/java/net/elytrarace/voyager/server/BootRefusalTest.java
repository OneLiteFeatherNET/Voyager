package net.elytrarace.voyager.server;

import net.elytrarace.voyager.platform.catalog.exception.MalformedCatalogFileException;
import net.elytrarace.voyager.platform.catalog.exception.UnreadableCatalogException;
import net.elytrarace.voyager.platform.catalog.exception.UnresolvedCupMapException;
import net.elytrarace.voyager.server.config.ServerSettings;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What the server says when its catalogue refuses to come up. The boot path is
 * {@link VoyagerServer#openGraph}, which is what {@code main} calls; these tests drive that same
 * call, so the message an operator reads is the message these assert.
 *
 * <p>Every test writes its own data directory in its own {@code @TempDir}. The shipped catalogue is
 * copied in where a test needs one valid file, and the broken file is written by the test itself, so
 * each test names the one thing it is about.
 */
@EnvTest
class BootRefusalTest {

    @TempDir
    Path tempDir;

    @Test
    void refusesBootWithTheFirstMalformedMapFileInSortedOrder(Env env) throws IOException {
        Path data = dataDirectory();
        write(data.resolve("maps/b-broken.json"), "");
        write(data.resolve("maps/a-broken.json"), "");
        write(data.resolve("cups/test_cup.json"), CUP_NAMING_A_MAP);

        assertThatThrownBy(() -> VoyagerServer.openGraph(settings(data)))
                .satisfies(refusal -> assertThat(causeOf(refusal, MalformedCatalogFileException.class))
                        .hasMessage("%s is not a valid definition: the file is empty"
                                .formatted(data.resolve("maps/a-broken.json"))));
    }

    @Test
    void refusesBootNamingTheCupsDirectoryWhenItIsMissing(Env env) throws IOException {
        Path data = dataDirectory();
        // The shipped map is valid, so the only thing wrong with the catalogue is the missing cups/.

        assertThatThrownBy(() -> VoyagerServer.openGraph(settings(data)))
                .satisfies(refusal -> assertThat(causeOf(refusal, UnreadableCatalogException.class))
                        .hasMessage("the cup catalogue directory %s does not exist"
                                .formatted(data.resolve("cups"))));
    }

    @Test
    void refusesBootWithTheUnresolvedCupMapExceptionWhenACupNamesAMapNothingProvides(Env env) throws IOException {
        Path data = dataDirectory();
        write(data.resolve("cups/test_cup.json"), CUP_NAMING_A_MISSING_MAP);

        assertThatThrownBy(() -> VoyagerServer.openGraph(settings(data)))
                .satisfies(refusal -> assertThat(causeOf(refusal, UnresolvedCupMapException.class))
                        .hasMessage("1 cup entry does name a map no map definition provides: "
                                + "cup 'test_cup' plays 'no-such-map'"));
    }

    @Test
    void refusesBootWithTheMalformedMapAndNotTheDanglingCupReference(Env env) throws IOException {
        // The cup also names a map that does not exist. Both faults are present; the refusal is the
        // map file, and the cross-catalogue check is never reached.
        Path data = dataDirectory();
        write(data.resolve("maps/a-broken.json"), "");
        write(data.resolve("cups/test_cup.json"), CUP_NAMING_A_MISSING_MAP);

        assertThatThrownBy(() -> VoyagerServer.openGraph(settings(data)))
                .satisfies(refusal -> {
                    assertThat(causeOf(refusal, MalformedCatalogFileException.class))
                            .hasMessageContaining("a-broken.json");
                    assertThat(causeOf(refusal, UnresolvedCupMapException.class)).isNull();
                });
    }

    /**
     * Intentional change, scope-cup-validation: a malformed cup file that is not the played cup no longer
     * stops boot, so the malformed map file is the refusal here. The cup is not the one named by
     * {@code test_cup}, so it is only reported as skipped (see CupBootValidationTest).
     */
    @Test
    void refusesBootWithTheMalformedMapBeforeAnUnplayedBrokenCup(Env env) throws IOException {
        Path data = dataDirectory();
        write(data.resolve("maps/a-broken.json"), "");
        write(data.resolve("cups/a-broken.json"), "");

        assertThatThrownBy(() -> VoyagerServer.openGraph(settings(data)))
                .satisfies(refusal -> assertThat(causeOf(refusal, MalformedCatalogFileException.class))
                        .hasMessage("%s is not a valid definition: the file is empty"
                                .formatted(data.resolve("maps/a-broken.json"))));
    }

    /**
     * The data directory with the shipped map in it and no cups yet. Each test adds or replaces the
     * one file it is about, so the other half of the catalogue is always valid.
     */
    private Path dataDirectory() throws IOException {
        Path data = tempDir.resolve("data");
        ShippedCatalogue.copyMapsInto(data);
        return data;
    }

    private ServerSettings settings(Path data) throws IOException {
        Path worlds = tempDir.resolve("worlds");
        Files.createDirectories(worlds);
        return new ServerSettings("127.0.0.1", 25571, data, worlds, Optional.of("test_cup"), false);
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    /**
     * The first exception of {@code type} in the chain that a refusal wraps, or {@code null}. The
     * graph builder wraps a bean's exception, so the assertion looks for the type rather than
     * depending on how many layers sit above it.
     */
    private static <T extends Throwable> T causeOf(Throwable refusal, Class<T> type) {
        for (Throwable cause = refusal; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return type.cast(cause);
            }
        }
        return null;
    }

    private static final String CUP_NAMING_A_MAP = """
            {"name": "test_cup", "mode": "RACE", "mapNames": ["elytraraceblueandred"]}
            """;

    private static final String CUP_NAMING_A_MISSING_MAP = """
            {"name": "test_cup", "mode": "RACE", "mapNames": ["no-such-map"]}
            """;
}
