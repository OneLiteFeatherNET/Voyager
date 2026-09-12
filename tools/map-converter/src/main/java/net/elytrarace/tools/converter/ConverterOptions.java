package net.elytrarace.tools.converter;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.GameMode;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What one run of the converter was asked to do.
 *
 * <p>The spawn is per map and has no default on purpose. It is the one seeded value that cannot be
 * invented from the rings: for {@code ElytraraceBlueAndRed} it comes from the world's
 * {@code level.dat}, which is authored data, and for a map whose world is not on disk there is
 * nothing to read. A converter that placed a player "somewhere behind the first ring" would write a
 * guess into a field that looks measured, so a map with no {@code --spawn} is refused instead.
 */
public record ConverterOptions(
        Path source,
        Path out,
        Map<String, Vec3> spawns,
        Duration referenceTime,
        int points,
        GameMode mode) {

    /** Seeded, and provisional: 1587.9 blocks of climbing course at a realistic pace. See the brief. */
    public static final Duration DEFAULT_REFERENCE_TIME = Duration.ofSeconds(60);

    /**
     * Seeded, and provisional: the value the loader in the tree being replaced awarded every ring,
     * carried forward so this conversion changes the data's shape and not its balance.
     */
    public static final int DEFAULT_POINTS = 10;

    /** The usage text, printed on any argument this cannot make sense of. */
    public static final String USAGE = """
            Usage: map-converter --source <dir> --out <dir> --spawn <world-dir>=<x>,<y>,<z> [...]
                                 [--reference-time-seconds <n>] [--points <n>] [--mode RACE|PRACTICE]

              --source  a directory holding maps/<world-dir>/{map.json,portals.json} and cups/cups.json
              --out     a directory to write maps/<name>.json and cups/<name>.json into
              --spawn   where a player starts on one map, keyed by its directory under maps/.
                        Required once per map found; there is no default, because there is no
                        honest one. Read it from the world's level.dat (SpawnX/SpawnY/SpawnZ).
            """;

    public ConverterOptions {
        spawns = Map.copyOf(spawns);
    }

    /**
     * Parses a command line.
     *
     * @param arguments the arguments as given, without the program name
     * @return what was asked for, with the two seeded values defaulted
     * @throws IllegalArgumentException on an unknown option, a missing value, a malformed
     *                                  {@code --spawn}, or a missing {@code --source}/{@code --out}
     */
    public static ConverterOptions parse(List<String> arguments) {
        Path source = null;
        Path out = null;
        Map<String, Vec3> spawns = new LinkedHashMap<>();
        Duration referenceTime = DEFAULT_REFERENCE_TIME;
        int points = DEFAULT_POINTS;
        GameMode mode = GameMode.RACE;

        for (int i = 0; i < arguments.size(); i += 2) {
            String option = arguments.get(i);
            if (i + 1 >= arguments.size()) {
                throw new IllegalArgumentException("%s needs a value".formatted(option));
            }
            String value = arguments.get(i + 1);
            switch (option) {
                case "--source" -> source = Path.of(value);
                case "--out" -> out = Path.of(value);
                case "--spawn" -> putSpawn(spawns, value);
                case "--reference-time-seconds" -> referenceTime = referenceTime(value);
                case "--points" -> points = points(value);
                case "--mode" -> mode = GameMode.byName(value).orElseThrow(() -> new IllegalArgumentException(
                        "--mode must be RACE or PRACTICE, was '%s'".formatted(value)));
                default -> throw new IllegalArgumentException("unknown option '%s'".formatted(option));
            }
        }

        if (source == null || out == null) {
            throw new IllegalArgumentException("--source and --out are both required");
        }
        return new ConverterOptions(source, out, spawns, referenceTime, points, mode);
    }

    private static void putSpawn(Map<String, Vec3> spawns, String value) {
        int separator = value.indexOf('=');
        if (separator < 1) {
            throw new IllegalArgumentException(
                    "--spawn must read <world-dir>=<x>,<y>,<z>, was '%s'".formatted(value));
        }
        String world = value.substring(0, separator);
        String[] coordinates = value.substring(separator + 1).split(",");
        if (coordinates.length != 3) {
            throw new IllegalArgumentException(
                    "--spawn needs three comma-separated coordinates, was '%s'".formatted(value));
        }
        Vec3 spawn;
        try {
            spawn = new Vec3(
                    Double.parseDouble(coordinates[0].trim()),
                    Double.parseDouble(coordinates[1].trim()),
                    Double.parseDouble(coordinates[2].trim()));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("--spawn coordinates must be numbers, was '%s'".formatted(value));
        }
        if (spawns.put(world, spawn) != null) {
            throw new IllegalArgumentException("--spawn was given twice for '%s'".formatted(world));
        }
    }

    private static Duration referenceTime(String value) {
        double seconds;
        try {
            seconds = Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "--reference-time-seconds must be a number, was '%s'".formatted(value));
        }
        if (seconds <= 0.0) {
            throw new IllegalArgumentException(
                    "--reference-time-seconds must be positive, was '%s'".formatted(value));
        }
        return Duration.ofMillis(Math.round(seconds * 1000.0));
    }

    private static int points(String value) {
        int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("--points must be a whole number, was '%s'".formatted(value));
        }
        if (parsed < 0) {
            throw new IllegalArgumentException("--points must not be negative, was '%s'".formatted(value));
        }
        return parsed;
    }

    /**
     * The spawn recorded for one map's world directory.
     *
     * @param worldDirectory the directory name under {@code <source>/maps/}
     * @return that map's spawn
     * @throws IllegalArgumentException if no {@code --spawn} was given for it
     */
    public Vec3 spawnFor(String worldDirectory) {
        Vec3 spawn = spawns.get(worldDirectory);
        if (spawn == null) {
            throw new IllegalArgumentException(
                    ("no --spawn given for '%s'; read SpawnX/SpawnY/SpawnZ out of that world's "
                            + "level.dat rather than letting the converter invent one")
                            .formatted(worldDirectory));
        }
        return spawn;
    }
}
