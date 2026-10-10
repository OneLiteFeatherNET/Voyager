package net.elytrarace.fitness;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fails when the frozen slice-boundary baseline holds a violation of a migration item that has been closed
 * (design D6 of the change extract-cup-slice).
 *
 * <p>A frozen rule fails only on a violation the baseline lacks, so a closed violation that came back would
 * pass silently if the baseline still held it. This test reads the committed store and names every stored line
 * that points into the source file of a closed item.
 *
 * <p>Each task that closes an item appends the item's source file names to {@link #CLOSED_SOURCES}, and removes
 * the item's lines from the store in the same change. The list starts empty.
 *
 * <p>Ordering: a local run shrinks the store in {@link SliceBoundaryRulesTest}, and JUnit may run this test
 * first. On the one local run that still holds the stale lines, this test fails and names them; the next run
 * passes, because the store has been shrunk. On CI the store cannot shrink, so the same failure is the correct
 * one and the stale lines must be committed away.
 */
class ClosedViolationsAreGoneTest {

    /** The store that {@link SliceBoundaryRulesTest} freezes its violations in, relative to this module. */
    private static final Path STORE = Path.of("src/test/resources/archunit_store");

    /** Source file names of the migration items closed so far, such as {@code CupStandings.java}. */
    private static final List<String> CLOSED_SOURCES = List.of("CupStanding.java", "CupStandings.java");

    @Test
    void noStoredViolationPointsIntoAClosedItemsSource() throws IOException {
        assertThat(Files.isDirectory(STORE))
                .describedAs("the frozen store %s must exist", STORE.toAbsolutePath())
                .isTrue();

        List<String> offending = new ArrayList<>();
        try (Stream<Path> files = Files.list(STORE)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".txt")).sorted().toList()) {
                for (String line : Files.readAllLines(file)) {
                    for (String source : CLOSED_SOURCES) {
                        if (line.contains("(" + source + ":")) {
                            offending.add(file.getFileName() + ": " + line);
                        }
                    }
                }
            }
        }

        assertThat(offending)
                .describedAs("stored violations of a closed migration item must be removed from the store")
                .isEmpty();
    }
}
