package net.elytrarace.tools.converter;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.elytrarace.tools.converter.exception.InvalidCourseException;
import net.elytrarace.tools.converter.legacy.LegacyBoostConfig;
import net.elytrarace.tools.converter.legacy.LegacyCupFile;
import net.elytrarace.tools.converter.legacy.LegacyGuide;
import net.elytrarace.tools.converter.legacy.LegacyKey;
import net.elytrarace.tools.converter.legacy.LegacyMapFile;
import net.elytrarace.tools.converter.legacy.LegacyPortal;
import net.elytrarace.tools.converter.legacy.LegacyUuid;
import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.GameMode;
import net.elytrarace.voyager.api.race.exception.InvalidBoostConfigException;

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
                GameMode.PRACTICE,
                3,
                1.25));

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

    /**
     * The two shapes of the old {@code boostConfig}, converted side by side in one run.
     *
     * <p>{@code blue-world} carries the real course's shape — a {@code cooldownMs} and no burn at all
     * — so its cooldown is carried across in ticks and its burn is seeded from Vanilla.
     * {@code sprint-world} carries the synthetic maps' shape, both numbers, and both differ from the
     * other map's: a converter that read one map's tuning into the other file, or that seeded both,
     * cannot produce all four of these numbers.
     */
    @Test
    void carriesEachMapsOwnBoostTuningAcrossAndSeedsOnlyWhatIsMissing(@TempDir Path root) throws IOException {
        Path source = sourceWithTwoMapsAndACup(root);

        MapConverter.convert(options(source, root));

        JsonObject blue = read(root.resolve("out/maps/blueandred.json")).getAsJsonObject("boostConfig");
        assertThat(blue.get("cooldownTicks").getAsInt())
                .describedAs("2000 ms is 40 ticks")
                .isEqualTo(40);
        assertThat(blue.get("burnDurationTicks").getAsInt())
                .describedAs("no burn in the old file, so Vanilla's deterministic core")
                .isEqualTo(BoostConfig.VANILLA_BURN_TICKS);

        JsonObject sprint = read(root.resolve("out/maps/sprint.json")).getAsJsonObject("boostConfig");
        assertThat(sprint.get("burnDurationTicks").getAsInt())
                .describedAs("carried, not seeded — 24 is not the Vanilla default")
                .isEqualTo(24);
        assertThat(sprint.get("cooldownTicks").getAsInt())
                .describedAs("3500 ms is 70 ticks")
                .isEqualTo(70);
    }

    /**
     * A map file with no {@code boostConfig} block at all — the shape a hand-written old file could
     * take — converts on the two seeds rather than failing. The result is still a legal pairing,
     * which is the thing worth asserting: 80 must outlast 30.
     */
    @Test
    void seedsBothNumbersForAMapFileThatCarriesNoBoostBlockAtAll(@TempDir Path root) throws IOException {
        Path source = sourceWithTwoMapsAndACup(root);
        writeMap(source.resolve("maps/blue-world"), BLUE, "blueandred", "blue-world", null);

        MapConverter.convert(options(source, root));

        JsonObject blue = read(root.resolve("out/maps/blueandred.json")).getAsJsonObject("boostConfig");
        assertThat(blue.get("burnDurationTicks").getAsInt()).isEqualTo(BoostConfig.VANILLA_BURN_TICKS);
        assertThat(blue.get("cooldownTicks").getAsInt()).isEqualTo(ConverterOptions.DEFAULT_COOLDOWN_TICKS);
    }

    /**
     * An old file whose two numbers cannot both be honoured stops the conversion rather than being
     * quietly adjusted. A cooldown of 1000 ms is 20 ticks against a 24-tick burn, which would let one
     * racer hold two burning rockets — the case the simulation's boolean boost input cannot express.
     */
    @Test
    void refusesAnOldFileWhoseCooldownWouldNotOutlastItsBurn(@TempDir Path root) throws IOException {
        Path source = sourceWithTwoMapsAndACup(root);
        writeMap(source.resolve("maps/sprint-world"), SPRINT, "sprint", "sprint-world",
                new LegacyBoostConfig(24, 1_000L));

        assertThatThrownBy(() -> MapConverter.convert(options(source, root)))
                .isInstanceOf(InvalidBoostConfigException.class)
                .hasMessageContaining("cannot express");
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

    /**
     * The guide points cross into the new format, and a map with no guide file crosses as a course
     * whose line is its rings alone.
     *
     * <p>Both cases are in one run on purpose: {@code blue-world} has a {@code guides.json} and
     * {@code sprint-world} does not, which is the split the real repository has — one converted course
     * with guides and three synthetic ones without. A converter that read the first map's guide file
     * for every map, or that refused a map without one, fails on one of these two maps.
     */
    @Test
    void carriesTheGuidePointsAcrossAndLeavesAMapWithNoGuideFileWithNone(@TempDir Path root) throws IOException {
        Path source = sourceWithTwoMapsAndACup(root);

        MapConverter.convert(options(source, root));

        JsonObject blue = read(root.resolve("out/maps/blueandred.json")).getAsJsonObject("guideLine");
        // Both points, in order index order, whatever order the file listed them in.
        assertThat(blue.getAsJsonArray("points")).hasSize(2);
        assertThat(blue.getAsJsonArray("points").get(0).getAsJsonObject().get("orderIndex").getAsInt())
                .isEqualTo(150);
        assertThat(blue.getAsJsonArray("points").get(0).getAsJsonObject()
                .getAsJsonObject("position").get("x").getAsDouble()).isEqualTo(-41.0);
        assertThat(blue.getAsJsonArray("points").get(1).getAsJsonObject().get("orderIndex").getAsInt())
                .isEqualTo(175);
        // Seeded from the options, not from a constant: 3 and 1.25 are neither of the converter's
        // own defaults.
        assertThat(blue.get("lookAheadRings").getAsInt()).isEqualTo(3);
        assertThat(blue.get("particleSpacing").getAsDouble()).isEqualTo(1.25);

        JsonObject sprint = read(root.resolve("out/maps/sprint.json")).getAsJsonObject("guideLine");
        assertThat(sprint.getAsJsonArray("points")).isEmpty();
        assertThat(sprint.get("lookAheadRings").getAsInt()).isEqualTo(3);
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
                GameMode.PRACTICE,
                3,
                1.25);
    }

    private static Path sourceWithTwoMapsAndACup(Path root) throws IOException {
        Path source = root.resolve("in");
        // The real ElytraraceBlueAndRed's shape: a cooldown and no burn at all.
        writeMap(source.resolve("maps/blue-world"), BLUE, "blueandred", "blue-world",
                new LegacyBoostConfig(null, 2_000L));
        writePortals(source.resolve("maps/blue-world"), List.of(
                LegacyPortals.ring(1, new int[] {85, -54, 54}, "STANDARD", LegacyPortals.octagon(UP, SOUTH, 2, 3)),
                LegacyPortals.ring(2, new int[] {2, -31, 69}, "BOOST",
                        LegacyPortals.oppositeFirst(new int[] {1, 2, 2}, new int[] {2, 1, -2})),
                LegacyPortals.ring(3, new int[] {-86, -15, 64}, "STANDARD", LegacyPortals.octagon(UP, SOUTH, 1, 4))));

        // Two guides between the same pair of rings, the later one written first — the shape the real
        // guides.json has at order indices 2450 and 2475, and the shape that loses a point if a
        // converter keeps one guide per gap or a reader trusts file order.
        writeGuides(source.resolve("maps/blue-world"), List.of(
                new LegacyGuide(175, -20.5, -40.25, 61.75),
                new LegacyGuide(150, -41.0, -44.5, 58.25)));

        // The synthetic maps' shape: both numbers, and both different from the other map's, so a
        // converter that read one map's tuning into the other file fails here rather than passing.
        writeMap(source.resolve("maps/sprint-world"), SPRINT, "sprint", "sprint-world",
                new LegacyBoostConfig(24, 3_500L));
        writePortals(source.resolve("maps/sprint-world"), List.of(
                LegacyPortals.ring(1, new int[] {0, 80, 0}, "STANDARD", LegacyPortals.octagon(UP, SOUTH, 2, 3)),
                LegacyPortals.ring(2, new int[] {-40, 80, 0}, "STANDARD", LegacyPortals.octagon(UP, SOUTH, 2, 3))));

        writeCups(source, new LegacyCupFile(new LegacyKey("cup", "test_cup"), List.of(SPRINT, BLUE)));
        return source;
    }

    private static void writeMap(Path directory, LegacyUuid uuid, String name, String world,
            LegacyBoostConfig boost) throws IOException {
        Files.createDirectories(directory);
        write(directory.resolve("map.json"), new LegacyMapFile(uuid, new LegacyKey("map", name), world, boost));
    }

    private static void writeGuides(Path directory, List<LegacyGuide> guides) throws IOException {
        Files.createDirectories(directory);
        write(directory.resolve("guides.json"), guides);
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
