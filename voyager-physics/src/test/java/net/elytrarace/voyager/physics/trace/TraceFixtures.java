package net.elytrarace.voyager.physics.trace;

import net.elytrarace.voyager.physics.trace.exception.InvalidTraceFixtureException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Finds the recorded fixtures on the test classpath.
 *
 * <p>Discovery, not a list: every {@code .json} under {@code /traces/} is a fixture, so recording a
 * tenth profile means committing one file and nothing else. The alternative — an array of profile
 * names in the test — is a second place for the set of fixtures to live, and the failure mode of the
 * two disagreeing is a fixture that is never replayed while the suite stays green.
 *
 * <p>An empty directory, or none, is a failure rather than a vacuous pass: a parameterised test with
 * no arguments would otherwise report success for a suite that replayed nothing. JUnit refuses an
 * empty {@code @MethodSource} on its own, and {@link #load()} refuses first, with a message that
 * says where it looked.
 */
abstract class TraceFixtures {

    private static final String DIRECTORY = "/traces";

    private TraceFixtures() {
    }

    /** Every fixture under {@code /traces/}, ordered by profile name so failures read predictably. */
    static List<RecordedGlide> load() {
        URL directory = TraceFixtures.class.getResource(DIRECTORY);
        if (directory == null) {
            throw new InvalidTraceFixtureException(
                    "no %s directory on the test classpath — the recorded fixtures live in "
                            + "voyager-physics/src/test/resources%s".formatted(DIRECTORY, DIRECTORY));
        }

        Path path = toPath(directory);
        try (Stream<Path> files = Files.list(path)) {
            List<RecordedGlide> fixtures = files
                    .filter(file -> file.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(file -> file.getFileName().toString()))
                    .map(TraceFixtures::read)
                    .toList();
            if (fixtures.isEmpty()) {
                throw new InvalidTraceFixtureException(
                        "%s contains no .json fixture — a parity suite with nothing to replay would "
                                + "pass vacuously".formatted(path));
            }
            return fixtures;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static RecordedGlide read(Path file) {
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            return RecordedGlide.of(TraceFixtureLoader.fromJson(json));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        } catch (InvalidTraceFixtureException exception) {
            throw new InvalidTraceFixtureException(
                    "%s is not a valid fixture: %s".formatted(file.getFileName(), exception.getMessage()),
                    exception);
        }
    }

    private static Path toPath(URL directory) {
        try {
            return Path.of(directory.toURI());
        } catch (URISyntaxException | IllegalArgumentException exception) {
            throw new InvalidTraceFixtureException(
                    ("%s is not a directory on the file system (%s). The fixtures are read from the "
                            + "module's own test resources, never from a packaged jar.")
                            .formatted(directory, exception.getMessage()),
                    exception);
        }
    }
}
