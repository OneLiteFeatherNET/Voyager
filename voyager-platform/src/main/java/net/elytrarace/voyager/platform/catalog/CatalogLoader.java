package net.elytrarace.voyager.platform.catalog;

import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.platform.catalog.exception.UnresolvedCupMapException;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The one entry point that reads a data directory's {@code cups/} and {@code maps/}.
 *
 * <p>{@link #read} collects every problem and never throws for a bad file. {@link #load} is boot's
 * policy: it refuses with the cause of the first problem, which is the same exception, with the same
 * message, that boot has always raised. The order is {@code cups/}, then {@code maps/}, then the
 * cross-catalogue check. Cups come first because avaje builds the cup catalogue before the map
 * catalogue, which is the order boot has always failed in.
 *
 * <p>The cross-catalogue check runs only when neither directory has a problem. A dangling reference
 * cannot be decided without both lists, and a broken directory would otherwise add a spurious
 * "plays unknown map" line.
 */
public final class CatalogLoader {

    private static final String CUPS = "cups";
    private static final String MAPS = "maps";

    private CatalogLoader() {
    }

    /**
     * Reads the data directory and returns its snapshot, or refuses with the first problem.
     *
     * @param dataDirectory the directory holding {@code cups/} and {@code maps/}
     * @return the snapshot of a catalogue with no problem
     * @throws RuntimeException the cause of the first problem: an {@code UnreadableCatalogException},
     *     a {@code MalformedCatalogFileException}, a {@code DuplicateCatalogEntryException} or an
     *     {@code UnresolvedCupMapException}
     */
    public static CatalogSnapshot load(Path dataDirectory) {
        CatalogReading reading = read(dataDirectory);
        if (!reading.problems().isEmpty()) {
            throw reading.problems().getFirst().cause();
        }
        Optional<UnresolvedCupMapException> unresolved = CatalogConsistency.unresolvedCupMaps(
                reading.snapshot().maps(), reading.snapshot().cups());
        if (unresolved.isPresent()) {
            throw unresolved.get();
        }
        return reading.snapshot();
    }

    /**
     * Reads the data directory and returns what parsed together with every problem found.
     *
     * <p>The cross-catalogue check is not part of the read. A dangling entry belongs to one cup, and
     * boot decides per cup whether it matters, so the check runs there, on the cups that are played.
     *
     * @param dataDirectory the directory holding {@code cups/} and {@code maps/}
     * @return the reading; never throws for a bad file or a missing directory
     */
    public static CatalogReading read(Path dataDirectory) {
        List<CatalogProblem> problems = new ArrayList<>();
        List<Path> cupFiles = new ArrayList<>();
        Map<String, Path> cupFilesByName = new LinkedHashMap<>();
        Map<String, Path> mapFilesByName = new LinkedHashMap<>();
        Map<String, CupDefinition> cups = CatalogDirectory.readAll(
                dataDirectory.resolve(CUPS), "cup", CupDefinition.class, CupDefinition::name, problems, cupFiles,
                cupFilesByName);
        Map<String, MapDefinition> maps = CatalogDirectory.readAll(
                dataDirectory.resolve(MAPS), "map", MapDefinition.class, MapDefinition::name, problems,
                new ArrayList<>(), mapFilesByName);
        return new CatalogReading(new CatalogSnapshot(maps, cups), problems, cupFiles, mapFilesByName,
                cupFilesByName);
    }
}
