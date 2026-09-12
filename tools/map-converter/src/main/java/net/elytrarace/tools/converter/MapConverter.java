package net.elytrarace.tools.converter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

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
import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.GuideLine;
import net.elytrarace.voyager.api.race.GuidePoint;
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

    /** One server tick at 20 TPS, the divisor the old {@code cooldownMs} is converted with. */
    private static final long MILLIS_PER_TICK = 50L;

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

            List<LegacyGuide> guides = readOptionalList(directory.resolve("guides.json"), LegacyGuide.class);

            String name = value(legacyMap.name(), directory.resolve("map.json"));
            List<Ring> rings = CourseConverter.toRings(portals, options.points());
            MapDefinition map = new MapDefinition(
                    name,
                    world(legacyMap, worldDirectory),
                    options.spawnFor(worldDirectory),
                    rings,
                    options.referenceTime(),
                    boostConfig(legacyMap),
                    new GuideLine(CourseConverter.toGuidePoints(guides, portals), options.lookAheadRings(),
                            options.particleSpacing()));

            if (mapFiles.put(name, CatalogWriter.toJson(map, mapNotes(map, options, legacyMap))) != null) {
                throw new InvalidCourseException(
                        "two maps under %s convert to the name '%s'".formatted(mapsIn, name));
            }
            if (legacyMap.uuid() != null) {
                nameByUuid.put(legacyMap.uuid().toUuid(), name);
            }
            System.out.printf("map '%s' — %s rings, %s guide point(s), world '%s', spawn %s%n",
                    name, rings.size(), map.guideLine().points().size(), map.world(), map.spawn());
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

    /**
     * The boost tuning, taken from the old file where it exists and seeded where it does not.
     *
     * <p>The two halves come from different places on purpose. The <strong>cooldown</strong> is real
     * authored data — {@code ElytraraceBlueAndRed} carries {@code cooldownMs: 2000}, which is a
     * balancing decision somebody made about that course — so it is carried across, converted once
     * into the ticks a server actually counts in. The <strong>burn</strong> is missing from that file
     * entirely, because the old server derived it from a default rather than from the map, so it is
     * seeded from {@link BoostConfig#VANILLA_BURN_TICKS} — Vanilla's own deterministic lifetime for
     * the strongest rocket a player can craft — rather than from a number invented here.
     *
     * <p>Rounding the cooldown to the nearest tick is the honest conversion and it can only move the
     * value by at most half a tick; {@code BoostConfig} then refuses the result if it is not longer
     * than the burn, which is where an old file whose two numbers cannot both be honoured stops.
     */
    private static BoostConfig boostConfig(LegacyMapFile legacyMap) {
        LegacyBoostConfig legacy = legacyMap.boostConfig();
        Integer burn = legacy == null ? null : legacy.burnDurationTicks();
        Long cooldownMs = legacy == null ? null : legacy.cooldownMs();
        return new BoostConfig(
                burn == null ? BoostConfig.VANILLA_BURN_TICKS : burn,
                cooldownMs == null
                        ? ConverterOptions.DEFAULT_COOLDOWN_TICKS
                        : Math.toIntExact(Math.round(cooldownMs / (double) MILLIS_PER_TICK)));
    }

    private static List<String> mapNotes(MapDefinition map, ConverterOptions options, LegacyMapFile legacyMap) {
        List<String> notes = new ArrayList<>(List.of(
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
                boostBurnNote(map, legacyMap),
                boostCooldownNote(map, legacyMap)));
        notes.addAll(guideLineNotes(map));
        notes.add("This file is data, not code: edit the seeds above here rather than in the server.");
        notes.add("%s rings, indexed 0..%s.".formatted(map.rings().size(), map.rings().size() - 1));
        return List.copyOf(notes);
    }

    /**
     * Where the racing line came from: the guide points are authored data, the two numbers beside
     * them are not.
     *
     * <p>The split is the same one the boost tuning has, and worth saying in the file for the same
     * reason. A guide point is a position a builder placed to steer the line around terrain, carried
     * across exactly as written — nothing about it was derived and there is nothing in it to tune. How
     * far ahead the line reaches and how densely it is drawn are seeds nobody has flown: they decide
     * what a racer sees, not where the course goes, and the first person to fly the map is the one who
     * should set them.
     */
    private static List<String> guideLineNotes(MapDefinition map) {
        int count = map.guideLine().points().size();
        String points = count == 0
                ? "guideLine.points — none. This course has no guides.json, so its racing line is the "
                        + "line through its rings alone, which is legal and means no straight ring-to-ring "
                        + "segment needed bending around terrain."
                : ("guideLine.points — %s point(s), carried from the old guides.json exactly as "
                        + "written. They are not rings: nothing is scored at one, and they exist only "
                        + "to pull the line off a straight ring-to-ring segment that would cut through "
                        + "terrain. orderIndex places a point between two rings, where ring i sits at "
                        + "i * %s; more than one guide between the same pair of rings is normal.")
                        .formatted(count, GuidePoint.RING_ORDER_STRIDE);
        return List.of(
                points,
                ("guideLine.lookAheadRings — PROVISIONAL seed of %s, reasoned rather than flown: the "
                        + "line is drawn from the ring a racer is heading for to %s ring(s) past it, "
                        + "which on this course is a few seconds of flight. Drawing the whole course "
                        + "at once tells a racer nothing about which strand is next.")
                        .formatted(map.guideLine().lookAheadRings(), map.guideLine().lookAheadRings()),
                ("guideLine.particleSpacing — PROVISIONAL seed of %s block(s) between particles, the "
                        + "value the tree being replaced used for the same partial-line mode. Halving "
                        + "it doubles what one racer's stretch costs to send every refresh.")
                        .formatted(map.guideLine().particleSpacing()));
    }

    /**
     * Whether the burn was carried or seeded, said in the file rather than left to be worked out.
     * The distinction matters to the next reader for exactly the reason the reference-time note
     * exists: a seed nobody marked becomes a measurement the first time somebody trusts it.
     */
    private static String boostBurnNote(MapDefinition map, LegacyMapFile legacyMap) {
        LegacyBoostConfig legacy = legacyMap.boostConfig();
        if (legacy != null && legacy.burnDurationTicks() != null) {
            return ("boostConfig.burnDurationTicks — carried from the old map.json's own "
                    + "burnDurationTicks of %s.").formatted(legacy.burnDurationTicks());
        }
        return ("boostConfig.burnDurationTicks — PROVISIONAL seed of %s, derived rather than "
                + "measured: Vanilla's rocket lifetime is 10 * flightDuration + random(6) + "
                + "random(7), and this is that formula's deterministic core for flightDuration 3, "
                + "the strongest rocket a player can craft. The random part is dropped on purpose "
                + "— two identical boosts have to be worth the same in a race. The old file "
                + "carried no burn at all. A balancing pass edits this number.")
                .formatted(map.boostConfig().burnDurationTicks());
    }

    /**
     * Whether the cooldown was carried or seeded, and — when carried — what it was before the one
     * conversion into ticks, so the arithmetic can be checked without the old file in hand.
     */
    private static String boostCooldownNote(MapDefinition map, LegacyMapFile legacyMap) {
        LegacyBoostConfig legacy = legacyMap.boostConfig();
        if (legacy != null && legacy.cooldownMs() != null) {
            return ("boostConfig.cooldownTicks — carried from the old map.json's cooldownMs of %s, "
                    + "converted once into the ticks a server counts in: %s. It is measured from "
                    + "the tick a boost STARTS, and has to stay longer than the burn — two rockets "
                    + "burning on one racer at once is a case the simulation's boolean boost input "
                    + "cannot express.").formatted(legacy.cooldownMs(), map.boostConfig().cooldownTicks());
        }
        return ("boostConfig.cooldownTicks — PROVISIONAL seed of %s (%s s), the default the tree "
                + "being replaced used where a map carried none. It is measured from the tick a "
                + "boost STARTS, and has to stay longer than the burn — two rockets burning on one "
                + "racer at once is a case the simulation's boolean boost input cannot express.")
                .formatted(map.boostConfig().cooldownTicks(),
                        map.boostConfig().cooldownTicks() * MILLIS_PER_TICK / 1000.0);
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

    /**
     * Reads a list that a map directory may simply not have.
     *
     * <p>Only {@code guides.json} is like this, and it is not an oversight in the old data: a course
     * whose rings can be joined by straight lines without cutting through anything needs no guide
     * points, and three of the four maps in the repository have no such file. An absent file is
     * therefore an empty list, while a present but unreadable or empty one still stops the conversion
     * — "there are no guides" and "the guides could not be read" are different answers.
     */
    private static <T> List<T> readOptionalList(Path file, Class<T> element) {
        return Files.isRegularFile(file) ? readList(file, element) : List.of();
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
