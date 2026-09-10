package net.elytrarace.fitness;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Rule 5 — nullability is the exception, not the default. */
class NullabilityConventionTest {

    @Test
    void everyPackageWithSourcesDeclaresNotNullByDefault() throws IOException {
        List<Path> roots = Arrays.stream(System.getProperty("voyager.sourceRoots", "").split(File.pathSeparator))
                .filter(entry -> !entry.isBlank())
                .map(Path::of)
                .toList();

        assertThat(roots)
                .as("the build must supply voyager.sourceRoots; an empty list would pass vacuously")
                .isNotEmpty();

        List<String> offenders = new ArrayList<>();
        for (Path root : roots) {
            try (Stream<Path> directories = Files.walk(root)) {
                directories.filter(Files::isDirectory).forEach(directory -> {
                    if (!containsSources(directory)) {
                        return;
                    }
                    Path packageInfo = directory.resolve("package-info.java");
                    if (!Files.exists(packageInfo)) {
                        offenders.add(root.relativize(directory) + " — no package-info.java");
                        return;
                    }
                    if (!readString(packageInfo).contains("@NotNullByDefault")) {
                        offenders.add(root.relativize(directory) + " — package-info.java lacks @NotNullByDefault");
                    }
                });
            }
        }

        assertThat(offenders).as("packages violating rule 5").isEmpty();
    }

    private static boolean containsSources(Path directory) {
        try (Stream<Path> files = Files.list(directory)) {
            return files.anyMatch(file -> {
                String name = file.getFileName().toString();
                return name.endsWith(".java") && !name.equals("package-info.java");
            });
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static String readString(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
