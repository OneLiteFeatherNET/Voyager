package net.elytrarace.voyager.platform.catalog;

import net.elytrarace.voyager.api.config.ConfigProblem;
import net.elytrarace.voyager.api.config.ConfigProblem.Severity;
import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.platform.catalog.exception.DuplicateCatalogEntryException;
import net.elytrarace.voyager.platform.world.WorldFolders;
import net.elytrarace.voyager.platform.world.WorldHealth;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Every problem in the catalogue and in the worlds it references, in one sorted list.
 *
 * <p>Boot refuses on the first problem; this reports them all. The catalogue half is
 * {@link CatalogLoader#read}, which already collects every problem as data. This class adds the
 * world half: each map's world folder, then its health through the {@link HealthSource}. A map whose
 * folder is missing or empty is not handed to the health source, because there is nothing to read.
 *
 * <p>A dangling map entry is reported per entry, for every cup that parsed, whether or not some other
 * file is malformed: the source is the cup file and the key is the map entry it names. The check runs on
 * the snapshot as it parsed, so a malformed map file can make an entry dangle here; the report still names
 * the entry, which is the operator's question.
 */
public final class CatalogValidation {

    private CatalogValidation() {
    }

    /**
     * Reads a world's health, reading its chunks first if the caller needs that.
     *
     * <p>The check's real source reads every chunk the world's region files cover and then reports the
     * counters; a test supplies a fake. An exception thrown here is reported against the world and does
     * not stop the other worlds from being checked.
     */
    @FunctionalInterface
    public interface HealthSource {
        /**
         * @param world the world directory's name
         * @return the world's health after reading it
         */
        WorldHealth healthOf(String world);
    }

    /**
     * Checks the catalogue and every world its maps name.
     *
     * @param dataDirectory the directory holding {@code cups/} and {@code maps/}
     * @param worldsRoot    the directory the world directories sit in
     * @param health        reads a world's health; called only for a world whose folder holds region data
     * @return every problem, sorted by source then key; empty when nothing is wrong
     */
    public static List<ConfigProblem> validate(Path dataDirectory, Path worldsRoot, HealthSource health) {
        CatalogReading reading = CatalogLoader.read(dataDirectory);
        List<ConfigProblem> problems = new ArrayList<>();
        for (CatalogProblem problem : reading.problems()) {
            problems.add(problemOf(problem));
        }
        problems.addAll(unresolvedCupEntries(reading));
        problems.addAll(worldProblems(reading, worldsRoot, health));

        problems.sort(ConfigProblem.ORDER);
        return List.copyOf(problems);
    }

    private static List<ConfigProblem> unresolvedCupEntries(CatalogReading reading) {
        CatalogSnapshot snapshot = reading.snapshot();
        List<ConfigProblem> entries = new ArrayList<>();
        for (CupDefinition cup : snapshot.cups().values()) {
            Path cupFile = reading.cupFilesByName().get(cup.name());
            for (String mapName : cup.mapNames()) {
                if (!snapshot.mapNames().contains(mapName)) {
                    entries.add(error(mapName, cupFile.toAbsolutePath().toString(),
                            "cup '%s' plays '%s', which no map file provides".formatted(cup.name(), mapName)));
                }
            }
        }
        return entries;
    }

    private static List<ConfigProblem> worldProblems(CatalogReading reading, Path worldsRoot, HealthSource health) {
        List<ConfigProblem> problems = new ArrayList<>();
        // One health read per world, however many maps play it: the read is the expensive part and the
        // answer is the same for every map that names that world.
        Map<String, Optional<String>> deepFailures = new HashMap<>();

        for (MapDefinition map : reading.snapshot().maps().values()) {
            Path mapFile = reading.mapFilesByName().get(map.name()).toAbsolutePath();
            String world = map.world();
            switch (WorldFolders.check(worldsRoot, world)) {
                case MISSING -> problems.add(error("world", mapFile.toString(),
                        "world '%s' is not a folder; looked for %s"
                                .formatted(world, worldsRoot.resolve(world).toAbsolutePath())));
                case NO_REGION_DATA -> problems.add(error("world", mapFile.toString(),
                        "world '%s' holds no region data; looked for region files in %s and in %s"
                                .formatted(world, worldsRoot.resolve(world).resolve("region").toAbsolutePath(),
                                        worldsRoot.resolve(world).resolve("dimensions").toAbsolutePath())));
                case PRESENT -> deepFailures.computeIfAbsent(world, name -> deepCheck(name, health))
                        .ifPresent(failure -> problems.add(error("world", mapFile.toString(), failure)));
                default -> throw new IllegalStateException(
                        "unhandled world state for '%s'".formatted(world));
            }
        }
        return problems;
    }

    /** Empty when the world is sound; otherwise the reason it is not, in the words the report prints. */
    private static Optional<String> deepCheck(String world, HealthSource health) {
        try {
            WorldHealth report = Objects.requireNonNull(health.healthOf(world), "the health source returned nothing");
            return report.isSound() ? Optional.empty() : Optional.of(report.describe());
        } catch (RuntimeException exception) {
            String reason = Optional.ofNullable(exception.getMessage()).orElse(exception.getClass().getName());
            return Optional.of("world '%s' could not be read: %s".formatted(world, reason));
        }
    }

    /**
     * One catalogue problem as the line the configuration check and a reload both print. Package-private: the
     * reloader reports the same problems in the same form.
     */
    static ConfigProblem problemOf(CatalogProblem problem) {
        return error(keyOf(problem), problem.source().toAbsolutePath().toString(), problem.message());
    }

    private static ConfigProblem error(String key, String source, String message) {
        return new ConfigProblem(key, source, message, Severity.ERROR);
    }

    /**
     * The field a catalogue problem concerns: the name for a duplicate, the directory name for a
     * directory that is wrong, and {@code file} for a file that will not parse.
     */
    private static String keyOf(CatalogProblem problem) {
        if (problem.cause() instanceof DuplicateCatalogEntryException) {
            return "name";
        }
        String fileName = problem.source().getFileName().toString();
        return fileName.endsWith(".json") ? "file" : fileName;
    }
}
