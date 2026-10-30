package net.elytrarace.voyager.platform.rotation;

import net.elytrarace.voyager.platform.LogCapture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The state file of the cup rotation: what it keeps, what it does when it cannot read or write, and that a write
 * replaces the name whole. Each test has its own temporary directory; nothing is shared, and no clock is read.
 */
class FileCupRotationStoreTest {

    private static final String ALPHA = "elytrarace:alpha_cup";
    private static final String LEGACY = "elytrarace:legacy_cup";

    @TempDir
    Path directory;

    @Test
    void aWrittenNameIsReadBackByANewStoreOnTheSameFile() {
        Path file = directory.resolve("rotation");
        new FileCupRotationStore(file).write(LEGACY);

        assertThat(new FileCupRotationStore(file).read()).contains(LEGACY);
    }

    @Test
    void aMissingFileReadsAsNoNameWithoutAWarning() {
        try (LogCapture log = LogCapture.of(FileCupRotationStore.class)) {
            Optional<String> read = new FileCupRotationStore(directory.resolve("rotation")).read();

            assertThat(read).isEmpty();
            assertThat(log.warnings()).isEmpty();
        }
    }

    @Test
    void anUnreadableFileReadsAsNoNameAndLogsOneWarningThatNamesTheFile() throws IOException {
        // A directory where the file should be exists, and cannot be read as text: the same refusal a damaged file gets.
        Path file = Files.createDirectory(directory.resolve("rotation"));

        try (LogCapture log = LogCapture.of(FileCupRotationStore.class)) {
            Optional<String> read = new FileCupRotationStore(file).read();

            assertThat(read).isEmpty();
            assertThat(log.warnings()).singleElement().asString().contains(file.toString());
        }
    }

    @Test
    void aFailedWriteLogsOneWarningAndDoesNotThrow() {
        Path file = directory.resolve("missing-directory").resolve("rotation");

        try (LogCapture log = LogCapture.of(FileCupRotationStore.class)) {
            new FileCupRotationStore(file).write(ALPHA);

            assertThat(log.warnings()).singleElement().asString().contains(file.toString());
        }
    }

    @Test
    void anUpdateReplacesTheNameThroughATemporaryFileThatIsThenMoved() throws IOException {
        Path file = directory.resolve("rotation");
        FileCupRotationStore store = new FileCupRotationStore(file);

        store.write(ALPHA);
        store.write(LEGACY);

        assertThat(store.read()).contains(LEGACY);
        try (var entries = Files.list(directory)) {
            assertThat(entries.map(entry -> entry.getFileName().toString()).toList()).containsExactly("rotation");
        }
    }
}
