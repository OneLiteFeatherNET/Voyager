package net.elytrarace.tools.converter;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.elytrarace.tools.converter.exception.InvalidCourseException;
import net.elytrarace.tools.converter.legacy.LegacyCupFile;
import net.elytrarace.tools.converter.legacy.LegacyKey;
import net.elytrarace.tools.converter.legacy.LegacyMapFile;
import net.elytrarace.tools.converter.legacy.LegacyPortal;
import net.elytrarace.tools.converter.legacy.LegacyUuid;
import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.GameMode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MapConverterTest {

    private static final Gson GSON = new Gson();

    private static final LegacyUuid BLUE = new LegacyUuid(-4958764204497878197L, -6851908530790143306L);
    private static final LegacyUuid SPRINT = new LegacyUuid(72057594037944320L, -9223372036854775807L);

    private static final int[] UP = {0, 1, 0};
    private static final int[] SOUTH = {0, 0, 1};

    @Test
    void convertsEveryMapIntoTheFileTheCatalogueReadsByName(@TempDir Path root) throws IOException {
        Path source = sourceWithTwoMapsAndACup(root);

        MapConverter.convert(new ConverterOptions(
                source,
                root.resolve("out"),
                Map.of("blue-world", new Vec3(109, -62, 54), "sprint-world", new Vec3(-8, 71, 3)),
                Duration.ofMillis(46_700),
                25,
                GameMode.PRACTICE));

        // Named by the map's own name, not by its directory: the directory is "blue-world".
        JsonObject blue = read(root.resolve("out/maps/blueandred.json"));
        assertThat(blue.get("world").getAsString()).isEqualTo("blue-world");
        assertThat(blue.getAsJsonObject("spawn").get("x").getAsDouble()).isEqualTo(109.0);
        assertThat(blue.getAsJsonObject("spawn").get("y").getAsDouble()).isEqualTo(-62.0);
        assertThat(blue.get("referenceTimeSeconds").getAsDouble()).isEqualTo(46.7);
        assertThat(blue.getAsJsonArray("rings")).hasSize(3);

        // Every map gets its own spawn, so a converter that reused the first one fails here.
        JsonObject sprint = read(root.resolve("out/maps/sprint.json"));
        assertThat(sprint.getAsJsonObject("spawn").get("x").getAsDouble()).isEqualTo(-8.0);
        assertThat(sprint.getAsJsonArray("rings").get(0).getAsJsonObject().get("points").getAsInt())
                .isEqualTo(25);
    }

    @Test
    void tradesACupsMapUuidsForTheNamesTheMapFilesCarry(@TempDir Path root) throws IOException {
        Path source = sourceWithTwoMapsAndACup(root);

        MapConverter.convert(options(source, root));

        JsonObject cup = read(root.resolve("out/cups/test_cup.json"));
        assertThat(cup.get("mode").getAsString()).isEqualTo("PRACTICE");
        // Order is the rotation, and the cup lists sprint before blue — a resolution that returned
        // the maps in directory order would get this backwards.
        assertThat(cup.getAsJsonArray("mapNames")).extracting(element -> element.getAsString())
                .containsExactly("sprint", "blueandred");
    }

    @Test
    void rejectsACupPlayingAMapNoMapFileClaims(@TempDir Path root) throws IOException {
        Path source = sourceWithTwoMapsAndACup(root);
        writeCups(source, new LegacyCupFile(new LegacyKey("cup", "test_cup"),
                List.of(BLUE, new LegacyUuid(1L, 2L))));

        assertThatThrownBy(() -> MapConverter.convert(options(source, root)))
                .isInstanceOf(InvalidCourseException.class)
                .hasMessageContaining("which no map.json under the source claims");
    }

    @Test
    void writesNothingWhenAnyMapFails(@TempDir Path root) throws IOException {
        Path source = sourceWithTwoMapsAndACup(root);
        // Break the second map's indices. The first map is perfectly convertible, so a converter
        // that wrote as it went would leave a maps/ directory holding one map and a cup that plays
        // two — a rotation that is quietly one map short and looks deliberate.
        writePortals(source.resolve("maps/sprint-world"), List.of(
                LegacyPortals.ring(1, new int[] {0, 80, 0}, "STANDARD", LegacyPortals.octagon(UP, SOUTH, 2, 3)),
                LegacyPortals.ring(3, new int[] {-40, 80, 0}, "STANDARD", LegacyPortals.octagon(UP, SOUTH, 2, 3))));

        assertThatThrownBy(() -> MapConverter.convert(options(source, root)))
                .isInstanceOf(InvalidCourseException.class);

        assertThat(root.resolve("out")).doesNotExist();
    }

    @Test
    void rejectsASourceWithNoMapsAtAll(@TempDir Path root) throws IOException {
        Files.createDirectories(root.resolve("empty/maps"));

        assertThatThrownBy(() -> MapConverter.convert(options(root.resolve("empty"), root)))
                .isInstanceOf(InvalidCourseException.class)
                .hasMessageContaining("holds a map.json");
    }

    private static ConverterOptions options(Path source, Path root) {
        return new ConverterOptions(
                source,
                root.resolve("out"),
                Map.of("blue-world", new Vec3(109, -62, 54), "sprint-world", new Vec3(-8, 71, 3)),
                Duration.ofMillis(46_700),
                25,
                GameMode.PRACTICE);
    }

    private static Path sourceWithTwoMapsAndACup(Path root) throws IOException {
        Path source = root.resolve("in");
        writeMap(source.resolve("maps/blue-world"), BLUE, "blueandred", "blue-world");
        writePortals(source.resolve("maps/blue-world"), List.of(
                LegacyPortals.ring(1, new int[] {85, -54, 54}, "STANDARD", LegacyPortals.octagon(UP, SOUTH, 2, 3)),
                LegacyPortals.ring(2, new int[] {2, -31, 69}, "BOOST",
                        LegacyPortals.oppositeFirst(new int[] {1, 2, 2}, new int[] {2, 1, -2})),
                LegacyPortals.ring(3, new int[] {-86, -15, 64}, "STANDARD", LegacyPortals.octagon(UP, SOUTH, 1, 4))));

        writeMap(source.resolve("maps/sprint-world"), SPRINT, "sprint", "sprint-world");
        writePortals(source.resolve("maps/sprint-world"), List.of(
                LegacyPortals.ring(1, new int[] {0, 80, 0}, "STANDARD", LegacyPortals.octagon(UP, SOUTH, 2, 3)),
                LegacyPortals.ring(2, new int[] {-40, 80, 0}, "STANDARD", LegacyPortals.octagon(UP, SOUTH, 2, 3))));

        writeCups(source, new LegacyCupFile(new LegacyKey("cup", "test_cup"), List.of(SPRINT, BLUE)));
        return source;
    }

    private static void writeMap(Path directory, LegacyUuid uuid, String name, String world) throws IOException {
        Files.createDirectories(directory);
        write(directory.resolve("map.json"), new LegacyMapFile(uuid, new LegacyKey("map", name), world));
    }

    private static void writePortals(Path directory, List<LegacyPortal> portals) throws IOException {
        Files.createDirectories(directory);
        write(directory.resolve("portals.json"), portals);
    }

    private static void writeCups(Path source, LegacyCupFile cup) throws IOException {
        Files.createDirectories(source.resolve("cups"));
        write(source.resolve("cups/cups.json"), List.of(cup));
    }

    private static void write(Path file, Object content) throws IOException {
        Files.writeString(file, GSON.toJson(content), StandardCharsets.UTF_8);
    }

    private static JsonObject read(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
