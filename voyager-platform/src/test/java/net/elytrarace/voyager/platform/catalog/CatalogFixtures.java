package net.elytrarace.voyager.platform.catalog;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Map and cup JSON written by hand for the tests, never the committed files.
 *
 * <p>A test that read {@code voyager-server/src/main/resources} to check that parsing works would
 * pass for the wrong reason the day somebody edits a map: it would be measuring the file rather than
 * the parser, and a parser that silently accepted less would still be green. The committed data has
 * exactly one test of its own, in {@code voyager-server}, and it says so in its name.
 *
 * <p>The values here are deliberately not round: a centre of {@code (85, -54, 54)} and a normal of
 * {@code (-1, 0, 0)} have nothing in common with a spawn of {@code (109, -62, 54)}, so a reader that
 * put one where another belongs cannot pass.
 */
final class CatalogFixtures {

    static final String MAP = """
            {
              "name": "%s",
              "world": "%s",
              "spawn": { "x": 109.0, "y": -62.0, "z": 54.0 },
              "referenceTimeSeconds": 46.7,
              "rings": [
                {
                  "index": 0,
                  "center": { "x": 85.0, "y": -54.0, "z": 54.0 },
                  "normal": { "x": -1.0, "y": 0.0, "z": 0.0 },
                  "radius": 3.605551275463989,
                  "points": 10,
                  "type": "STANDARD"
                },
                {
                  "index": 1,
                  "center": { "x": 2.0, "y": -31.0, "z": 69.0 },
                  "normal": { "x": -0.6666666666666666, "y": 0.6666666666666666, "z": -0.3333333333333333 },
                  "radius": 3.0,
                  "points": 25,
                  "type": "BOOST"
                }
              ],
              "notes": [ "a note the reader must ignore" ]
            }
            """;

    static final String CUP = """
            {
              "name": "%s",
              "mode": "%s",
              "mapNames": [ %s ],
              "notes": [ "a note the reader must ignore" ]
            }
            """;

    private CatalogFixtures() {
    }

    /** Writes a valid map definition, whose declared name is deliberately not the filename. */
    static Path map(Path directory, String fileName, String name, String world) {
        return write(directory, fileName, MAP.formatted(name, world));
    }

    /** Writes a valid cup definition, whose declared name is deliberately not the filename. */
    static Path cup(Path directory, String fileName, String name, String mode, String... mapNames) {
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < mapNames.length; i++) {
            names.append(i == 0 ? "" : ", ").append('"').append(mapNames[i]).append('"');
        }
        return write(directory, fileName, CUP.formatted(name, mode, names));
    }

    static Path write(Path directory, String fileName, String content) {
        try {
            Files.createDirectories(directory);
            Path file = directory.resolve(fileName);
            Files.writeString(file, content, StandardCharsets.UTF_8);
            return file;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
