package net.elytrarace.tools.converter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import net.elytrarace.tools.converter.exception.InvalidCourseException;
import net.elytrarace.tools.converter.legacy.LegacyCupFile;
import net.elytrarace.tools.converter.legacy.LegacyKey;
import net.elytrarace.tools.converter.legacy.LegacyMapFile;
import net.elytrarace.tools.converter.legacy.LegacyPortal;
import net.elytrarace.tools.converter.legacy.LegacyUuid;
import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.Ring;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * The one-shot conversion of the old map and cup data into the format the rebuild reads.
 *
 * <p><strong>Why a conversion and not a reader.</strong> The old format cannot express three of the
 * five things a {@code MapDefinition} is made of — there is no spawn, no reference time, no stored
 * normal and no radius, the coordinates are integers, and a cup names its maps by UUID rather than
 * by name. A runtime reader would put all of that derivation on the server's boot path forever, and
 * would have to answer every ambiguity in it silently, every time. Converting once puts the answers
 * in a file a person can read and a designer can edit.
 *
 * <p><strong>Throwaway, and outside {@code voyager-*} for that reason</strong> — the same place and
 * the same reasoning as {@code tools/trace-recorder}. It runs once per source file, its output is
 * committed, and it is deleted when the tree being replaced is.
 *
 * <p>Run it with
 * {@snippet lang = "shell":
 * ./gradlew :tools:map-converter:run --args="--source run/run/data --out voyager-server/src/main/resources \
 *     --spawn ElytraraceBlueAndRed=109,-62,54"
 *}
 */
public final class MapConverter {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private MapConverter() {
    }

    public static void main(String[] arguments) {
        ConverterOptions options;
        try {
            options = ConverterOptions.parse(List.of(arguments));
        } catch (IllegalArgumentException exception) {
            System.err.println(exception.getMessage());
            System.err.println();
            System.err.print(ConverterOptions.USAGE);
            System.exit(2);
            return;
        }
        convert(options);
    }

    /**
     * Converts every map and cup under {@code options.source()} into {@code options.out()}.
     *
     * <p>Nothing is written until every map has converted. A half-written output directory is worse
     * than none: the catalogue reads a directory eagerly and would come up on whatever happened to
     * land before the failure, which is a shorter cup rotation that looks deliberate.
     *
     * @param options what to convert, and the values the old data does not carry
     * @throws InvalidCourseException if any map or cup cannot be converted without guessing
     */
    static void convert(ConverterOptions options) {
        Path mapsIn = options.source().resolve("maps");
        Path cupsIn = options.source().resolve("cups").resolve("cups.json");

        Map<UUID, String> nameByUuid = new LinkedHashMap<>();
        Map<String, JsonObject> mapFiles = new LinkedHashMap<>();

        for (Path directory : mapDirectories(mapsIn)) {
            String worldDirectory = directory.getFileName().toString();
            LegacyMapFile legacyMap = read(directory.resolve("map.json"), LegacyMapFile.class);
            List<LegacyPortal> portals = readList(directory.resolve("portals.json"), LegacyPortal.class);

            String name = value(legacyMap.name(), directory.resolve("map.json"));
            List<Ring> rings = CourseConverter.toRings(portals, options.points());
            MapDefinition map = new MapDefinition(
                    name,
                    world(legacyMap, worldDirectory),
                    options.spawnFor(worldDirectory),
                    rings,
                    options.referenceTime());

            if (mapFiles.put(name, CatalogWriter.toJson(map, mapNotes(map, options))) != null) {
                throw new InvalidCourseException(
                        "two maps under %s convert to the name '%s'".formatted(mapsIn, name));
            }
            if (legacyMap.uuid() != null) {
                nameByUuid.put(legacyMap.uuid().toUuid(), name);
            }
            System.out.printf("map '%s' — %s rings, world '%s', spawn %s%n",
                    name, rings.size(), map.world(), map.spawn());
        }
        if (mapFiles.isEmpty()) {
            throw new InvalidCourseException("no map directory under %s holds a map.json".formatted(mapsIn));
        }

        Map<String, JsonObject> cupFiles = new LinkedHashMap<>();
        for (LegacyCupFile legacyCup : readList(cupsIn, LegacyCupFile.class)) {
            String name = value(legacyCup.name(), cupsIn);
            CupDefinition cup = new CupDefinition(name, mapNamesOf(legacyCup, nameByUuid, cupsIn), options.mode());
            if (cupFiles.put(name, CatalogWriter.toJson(cup, cupNotes(options))) != null) {
                throw new InvalidCourseException("two cups in %s convert to the name '%s'".formatted(cupsIn, name));
            }
            System.out.printf("cup '%s' — %s, maps %s%n", name, cup.mode(), cup.mapNames());
        }

        writeAll(options.out().resolve("maps"), mapFiles);
        writeAll(options.out().resolve("cups"), cupFiles);
    }

    /**
     * The maps a cup plays, by name.
     *
     * <p>A UUID with no map behind it stops the conversion. It is the only moment the two files are
     * ever read together — after this the cup carries names, and a name that resolves to nothing is
     * the catalogue's problem rather than a silently shorter rotation.
     */
    private static List<String> mapNamesOf(LegacyCupFile cup, Map<UUID, String> nameByUuid, Path cupsFile) {
        List<LegacyUuid> maps = cup.maps();
        if (maps == null || maps.isEmpty()) {
            throw new InvalidCourseException("cup '%s' in %s plays no maps".formatted(cup.name(), cupsFile));
        }
        List<String> names = new ArrayList<>(maps.size());
        for (LegacyUuid uuid : maps) {
            String name = nameByUuid.get(uuid.toUuid());
            if (name == null) {
                throw new InvalidCourseException(
                        ("cup '%s' in %s plays the map %s, which no map.json under the source claims; "
                                + "the rotation would silently lose a map")
                                .formatted(value(cup.name(), cupsFile), cupsFile, uuid.toUuid()));
            }
            names.add(name);
        }
        return names;
    }

    private static List<String> mapNotes(MapDefinition map, ConverterOptions options) {
        return List.of(
                "Converted from the old map.json/portals.json format by tools/map-converter. Every "
                        + "ring normal below was derived from the recorded rim points and oriented by "
                        + "the bisector of the flight path; see CourseConverter for why that rule and "
                        + "not the three simpler ones.",
                "spawn — read from the world's level.dat (SpawnX/SpawnY/SpawnZ), not derived.",
                ("referenceTimeSeconds — PROVISIONAL seed of %s s, not a measurement. It decides medal "
                        + "tiers only. Replace it with a real lap once someone has flown one.")
                        .formatted(options.referenceTime().toMillis() / 1000.0),
                ("points — PROVISIONAL seed of %s on every ring, carried over from the loader in the "
                        + "tree being replaced. The old data records no per-ring variation, so there is "
                        + "nothing here to preserve; a balancing pass edits this file.")
                        .formatted(options.points()),
                "This file is data, not code: edit the seeds above here rather than in the server.",
                "%s rings, indexed 0..%s.".formatted(map.rings().size(), map.rings().size() - 1));
    }

    private static List<String> cupNotes(ConverterOptions options) {
        return List.of(
                "Converted from the old cups.json by tools/map-converter. The old format named its "
                        + "maps by UUID; those were traded for map names against the map.json files "
                        + "beside them, and a UUID with no map behind it stopped the conversion.",
                ("mode — PROVISIONAL seed of %s. The old format carries no game mode at all, because a "
                        + "cup was always played the same way.").formatted(options.mode()));
    }

    private static String world(LegacyMapFile legacyMap, String worldDirectory) {
        String world = legacyMap.world();
        if (world == null || world.isBlank()) {
            throw new InvalidCourseException(
                    "the map in directory '%s' names no world".formatted(worldDirectory));
        }
        return world;
    }

    private static String value(@Nullable LegacyKey key, Path file) {
        if (key == null || key.value() == null || key.value().isBlank()) {
            throw new InvalidCourseException("%s carries no name".formatted(file));
        }
        // The namespace is dropped: it is "map"/"cup" in the real data and "voyager" in the alpha
        // data, it never distinguishes two entries, and it would put a colon into every filename.
        // A collision would not pass silently — the caller rejects a repeated name.
        return key.value();
    }

    private static List<Path> mapDirectories(Path mapsIn) {
        if (!Files.isDirectory(mapsIn)) {
            throw new InvalidCourseException("%s is not a directory".formatted(mapsIn));
        }
        try (Stream<Path> entries = Files.list(mapsIn)) {
            return entries.filter(Files::isDirectory)
                    .filter(directory -> Files.isRegularFile(directory.resolve("map.json")))
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            throw new UncheckedIOException("cannot list %s".formatted(mapsIn), exception);
        }
    }

    private static <T> T read(Path file, Class<T> type) {
        T parsed = GSON.fromJson(readString(file), type);
        if (parsed == null) {
            throw new InvalidCourseException("%s is empty".formatted(file));
        }
        return parsed;
    }

    private static <T> List<T> readList(Path file, Class<T> element) {
        List<T> parsed = GSON.fromJson(readString(file), TypeToken.getParameterized(List.class, element).getType());
        if (parsed == null || parsed.isEmpty()) {
            throw new InvalidCourseException("%s holds no entries".formatted(file));
        }
        return parsed;
    }

    private static String readString(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("cannot read %s".formatted(file), exception);
        }
    }

    private static void writeAll(Path directory, Map<String, JsonObject> files) {
        try {
            Files.createDirectories(directory);
            for (Map.Entry<String, JsonObject> file : files.entrySet()) {
                Path target = directory.resolve("%s.json".formatted(file.getKey()));
                Files.writeString(target, GSON.toJson(file.getValue()) + "\n", StandardCharsets.UTF_8);
                System.out.printf("wrote %s%n", target);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("cannot write into %s".formatted(directory), exception);
        }
    }
}
