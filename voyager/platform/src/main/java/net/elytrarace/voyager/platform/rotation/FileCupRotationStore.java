package net.elytrarace.voyager.platform.rotation;

import net.elytrarace.voyager.race.rotation.CupRotationStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * The armed cup kept in one file: the adapter of {@link CupRotationStore} the server uses when
 * {@code VOYAGER_CUP_ROTATION_STATE} names a file.
 *
 * <p>A write goes to a temporary file in the same directory, which is then moved over the state file, so a reader sees
 * the old name or the new one and never half of a name. A file that is missing reads as no name without a warning: the
 * first boot of a rotation has none. A file that cannot be read, or a write that fails, logs one warning that names the
 * file and carries on: the rotation keeps its position in memory, and a restart begins again at the first cup.
 */
public final class FileCupRotationStore implements CupRotationStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileCupRotationStore.class);

    private final Path file;

    /** @param file the state file; its directory must exist, which the configuration check refuses otherwise */
    public FileCupRotationStore(Path file) {
        this.file = file;
    }

    @Override
    public Optional<String> read() {
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            String name = Files.readString(file, StandardCharsets.UTF_8).strip();
            return name.isEmpty() ? Optional.empty() : Optional.of(name);
        } catch (IOException | RuntimeException failure) {
            LOGGER.warn("cannot read the cup rotation state file {}; the rotation starts at its first cup: {}",
                    file, failure.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void write(String cup) {
        Path temporary = null;
        try {
            Path directory = file.toAbsolutePath().getParent();
            temporary = Files.createTempFile(directory, file.getFileName().toString(), ".tmp");
            Files.writeString(temporary, cup + "\n", StandardCharsets.UTF_8);
            moveOver(temporary);
        } catch (IOException | RuntimeException failure) {
            LOGGER.warn("cannot write the cup rotation state file {}; the rotation continues in memory: {}",
                    file, failure.getMessage());
            deleteQuietly(temporary);
        }
    }

    private void moveOver(Path temporary) throws IOException {
        try {
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteQuietly(Path temporary) {
        if (temporary == null) {
            return;
        }
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException ignored) {
            // The temporary file is left behind only when the disk refuses it too; the warning above already says why.
        }
    }
}
